package com.hoggamers.rankforge.data.cloud

import android.util.Log
import com.hoggamers.rankforge.BuildConfig
import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
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

private const val LOBBY_STORAGE_TIMING_TAG = "PointIQCalcTiming"

private fun logLobbyStorageTiming(
    stage: String,
    startedAtNanos: Long,
    tournamentId: String,
    matchId: String,
    lobbyScreenshotIndex: Int,
    outcome: String,
    byteSize: Long,
) {
    if (!BuildConfig.DEBUG) return
    val durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
    Log.d(
        LOBBY_STORAGE_TIMING_TAG,
        "stage=$stage duration_ms=$durationMs tournament_id=$tournamentId match_id=$matchId " +
            "lobby_index=$lobbyScreenshotIndex byte_size=$byteSize outcome=$outcome",
    )
}

sealed interface MatchLobbyScreenshotStorageUploadResult {
    data class Uploaded(val objectPath: String) : MatchLobbyScreenshotStorageUploadResult

    data class Failed(
        val failure: MatchLobbyScreenshotStorageUploadFailure,
    ) : MatchLobbyScreenshotStorageUploadResult
}

enum class MatchLobbyScreenshotStorageUploadFailure {
    MISSING_AUTH_SESSION,
    MISSING_LOCAL_FILE,
    MISSING_TOURNAMENT_ID,
    MISSING_MATCH_ID,
    INVALID_INDEX,
    CLOUD_MATCH_ID_UNAVAILABLE,
    UNSUPPORTED_FORMAT,
    LOCAL_FILE_READ_FAILED,
    NETWORK,
    AUTHORIZATION,
    UPLOAD_FAILED,
}

interface MatchLobbyScreenshotStorageUploader {
    suspend fun upload(
        tournamentId: String?,
        matchId: String?,
        lobbyScreenshotIndex: Int?,
        localFile: File?,
    ): MatchLobbyScreenshotStorageUploadResult

    suspend fun upload(
        expectedOwnerUserId: String,
        tournamentId: String?,
        matchId: String?,
        lobbyScreenshotIndex: Int?,
        localFile: File?,
    ): MatchLobbyScreenshotStorageUploadResult =
        throw SecurityException("Expected screenshot owner is required.")
}

@Singleton
class SupabaseMatchLobbyScreenshotStorageUploader internal constructor(
    private val isConfigured: () -> Boolean,
    private val currentUserId: suspend () -> String?,
    private val uploadFile: suspend (String, String, File, String) -> Unit,
) : MatchLobbyScreenshotStorageUploader {
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
        lobbyScreenshotIndex: Int?,
        localFile: File?,
    ): MatchLobbyScreenshotStorageUploadResult {
        return uploadInternal(null, tournamentId, matchId, lobbyScreenshotIndex, localFile)
    }

    override suspend fun upload(
        expectedOwnerUserId: String,
        tournamentId: String?,
        matchId: String?,
        lobbyScreenshotIndex: Int?,
        localFile: File?,
    ): MatchLobbyScreenshotStorageUploadResult = uploadInternal(
        expectedOwnerUserId,
        tournamentId,
        matchId,
        lobbyScreenshotIndex,
        localFile,
    )

    private suspend fun uploadInternal(
        expectedOwnerUserId: String?,
        tournamentId: String?,
        matchId: String?,
        lobbyScreenshotIndex: Int?,
        localFile: File?,
    ): MatchLobbyScreenshotStorageUploadResult {
        val normalizedTournamentId = tournamentId?.takeIf { it.isNotBlank() }
            ?: return failed(MatchLobbyScreenshotStorageUploadFailure.MISSING_TOURNAMENT_ID)
        val normalizedMatchId = matchId?.takeIf { it.isNotBlank() }
            ?: return failed(MatchLobbyScreenshotStorageUploadFailure.MISSING_MATCH_ID)
        val index = lobbyScreenshotIndex?.takeIf { it in 1..3 }
            ?: return failed(MatchLobbyScreenshotStorageUploadFailure.INVALID_INDEX)
        if (!isConfigured()) return failed(MatchLobbyScreenshotStorageUploadFailure.UPLOAD_FAILED)
        val userId = currentUserId()
            ?: return failed(MatchLobbyScreenshotStorageUploadFailure.MISSING_AUTH_SESSION)
        if (expectedOwnerUserId != null && (expectedOwnerUserId.isBlank() || userId != expectedOwnerUserId)) {
            return failed(MatchLobbyScreenshotStorageUploadFailure.AUTHORIZATION)
        }
        val file = localFile ?: return failed(MatchLobbyScreenshotStorageUploadFailure.MISSING_LOCAL_FILE)
        val readable = runCatching { file.isFile && file.canRead() && file.length() > 0L }.getOrDefault(false)
        if (!readable) return failed(MatchLobbyScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED)
        val format = formatFor(file)
            ?: return failed(MatchLobbyScreenshotStorageUploadFailure.UNSUPPORTED_FORMAT)
        val objectPath = objectPath(
            userId = userId,
            tournamentId = normalizedTournamentId,
            matchId = normalizedMatchId,
            lobbyScreenshotIndex = index,
            extension = format.extension,
        ) ?: return failed(MatchLobbyScreenshotStorageUploadFailure.CLOUD_MATCH_ID_UNAVAILABLE)

        val uploadStartedAtNanos = System.nanoTime()
        var uploadOutcome = "THREW"
        return try {
            uploadFile(OCR_SCREENSHOTS_BUCKET, objectPath, file, format.contentType)
            uploadOutcome = "UPLOADED"
            MatchLobbyScreenshotStorageUploadResult.Uploaded(objectPath)
        } catch (cancellation: CancellationException) {
            uploadOutcome = "CANCELLED"
            throw cancellation
        } catch (throwable: Throwable) {
            val failure = throwable.toUploadFailure()
            uploadOutcome = "FAILED_$failure"
            failed(failure)
        } finally {
            logLobbyStorageTiming(
                stage = "LOBBY_STORAGE_UPLOAD_$index",
                startedAtNanos = uploadStartedAtNanos,
                tournamentId = normalizedTournamentId,
                matchId = normalizedMatchId,
                lobbyScreenshotIndex = index,
                outcome = uploadOutcome,
                byteSize = runCatching { file.length() }.getOrDefault(-1L),
            )
        }
    }

    private fun failed(failure: MatchLobbyScreenshotStorageUploadFailure) =
        MatchLobbyScreenshotStorageUploadResult.Failed(failure)

    companion object {
        fun objectPath(
            userId: String,
            tournamentId: String,
            matchId: String,
            lobbyScreenshotIndex: Int,
            extension: String,
        ): String? {
            if (lobbyScreenshotIndex !in 1..3) return null
            val cloudMatchId = MatchCloudIdentity.matchId(tournamentId, matchId) ?: return null
            return "users/$userId/tournaments/$tournamentId/matches/$cloudMatchId/lobby/" +
                "$lobbyScreenshotIndex/original.$extension"
        }

        fun formatFor(file: File): MatchLobbyScreenshotImageFormat? = when (
            file.extension.lowercase(Locale.ROOT)
        ) {
            "png" -> MatchLobbyScreenshotImageFormat("png", "image/png")
            "jpg", "jpeg" -> MatchLobbyScreenshotImageFormat("jpg", "image/jpeg")
            "webp" -> MatchLobbyScreenshotImageFormat("webp", "image/webp")
            else -> null
        }
    }
}

data class MatchLobbyScreenshotImageFormat(
    val extension: String,
    val contentType: String,
)

class NoOpMatchLobbyScreenshotStorageUploader : MatchLobbyScreenshotStorageUploader {
    override suspend fun upload(
        tournamentId: String?,
        matchId: String?,
        lobbyScreenshotIndex: Int?,
        localFile: File?,
    ): MatchLobbyScreenshotStorageUploadResult = MatchLobbyScreenshotStorageUploadResult.Failed(
        MatchLobbyScreenshotStorageUploadFailure.UPLOAD_FAILED,
    )
}

private fun Throwable.toUploadFailure(): MatchLobbyScreenshotStorageUploadFailure {
    val message = message.orEmpty().lowercase(Locale.ROOT)
    return when {
        this is IOException ||
            message.contains("timeout") ||
            message.contains("network") ||
            message.contains("unable to resolve") ||
            message.contains("connect") -> MatchLobbyScreenshotStorageUploadFailure.NETWORK
        message.contains("401") ||
            message.contains("403") ||
            message.contains("unauthor") ||
            message.contains("forbidden") ||
            message.contains("row-level security") ||
            message.contains("42501") -> MatchLobbyScreenshotStorageUploadFailure.AUTHORIZATION
        else -> MatchLobbyScreenshotStorageUploadFailure.UPLOAD_FAILED
    }
}
