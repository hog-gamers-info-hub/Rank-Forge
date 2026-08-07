package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import io.ktor.http.ContentType
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

const val OCR_SCREENSHOTS_BUCKET = "ocr-screenshots"

sealed interface MatchResultScreenshotStorageUploadResult {
    data class Uploaded(val objectPath: String) : MatchResultScreenshotStorageUploadResult

    data class Failed(
        val failure: MatchResultScreenshotStorageUploadFailure,
    ) : MatchResultScreenshotStorageUploadResult
}

enum class MatchResultScreenshotStorageUploadFailure {
    MISSING_AUTH_SESSION,
    MISSING_LOCAL_FILE,
    MISSING_TOURNAMENT_ID,
    MISSING_MATCH_ID,
    INVALID_ROLE,
    CLOUD_MATCH_ID_UNAVAILABLE,
    UNSUPPORTED_FORMAT,
    LOCAL_FILE_READ_FAILED,
    NETWORK,
    AUTHORIZATION,
    UPLOAD_FAILED,
}

interface MatchResultScreenshotStorageUploader {
    suspend fun upload(
        tournamentId: String?,
        matchId: String?,
        role: MatchResultScreenshotRole?,
        localFile: File?,
    ): MatchResultScreenshotStorageUploadResult
}

@Singleton
class SupabaseMatchResultScreenshotStorageUploader internal constructor(
    private val isConfigured: () -> Boolean,
    private val currentUserId: suspend () -> String?,
    private val uploadFile: suspend (String, String, File, String) -> Unit,
) : MatchResultScreenshotStorageUploader {
    @Inject
    constructor(
        config: SupabaseAuthConfig,
        clientProvider: SupabaseClientProvider,
    ) : this(
        isConfigured = { config.isConfigured },
        currentUserId = {
            clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        },
        uploadFile = { bucket, path, file, contentType ->
            clientProvider.client.storage.from(bucket).upload(path, file) {
                upsert = true
                this.contentType = ContentType.parse(contentType)
            }
        },
    )

    override suspend fun upload(
        tournamentId: String?,
        matchId: String?,
        role: MatchResultScreenshotRole?,
        localFile: File?,
    ): MatchResultScreenshotStorageUploadResult {
        val normalizedTournamentId = tournamentId?.takeIf { it.isNotBlank() }
            ?: return MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.MISSING_TOURNAMENT_ID,
            )
        val normalizedMatchId = matchId?.takeIf { it.isNotBlank() }
            ?: return MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.MISSING_MATCH_ID,
            )
        val screenshotRole = role ?: return MatchResultScreenshotStorageUploadResult.Failed(
            MatchResultScreenshotStorageUploadFailure.INVALID_ROLE,
        )
        if (!configured()) {
            return MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.UPLOAD_FAILED,
            )
        }
        val userId = currentSessionUserId()
            ?: return MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.MISSING_AUTH_SESSION,
            )
        val file = localFile ?: return MatchResultScreenshotStorageUploadResult.Failed(
            MatchResultScreenshotStorageUploadFailure.MISSING_LOCAL_FILE,
        )
        val canReadFile = runCatching {
            file.isFile && file.canRead() && file.length() > 0L
        }.getOrDefault(false)
        if (!canReadFile) {
            return MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED,
            )
        }
        val format = formatFor(file)
            ?: return MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.UNSUPPORTED_FORMAT,
            )
        val objectPath = objectPath(
            userId = userId,
            tournamentId = normalizedTournamentId,
            matchId = normalizedMatchId,
            role = screenshotRole,
            extension = format.extension,
        ) ?: return MatchResultScreenshotStorageUploadResult.Failed(
            MatchResultScreenshotStorageUploadFailure.CLOUD_MATCH_ID_UNAVAILABLE,
        )

        return try {
            upload(OCR_SCREENSHOTS_BUCKET, objectPath, file, format.contentType)
            MatchResultScreenshotStorageUploadResult.Uploaded(objectPath)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            MatchResultScreenshotStorageUploadResult.Failed(throwable.toMatchResultScreenshotUploadFailure())
        }
    }

    private fun configured(): Boolean = isConfigured()

    private suspend fun currentSessionUserId(): String? = currentUserId()

    private suspend fun upload(
        bucket: String,
        path: String,
        file: File,
        contentType: String,
    ) = uploadFile(bucket, path, file, contentType)

    companion object {
        fun objectPath(
            userId: String,
            tournamentId: String,
            matchId: String,
            role: MatchResultScreenshotRole,
            extension: String,
        ): String? {
            val cloudMatchId = MatchCloudIdentity.matchId(
                tournamentId = tournamentId,
                localMatchId = matchId,
            ) ?: return null
            val roleSegment = role.toCloudPathSegment()
            return "users/$userId/tournaments/$tournamentId/matches/$cloudMatchId/result/" +
                "$roleSegment/original.$extension"
        }

        fun formatFor(file: File): MatchResultScreenshotImageFormat? = when (
            file.extension.lowercase(Locale.ROOT)
        ) {
            "png" -> MatchResultScreenshotImageFormat("png", "image/png")
            "jpg", "jpeg" -> MatchResultScreenshotImageFormat("jpg", "image/jpeg")
            "webp" -> MatchResultScreenshotImageFormat("webp", "image/webp")
            else -> null
        }
    }
}

data class MatchResultScreenshotImageFormat(
    val extension: String,
    val contentType: String,
)

fun MatchResultScreenshotRole.toCloudPathSegment(): String = when (this) {
    MatchResultScreenshotRole.MATCH_RESULT_UPPER -> "upper"
    MatchResultScreenshotRole.MATCH_RESULT_LOWER -> "lower"
}

private fun Throwable.toMatchResultScreenshotUploadFailure(): MatchResultScreenshotStorageUploadFailure {
    val message = message.orEmpty().lowercase(Locale.ROOT)
    return when {
        this is IOException ||
            message.contains("timeout") ||
            message.contains("network") ||
            message.contains("unable to resolve") ||
            message.contains("connect") -> MatchResultScreenshotStorageUploadFailure.NETWORK
        message.contains("401") ||
            message.contains("403") ||
            message.contains("unauthor") ||
            message.contains("forbidden") ||
            message.contains("row-level security") ||
            message.contains("42501") -> MatchResultScreenshotStorageUploadFailure.AUTHORIZATION
        else -> MatchResultScreenshotStorageUploadFailure.UPLOAD_FAILED
    }
}

class NoOpMatchResultScreenshotStorageUploader : MatchResultScreenshotStorageUploader {
    override suspend fun upload(
        tournamentId: String?,
        matchId: String?,
        role: MatchResultScreenshotRole?,
        localFile: File?,
    ): MatchResultScreenshotStorageUploadResult =
        MatchResultScreenshotStorageUploadResult.Failed(
            MatchResultScreenshotStorageUploadFailure.UPLOAD_FAILED,
        )
}
