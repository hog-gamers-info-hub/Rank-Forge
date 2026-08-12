package com.hoggamers.rankforge.data.cloud

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLobbyScreenshotStorageUploaderTest {
    private val tournamentId = "87204119-1b59-447a-8edc-bfecdaaeccfa"
    private val localMatchId = "2cfb5241-7c23-49eb-a6f5-224a779fb220"

    @Test
    fun objectPathUsesCloudMatchIdentityAndLobbyIndex() {
        val cloudMatchId = UUID.nameUUIDFromBytes(
            "rank-forge:match:$tournamentId:$localMatchId".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        (1..3).forEach { index ->
            assertEquals(
                "users/user-id/tournaments/$tournamentId/matches/$cloudMatchId/lobby/$index/original.png",
                SupabaseMatchLobbyScreenshotStorageUploader.objectPath(
                    "user-id", tournamentId, localMatchId, index, "png",
                ),
            )
        }
    }

    @Test
    fun uploadSupportsPngJpegAndWebp() = runTest {
        val calls = mutableListOf<List<String>>()
        val uploader = uploader { bucket, path, _, contentType -> calls += listOf(bucket, path, contentType) }
        listOf("png" to "image/png", "jpg" to "image/jpeg", "webp" to "image/webp").forEach { (extension, mime) ->
            val result = uploader.upload(tournamentId, localMatchId, 1, temporaryFile(extension))
            assertTrue(result is MatchLobbyScreenshotStorageUploadResult.Uploaded)
            assertEquals(mime, calls.last()[2])
            assertEquals(OCR_SCREENSHOTS_BUCKET, calls.last()[0])
        }
    }

    @Test
    fun invalidInputsAndMissingAuthAreControlled() = runTest {
        val file = temporaryFile("png")
        val uploader = uploader()
        assertFailure(uploader.upload(null, localMatchId, 1, file), MatchLobbyScreenshotStorageUploadFailure.MISSING_TOURNAMENT_ID)
        assertFailure(uploader.upload(tournamentId, null, 1, file), MatchLobbyScreenshotStorageUploadFailure.MISSING_MATCH_ID)
        assertFailure(uploader.upload(tournamentId, localMatchId, 0, file), MatchLobbyScreenshotStorageUploadFailure.INVALID_INDEX)
        assertFailure(uploader.upload(tournamentId, localMatchId, 4, file), MatchLobbyScreenshotStorageUploadFailure.INVALID_INDEX)
        assertFailure(uploader(userId = null).upload(tournamentId, localMatchId, 1, file), MatchLobbyScreenshotStorageUploadFailure.MISSING_AUTH_SESSION)
        assertFailure(uploader().upload(tournamentId, localMatchId, 1, null), MatchLobbyScreenshotStorageUploadFailure.MISSING_LOCAL_FILE)
        assertFailure(uploader().upload(tournamentId, localMatchId, 1, File("missing.png")), MatchLobbyScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED)
        assertFailure(uploader().upload(tournamentId, localMatchId, 1, temporaryFile("gif")), MatchLobbyScreenshotStorageUploadFailure.UNSUPPORTED_FORMAT)
    }

    @Test
    fun networkAuthorizationAndCancellationPropagateCorrectly() = runTest {
        assertFailure(
            uploader { _, _, _, _ -> error("network timeout") }.upload(tournamentId, localMatchId, 1, temporaryFile("png")),
            MatchLobbyScreenshotStorageUploadFailure.NETWORK,
        )
        assertFailure(
            uploader { _, _, _, _ -> error("403 forbidden") }.upload(tournamentId, localMatchId, 1, temporaryFile("png")),
            MatchLobbyScreenshotStorageUploadFailure.AUTHORIZATION,
        )
        val cancellation = CancellationException("cancelled")
        try {
            uploader { _, _, _, _ -> throw cancellation }.upload(tournamentId, localMatchId, 1, temporaryFile("png"))
            throw AssertionError("CancellationException was swallowed")
        } catch (actual: CancellationException) {
            assertEquals(cancellation, actual)
        }
    }

    private fun uploader(
        userId: String? = "user-id",
        upload: suspend (String, String, File, String) -> Unit = { _, _, _, _ -> },
    ) = SupabaseMatchLobbyScreenshotStorageUploader(
        isConfigured = { true },
        currentUserId = { userId },
        uploadFile = upload,
    )

    private fun temporaryFile(extension: String): File = File.createTempFile("lobby-upload-", ".$extension").apply {
        writeText("image", Charsets.UTF_8)
        deleteOnExit()
    }

    private fun assertFailure(
        result: MatchLobbyScreenshotStorageUploadResult,
        expected: MatchLobbyScreenshotStorageUploadFailure,
    ) {
        assertEquals(MatchLobbyScreenshotStorageUploadResult.Failed(expected), result)
    }
}
