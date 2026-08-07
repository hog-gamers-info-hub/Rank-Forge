package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseMatchResultScreenshotStorageUploaderTest {
    @Test
    fun upperAndLowerPathsUseCanonicalCloudMatchIdentity() {
        val tournamentId = "87204119-1b59-447a-8edc-bfecdaaeccfa"
        val localMatchId = "local-match-1"
        val cloudMatchId = UUID.nameUUIDFromBytes(
            "rank-forge:match:$tournamentId:$localMatchId".toByteArray(StandardCharsets.UTF_8),
        ).toString()

        val upper = SupabaseMatchResultScreenshotStorageUploader.objectPath(
            userId = "user-id",
            tournamentId = tournamentId,
            matchId = localMatchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            extension = "png",
        )
        val lower = SupabaseMatchResultScreenshotStorageUploader.objectPath(
            userId = "user-id",
            tournamentId = tournamentId,
            matchId = localMatchId,
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            extension = "png",
        )

        assertEquals(
            "users/user-id/tournaments/$tournamentId/matches/$cloudMatchId/result/upper/original.png",
            upper,
        )
        assertEquals(
            "users/user-id/tournaments/$tournamentId/matches/$cloudMatchId/result/lower/original.png",
            lower,
        )
        assertNotEquals(upper, lower)
    }

    @Test
    fun jpgAndWebpFormatsMapToApprovedMimeTypes() {
        assertEquals(
            MatchResultScreenshotImageFormat("jpg", "image/jpeg"),
            SupabaseMatchResultScreenshotStorageUploader.formatFor(File("original.jpg")),
        )
        assertEquals(
            MatchResultScreenshotImageFormat("jpg", "image/jpeg"),
            SupabaseMatchResultScreenshotStorageUploader.formatFor(File("original.jpeg")),
        )
        assertEquals(
            MatchResultScreenshotImageFormat("webp", "image/webp"),
            SupabaseMatchResultScreenshotStorageUploader.formatFor(File("original.webp")),
        )
    }

    @Test
    fun objectPathRejectsInvalidTournamentIdentity() {
        assertNull(
            SupabaseMatchResultScreenshotStorageUploader.objectPath(
                userId = "user-id",
                tournamentId = "not-a-uuid",
                matchId = "local-match",
                role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                extension = "png",
            ),
        )
    }

    @Test
    fun validationFailuresAreControlledBeforeUpload() = runTest {
        val uploader = uploader()
        val file = temporaryFile("png")

        assertEquals(
            MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.MISSING_TOURNAMENT_ID,
            ),
            uploader.upload(null, "match", MatchResultScreenshotRole.MATCH_RESULT_UPPER, file),
        )
        assertEquals(
            MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.MISSING_MATCH_ID,
            ),
            uploader.upload(TOURNAMENT_ID, "", MatchResultScreenshotRole.MATCH_RESULT_UPPER, file),
        )
        assertEquals(
            MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.INVALID_ROLE,
            ),
            uploader.upload(TOURNAMENT_ID, "match", null, file),
        )
        assertEquals(
            MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.UNSUPPORTED_FORMAT,
            ),
            uploader.upload(TOURNAMENT_ID, "match", MatchResultScreenshotRole.MATCH_RESULT_UPPER, temporaryFile("gif")),
        )
    }

    @Test
    fun missingAuthAndMissingLocalFileAreControlled() = runTest {
        val missingAuthUploader = uploader(userId = null)

        assertEquals(
            MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.MISSING_AUTH_SESSION,
            ),
            missingAuthUploader.upload(
                TOURNAMENT_ID,
                "match",
                MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                temporaryFile("png"),
            ),
        )
        assertEquals(
            MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.MISSING_LOCAL_FILE,
            ),
            uploader().upload(TOURNAMENT_ID, "match", MatchResultScreenshotRole.MATCH_RESULT_UPPER, null),
        )
    }

    @Test
    fun unreadableLocalFileAndCloudMatchIdentityFailureAreControlled() = runTest {
        val missing = File("missing-original.png")

        assertEquals(
            MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.LOCAL_FILE_READ_FAILED,
            ),
            uploader().upload(TOURNAMENT_ID, "match", MatchResultScreenshotRole.MATCH_RESULT_UPPER, missing),
        )
        assertEquals(
            MatchResultScreenshotStorageUploadResult.Failed(
                MatchResultScreenshotStorageUploadFailure.CLOUD_MATCH_ID_UNAVAILABLE,
            ),
            uploader().upload("not-a-uuid", "match", MatchResultScreenshotRole.MATCH_RESULT_UPPER, temporaryFile("png")),
        )
    }

    @Test
    fun uploadUsesOcrBucketAndRoleSpecificPath() = runTest {
        val uploads = mutableListOf<UploadCall>()
        val uploader = uploader { bucket, path, _, contentType ->
            uploads += UploadCall(bucket, path, contentType)
        }

        val result = uploader.upload(
            tournamentId = TOURNAMENT_ID,
            matchId = "local-match-1",
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            localFile = temporaryFile("webp"),
        )

        assertTrue(result is MatchResultScreenshotStorageUploadResult.Uploaded)
        assertEquals(1, uploads.size)
        assertEquals(OCR_SCREENSHOTS_BUCKET, uploads.single().bucket)
        assertEquals("image/webp", uploads.single().contentType)
        assertTrue(uploads.single().path.contains("/result/lower/original.webp"))
    }

    @Test
    fun cancellationPropagates() {
        val uploader = uploader { _, _, _, _ -> throw CancellationException("cancelled") }

        assertThrows(CancellationException::class.java) {
            runTest {
                uploader.upload(
                    TOURNAMENT_ID,
                    "match",
                    MatchResultScreenshotRole.MATCH_RESULT_UPPER,
                    temporaryFile("png"),
                )
            }
        }
    }

    private fun uploader(
        userId: String? = "user-id",
        upload: suspend (String, String, File, String) -> Unit = { _, _, _, _ -> },
    ) = SupabaseMatchResultScreenshotStorageUploader(
        isConfigured = { true },
        currentUserId = { userId },
        uploadFile = upload,
    )

    private fun temporaryFile(extension: String): File =
        kotlin.io.path.createTempFile("rank-forge-upload", ".$extension")
            .toFile()
            .also { it.writeBytes(byteArrayOf(1, 2, 3)) }

    private data class UploadCall(
        val bucket: String,
        val path: String,
        val contentType: String,
    )

    private companion object {
        const val TOURNAMENT_ID = "87204119-1b59-447a-8edc-bfecdaaeccfa"
    }
}
