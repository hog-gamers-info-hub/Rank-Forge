package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseMatchResultScreenshotAssetCloudDataSourceTest {
    @Test
    fun mapperUsesExactTableIdentityAndCanonicalCloudMatchId() {
        val asset = asset(role = MatchResultScreenshotRole.MATCH_RESULT_UPPER)
        val cloudMatchId = expectedCloudMatchId(asset.tournamentId, asset.matchId)

        val payload = (
            asset.toMatchResultScreenshotAssetCloudPayload(OWNER_ID)
                as MatchResultScreenshotAssetCloudPayloadMappingResult.Success
            ).payload

        assertEquals(SupabaseMatchResultScreenshotAssetCloudDataSource.TABLE_NAME, "match_result_screenshot_assets")
        assertEquals(cloudMatchId, payload.matchId)
        assertEquals(OWNER_ID, payload.ownerId)
        assertEquals(asset.tournamentId, payload.tournamentId)
        assertEquals(OcrScreenshotKind.MATCH_RESULT.name, payload.screenshotKind)
        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_UPPER.name, payload.screenshotRole)
        assertEquals(asset.sha256, payload.sha256)
    }

    @Test
    fun mapperPreservesUpperLowerRolesAndCropStates() {
        val upper = asset(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            cropProfileId = "match-result",
            cropLeft = 0.1,
            cropTop = 0.2,
            cropRight = 0.8,
            cropBottom = 0.9,
        )
        val lower = asset(role = MatchResultScreenshotRole.MATCH_RESULT_LOWER)

        val upperPayload = (
            upper.toMatchResultScreenshotAssetCloudPayload(OWNER_ID)
                as MatchResultScreenshotAssetCloudPayloadMappingResult.Success
            ).payload
        val lowerPayload = (
            lower.toMatchResultScreenshotAssetCloudPayload(OWNER_ID)
                as MatchResultScreenshotAssetCloudPayloadMappingResult.Success
            ).payload

        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_UPPER.name, upperPayload.screenshotRole)
        assertEquals("match-result", upperPayload.cropProfileId)
        assertEquals(0.1, upperPayload.cropLeft ?: -1.0, 0.0)
        assertEquals(0.9, upperPayload.cropBottom ?: -1.0, 0.0)
        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_LOWER.name, lowerPayload.screenshotRole)
        assertNull(lowerPayload.cropProfileId)
        assertNull(lowerPayload.cropLeft)
        assertNull(lowerPayload.cropBottom)
    }

    @Test
    fun mapperFailsClosedWhenCloudMatchIdCannotBeDerived() {
        assertEquals(
            MatchResultScreenshotAssetCloudPayloadMappingResult.CloudMatchIdUnavailable,
            asset(tournamentId = "not-a-uuid")
                .toMatchResultScreenshotAssetCloudPayload(OWNER_ID),
        )
        assertEquals(
            MatchResultScreenshotAssetCloudPayloadMappingResult.InvalidIdentity,
            asset(roleName = "BAD_ROLE")
                .toMatchResultScreenshotAssetCloudPayload(OWNER_ID),
        )
    }

    @Test
    fun upsertWritesPayloadAndMapsAuthorizationFailure() = runTest {
        val payloads = mutableListOf<MatchResultScreenshotAssetCloudPayload>()
        val dataSource = dataSource(
            upsert = { payload -> payloads += payload },
        )

        assertEquals(
            MatchResultScreenshotAssetCloudResult.Success,
            dataSource.upsert(asset(role = MatchResultScreenshotRole.MATCH_RESULT_LOWER)),
        )
        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_LOWER.name, payloads.single().screenshotRole)

        val failed = dataSource(upsert = { error("403 forbidden") })
            .upsert(asset()) as MatchResultScreenshotAssetCloudResult.Failed
        assertEquals(MatchResultScreenshotAssetCloudFailure.AUTHORIZATION, failed.failure)
    }

    @Test
    fun missingAuthAndCloudMatchFailureAreControlled() = runTest {
        assertEquals(
            MatchResultScreenshotAssetCloudResult.Failed(
                MatchResultScreenshotAssetCloudFailure.MISSING_AUTH_SESSION,
            ),
            dataSource(userId = null).upsert(asset()),
        )
        assertEquals(
            MatchResultScreenshotAssetCloudResult.Failed(
                MatchResultScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE,
            ),
            dataSource().upsert(asset(tournamentId = "not-a-uuid")),
        )
    }

    @Test
    fun deleteFiltersByCanonicalMatchIdAndExactRoleOnly() = runTest {
        val deletes = mutableListOf<Pair<String, String>>()
        val dataSource = dataSource(
            delete = { cloudMatchId, role -> deletes += cloudMatchId to role },
        )
        val identity = identity(role = MatchResultScreenshotRole.MATCH_RESULT_LOWER)

        assertEquals(MatchResultScreenshotAssetCloudResult.Success, dataSource.deleteByIdentity(identity))

        assertEquals(
            expectedCloudMatchId(identity.tournamentId, identity.matchId),
            deletes.single().first,
        )
        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_LOWER.name, deletes.single().second)
    }

    @Test
    fun cancellationPropagates() {
        val dataSource = dataSource(upsert = { throw CancellationException("cancelled") })

        assertThrows(CancellationException::class.java) {
            runTest {
                dataSource.upsert(asset())
            }
        }
    }

    private fun dataSource(
        userId: String? = OWNER_ID,
        upsert: suspend (MatchResultScreenshotAssetCloudPayload) -> Unit = { },
        delete: suspend (String, String) -> Unit = { _, _ -> },
    ) = SupabaseMatchResultScreenshotAssetCloudDataSource(
        isConfigured = { true },
        currentUserId = { userId },
        upsertPayload = upsert,
        deleteRole = delete,
    )

    private fun identity(
        tournamentId: String = TOURNAMENT_ID,
        matchId: String = LOCAL_MATCH_ID,
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
    ) = MatchResultScreenshotIdentity(
        tournamentId = tournamentId,
        matchId = matchId,
        role = role,
    )

    private fun asset(
        tournamentId: String = TOURNAMENT_ID,
        matchId: String = LOCAL_MATCH_ID,
        role: MatchResultScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
        roleName: String = role.name,
        cropProfileId: String? = null,
        cropLeft: Double? = null,
        cropTop: Double? = null,
        cropRight: Double? = null,
        cropBottom: Double? = null,
    ) = MatchResultScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = matchId,
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = roleName,
        ownerUserId = OWNER_ID,
        localRelativePath = "screenshots/tournament/match/result/upper/original.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1600,
        originalHeight = 720,
        byteSize = 4,
        sha256 = "a".repeat(64),
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.PENDING.name,
        uploadFailureCode = null,
        storageBucket = OCR_SCREENSHOTS_BUCKET,
        storageObjectPath = SupabaseMatchResultScreenshotStorageUploader.objectPath(
            userId = OWNER_ID,
            tournamentId = TOURNAMENT_ID,
            matchId = LOCAL_MATCH_ID,
            role = role,
            extension = "png",
        ),
        cropProfileId = cropProfileId,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
        createdAt = 1_800_000_000_000,
        updatedAt = 1_800_000_001_000,
        preservedAt = 1_800_000_000_000,
        uploadedAt = null,
        revision = 1,
    )

    private fun expectedCloudMatchId(
        tournamentId: String,
        localMatchId: String,
    ): String = UUID.nameUUIDFromBytes(
        "rank-forge:match:$tournamentId:$localMatchId".toByteArray(StandardCharsets.UTF_8),
    ).toString()

    private companion object {
        const val OWNER_ID = "91000000-0000-0000-0000-000000000001"
        const val TOURNAMENT_ID = "87204119-1b59-447a-8edc-bfecdaaeccfa"
        const val LOCAL_MATCH_ID = "local-match-1"
    }
}
