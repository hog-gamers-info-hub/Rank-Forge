package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import io.ktor.http.ContentType
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

const val MATCH_SCREENSHOTS_BUCKET = "match-screenshots"

sealed interface ScreenshotStorageUploadResult {
    data class Uploaded(val objectPath: String) : ScreenshotStorageUploadResult

    data class Failed(val error: ScreenshotStorageUploadFailure) : ScreenshotStorageUploadResult
}

enum class ScreenshotStorageUploadFailure {
    MISSING_AUTH_SESSION,
    MISSING_LOCAL_FILE,
    MISSING_TOURNAMENT_ID,
    MISSING_MATCH_ID,
    UNSUPPORTED_FORMAT,
    LOCAL_FILE_READ_FAILED,
    NETWORK,
    AUTHORIZATION,
    UPLOAD_FAILED,
}

interface ScreenshotStorageUploader {
    suspend fun upload(
        tournamentId: String?,
        matchId: String?,
        localFile: File?,
    ): ScreenshotStorageUploadResult
}

@Singleton
class SupabaseScreenshotStorageUploader @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val clientProvider: SupabaseClientProvider,
) : ScreenshotStorageUploader {
    override suspend fun upload(
        tournamentId: String?,
        matchId: String?,
        localFile: File?,
    ): ScreenshotStorageUploadResult {
        val normalizedTournamentId = tournamentId?.takeIf { it.isNotBlank() }
            ?: return ScreenshotStorageUploadResult.Failed(
                ScreenshotStorageUploadFailure.MISSING_TOURNAMENT_ID,
            )
        val normalizedMatchId = matchId?.takeIf { it.isNotBlank() }
            ?: return ScreenshotStorageUploadResult.Failed(
                ScreenshotStorageUploadFailure.MISSING_MATCH_ID,
            )
        if (!config.isConfigured) {
            return ScreenshotStorageUploadResult.Failed(
                ScreenshotStorageUploadFailure.UPLOAD_FAILED,
            )
        }
        val session = clientProvider.client.auth.currentSessionOrNull()
            ?: return ScreenshotStorageUploadResult.Failed(
                ScreenshotStorageUploadFailure.MISSING_AUTH_SESSION,
            )
        val userId = session.user?.id?.takeIf { it.isNotBlank() }
            ?: return ScreenshotStorageUploadResult.Failed(
                ScreenshotStorageUploadFailure.MISSING_AUTH_SESSION,
            )
        val file = localFile
            ?: return ScreenshotStorageUploadResult.Failed(
                ScreenshotStorageUploadFailure.MISSING_LOCAL_FILE,
            )
        val canReadFile = runCatching {
            file.isFile && file.canRead() && file.length() > 0L
        }.getOrDefault(false)
        if (!canReadFile) {
            return ScreenshotStorageUploadResult.Failed(
                ScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED,
            )
        }
        val format = formatFor(file)
            ?: return ScreenshotStorageUploadResult.Failed(
                ScreenshotStorageUploadFailure.UNSUPPORTED_FORMAT,
            )
        val objectPath = objectPath(userId, normalizedTournamentId, normalizedMatchId, format.extension)

        return try {
            clientProvider.client.storage.from(MATCH_SCREENSHOTS_BUCKET).upload(objectPath, file) {
                upsert = true
                contentType = ContentType.parse(format.contentType)
            }
            ScreenshotStorageUploadResult.Uploaded(objectPath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            ScreenshotStorageUploadResult.Failed(throwable.toUploadFailure())
        }
    }

    companion object {
        fun objectPath(
            userId: String,
            tournamentId: String,
            matchId: String,
            extension: String,
        ): String = "users/$userId/tournaments/$tournamentId/matches/$matchId/original.$extension"

        private fun formatFor(file: File): ScreenshotImageFormat? = when (
            file.extension.lowercase(Locale.ROOT)
        ) {
            "png" -> ScreenshotImageFormat("png", "image/png")
            "jpg", "jpeg" -> ScreenshotImageFormat("jpg", "image/jpeg")
            "webp" -> ScreenshotImageFormat("webp", "image/webp")
            else -> null
        }
    }
}

private data class ScreenshotImageFormat(
    val extension: String,
    val contentType: String,
)

private fun Throwable.toUploadFailure(): ScreenshotStorageUploadFailure {
    val message = message.orEmpty().lowercase(Locale.ROOT)
    return when {
        message.contains("401") || message.contains("403") ||
            message.contains("unauthor") || message.contains("forbidden") ->
            ScreenshotStorageUploadFailure.AUTHORIZATION
        message.contains("timeout") || message.contains("network") ||
            message.contains("unable to resolve") || message.contains("connect") ->
            ScreenshotStorageUploadFailure.NETWORK
        else -> ScreenshotStorageUploadFailure.UPLOAD_FAILED
    }
}

class NoOpScreenshotStorageUploader : ScreenshotStorageUploader {
    override suspend fun upload(
        tournamentId: String?,
        matchId: String?,
        localFile: File?,
    ): ScreenshotStorageUploadResult = ScreenshotStorageUploadResult.Failed(
        ScreenshotStorageUploadFailure.UPLOAD_FAILED,
    )
}
