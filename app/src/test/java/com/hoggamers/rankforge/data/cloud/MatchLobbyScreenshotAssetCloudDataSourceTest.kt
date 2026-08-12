package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchLobbyScreenshotAssetCloudDataSourceTest {
    private val tournamentId = "87204119-1b59-447a-8edc-bfecdaaeccfa"
    private val localMatchId = "2cfb5241-7c23-49eb-a6f5-224a779fb220"
    private val ownerId = "11111111-1111-1111-1111-111111111111"

    @Test
    fun payloadMapsExactLobbyIdentityCloudMatchAndMetadata() {
        val entity = asset(index = 2).copy(
            cropProfileId = "lobby",
            cropLeft = 0.1,
            cropTop = 0.2,
            cropRight = 0.8,
            cropBottom = 0.9,
            storageBucket = OCR_SCREENSHOTS_BUCKET,
            storageObjectPath = "cloud/path.png",
            uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
            uploadedAt = 1_800_000_000_000,
            revision = 4,
        )
        val result = entity.toMatchLobbyScreenshotAssetCloudPayload(ownerId)
            as MatchLobbyScreenshotAssetCloudPayloadMappingResult.Success
        val payload = result.payload
        assertEquals(expectedCloudMatchId(tournamentId, localMatchId), payload.matchId)
        assertEquals(ownerId, payload.ownerId)
        assertEquals(tournamentId, payload.tournamentId)
        assertEquals(2, payload.lobbyScreenshotIndex)
        assertEquals(entity.fileExtension, payload.localFileExtension)
        assertEquals(entity.mimeType, payload.mimeType)
        assertEquals(entity.originalWidth, payload.originalWidth)
        assertEquals(entity.byteSize, payload.byteSize)
        assertEquals(entity.sha256, payload.sha256)
        assertEquals(entity.storageBucket, payload.storageBucket)
        assertEquals(entity.storageObjectPath, payload.storageObjectPath)
        assertEquals(entity.uploadStatus, payload.uploadStatus)
        assertEquals(entity.uploadedAt?.toCloudTimestamp(), payload.uploadedAt)
        assertEquals(entity.revision, payload.revision)
        assertEquals(0.1, payload.cropLeft ?: -1.0, 0.0)
        assertEquals(0.9, payload.cropBottom ?: -1.0, 0.0)
    }

    @Test
    fun mapperRejectsInvalidIndexAndUnavailableCloudMatch() {
        assertEquals(
            MatchLobbyScreenshotAssetCloudPayloadMappingResult.InvalidIdentity,
            asset(index = 4).toMatchLobbyScreenshotAssetCloudPayload(ownerId),
        )
        assertEquals(
            MatchLobbyScreenshotAssetCloudPayloadMappingResult.CloudMatchIdUnavailable,
            asset(index = 1, tournamentId = "not-a-uuid").toMatchLobbyScreenshotAssetCloudPayload(ownerId),
        )
    }

    @Test
    fun upsertUsesAuthenticatedOwnerAndMapsFailures() = runTest {
        val payloads = mutableListOf<MatchLobbyScreenshotAssetCloudPayload>()
        val source = source(upsert = { payload -> payloads += payload })
        assertEquals(MatchLobbyScreenshotAssetCloudResult.Success, source.upsert(asset(index = 1)))
        assertEquals(ownerId, payloads.single().ownerId)
        assertEquals(1, payloads.single().lobbyScreenshotIndex)
        assertEquals(
            MatchLobbyScreenshotAssetCloudFailure.AUTHORIZATION,
            (source(upsert = { error("403 forbidden") }).upsert(asset(1)) as MatchLobbyScreenshotAssetCloudResult.Failed).failure,
        )
        assertEquals(
            MatchLobbyScreenshotAssetCloudFailure.MISSING_AUTH_SESSION,
            (source(userId = null).upsert(asset(1)) as MatchLobbyScreenshotAssetCloudResult.Failed).failure,
        )
    }

    @Test
    fun deleteUsesCloudMatchIdAndExactLobbyIndex() = runTest {
        val deletes = mutableListOf<Pair<String, Int>>()
        val source = source(delete = { cloudMatchId, index -> deletes += cloudMatchId to index })
        val identity = MatchLobbyScreenshotIdentity(tournamentId, localMatchId, 3)
        assertEquals(MatchLobbyScreenshotAssetCloudResult.Success, source.deleteByIdentity(identity))
        assertEquals(listOf(expectedCloudMatchId(tournamentId, localMatchId) to 3), deletes)
        assertTrue(2 !in deletes.map { it.second })
        assertEquals(
            MatchLobbyScreenshotAssetCloudFailure.CLOUD_MATCH_ID_UNAVAILABLE,
            (source().deleteByIdentity(MatchLobbyScreenshotIdentity("not-a-uuid", localMatchId, 1)) as MatchLobbyScreenshotAssetCloudResult.Failed).failure,
        )
    }

    @Test
    fun cancellationPropagatesForUpsertAndDelete() = runTest {
        val cancellation = CancellationException("cancelled")
        try {
            source(upsert = { throw cancellation }).upsert(asset(1))
            throw AssertionError("CancellationException was swallowed")
        } catch (actual: CancellationException) {
            assertEquals(cancellation.message, actual.message)
        }
        try {
            source(delete = { _, _ -> throw cancellation }).deleteByIdentity(MatchLobbyScreenshotIdentity(tournamentId, localMatchId, 1))
            throw AssertionError("CancellationException was swallowed")
        } catch (actual: CancellationException) {
            assertEquals(cancellation.message, actual.message)
        }
    }

    private fun source(
        userId: String? = ownerId,
        upsert: suspend (MatchLobbyScreenshotAssetCloudPayload) -> Unit = {},
        delete: suspend (String, Int) -> Unit = { _, _ -> },
    ) = SupabaseMatchLobbyScreenshotAssetCloudDataSource(
        isConfigured = { true },
        currentUserId = { userId },
        upsertPayload = upsert,
        deleteAsset = delete,
    )

    private fun asset(
        index: Int,
        tournamentId: String = this.tournamentId,
        matchId: String = localMatchId,
    ) = MatchLobbyScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = matchId,
        lobbyScreenshotIndex = index,
        ownerUserId = "local-owner",
        localRelativePath = "screenshots/local.png",
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1600,
        originalHeight = 900,
        byteSize = 4,
        sha256 = "a".repeat(64),
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.PENDING.name,
        uploadFailureCode = null,
        storageBucket = null,
        storageObjectPath = null,
        cropProfileId = null,
        cropLeft = null,
        cropTop = null,
        cropRight = null,
        cropBottom = null,
        createdAt = 1_800_000_000_000,
        updatedAt = 1_800_000_001_000,
        preservedAt = 1_800_000_000_000,
        uploadedAt = null,
        revision = 1,
    )

    private fun expectedCloudMatchId(tournamentId: String, matchId: String): String = UUID.nameUUIDFromBytes(
        "rank-forge:match:$tournamentId:$matchId".toByteArray(),
    ).toString()
}
