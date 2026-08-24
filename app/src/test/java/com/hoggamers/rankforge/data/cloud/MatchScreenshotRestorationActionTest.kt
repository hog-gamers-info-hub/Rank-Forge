package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import com.hoggamers.rankforge.domain.tournament.MatchCloudRestorationResult
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchScreenshotRestorationActionTest {
    @Test
    fun restoresLobbyAndResultWithExactIdentityStoragePathStateAndCrop() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val lobby = lobbyPayload(matchId = MATCH_1, bytes = bytes, withCrop = true)
        val result = resultPayload(matchId = MATCH_1, bytes = bytes, withCrop = true)
        val storage = RecordingStorage(bytes)
        val lobbyRepository = RecordingLobbyRepository()
        val resultRepository = RecordingResultRepository()
        val action = action(
            storage,
            lobbyRepository,
            resultRepository,
            lobbyPayloads = listOf(lobby),
            resultPayloads = listOf(result),
        )

        assertEquals(
            MatchCloudRestorationResult.Success,
            action(TOURNAMENT_ID, setOf(MATCH_1)),
        )

        assertEquals(
            listOf(lobby.storageBucket to lobby.storageObjectPath, result.storageBucket to result.storageObjectPath),
            storage.requests,
        )
        val savedLobby = lobbyRepository.saved.single()
        assertEquals(MATCH_1, savedLobby.matchId)
        assertEquals(1, savedLobby.lobbyScreenshotIndex)
        assertEquals(ScreenshotLocalStatus.PRESERVED.name, savedLobby.localStatus)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, savedLobby.uploadStatus)
        assertEquals("lobby", savedLobby.cropProfileId)
        assertEquals(0.1, savedLobby.cropLeft ?: -1.0, 0.0)
        assertTrue(savedLobby.localRelativePath.contains("/lobby/1/original.png"))
        val savedResult = resultRepository.saved.single()
        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_UPPER.name, savedResult.screenshotRole)
        assertEquals(ScreenshotLocalStatus.PRESERVED.name, savedResult.localStatus)
        assertEquals(ScreenshotUploadStatus.UPLOADED.name, savedResult.uploadStatus)
        assertEquals("match-result", savedResult.cropProfileId)
        assertTrue(savedResult.localRelativePath.contains("/result/upper/original.png"))
    }

    @Test
    fun ignoresAssetsOutsideRestoredMatchSet() = runTest {
        val bytes = byteArrayOf(9, 8, 7)
        val storage = RecordingStorage(bytes)
        val lobbyRepository = RecordingLobbyRepository()
        val unrelated = lobbyPayload(matchId = MATCH_2, bytes = bytes)
        val action = action(
            storage,
            lobbyRepository,
            RecordingResultRepository(),
            lobbyPayloads = listOf(unrelated),
        )

        assertEquals(MatchCloudRestorationResult.ValidationFailure, action(TOURNAMENT_ID, setOf(MATCH_1)))
        assertTrue(storage.requests.isEmpty())
        assertTrue(lobbyRepository.saved.isEmpty())
    }

    @Test
    fun noCloudAssetsIsSuccessfulNoOp() = runTest {
        val lobbyRepository = RecordingLobbyRepository()
        val resultRepository = RecordingResultRepository()
        assertEquals(
            MatchCloudRestorationResult.Success,
            action(RecordingStorage(byteArrayOf()), lobbyRepository, resultRepository)(TOURNAMENT_ID, setOf(MATCH_1)),
        )
        assertTrue(lobbyRepository.saved.isEmpty())
        assertTrue(resultRepository.saved.isEmpty())
    }

    @Test
    fun sizeOrShaMismatchRejectsWithoutReplacingExistingFile() = runTest {
        val root = Files.createTempDirectory("rank-forge-e2").toFile()
        val oldBytes = byteArrayOf(1, 1, 1)
        val preserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener { null },
            mimeTypeReader = com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader { null },
        )
        val oldFile = preserver.restoreMatchLobbyScreenshot(TOURNAMENT_ID, MATCH_1, 1, "png", oldBytes)
        assertTrue(oldFile is com.hoggamers.rankforge.presentation.screen.LocalImagePreservationResult.Preserved)
        val payload = lobbyPayload(matchId = MATCH_1, bytes = byteArrayOf(2, 2, 2))
        val action = action(
            RecordingStorage(byteArrayOf(3, 3)),
            RecordingLobbyRepository(),
            RecordingResultRepository(),
            preserver = preserver,
            lobbyPayloads = listOf(payload),
        )

        assertEquals(MatchCloudRestorationResult.ValidationFailure, action(TOURNAMENT_ID, setOf(MATCH_1)))
        assertArrayEquals(oldBytes, preserver.lobbyPreservedFile(TOURNAMENT_ID, MATCH_1, 1, "png").readBytes())
    }

    @Test
    fun shaMismatchWithMatchingSizeIsRejected() = runTest {
        val expected = byteArrayOf(1, 2, 3)
        val action = action(
            RecordingStorage(byteArrayOf(4, 5, 6)),
            RecordingLobbyRepository(),
            RecordingResultRepository(),
            lobbyPayloads = listOf(lobbyPayload(matchId = MATCH_1, bytes = expected)),
        )

        assertEquals(MatchCloudRestorationResult.ValidationFailure, action(TOURNAMENT_ID, setOf(MATCH_1)))
    }

    @Test
    fun networkFailureLeavesParentAssetRepositoriesUntouched() = runTest {
        val lobbyRepository = RecordingLobbyRepository()
        val action = action(
            RecordingStorage(failure = IOExceptionForTest()),
            lobbyRepository,
            RecordingResultRepository(),
            lobbyPayloads = listOf(lobbyPayload(matchId = MATCH_1, bytes = byteArrayOf(1, 2, 3))),
        )

        assertEquals(MatchCloudRestorationResult.NetworkFailure, action(TOURNAMENT_ID, setOf(MATCH_1)))
        assertTrue(lobbyRepository.saved.isEmpty())
    }

    @Test
    fun repeatedRestorationIsIdempotentForSameIdentityAndFingerprint() = runTest {
        val bytes = byteArrayOf(4, 5, 6)
        val lobbyRepository = RecordingLobbyRepository()
        val action = action(
            RecordingStorage(bytes),
            lobbyRepository,
            RecordingResultRepository(),
            lobbyPayloads = listOf(lobbyPayload(matchId = MATCH_1, bytes = bytes)),
        )

        assertEquals(MatchCloudRestorationResult.Success, action(TOURNAMENT_ID, setOf(MATCH_1)))
        assertEquals(MatchCloudRestorationResult.Success, action(TOURNAMENT_ID, setOf(MATCH_1)))
        assertEquals(1, lobbyRepository.savedByIdentity.size)
    }

    @Test
    fun cancellationFromStoragePropagates() = runTest {
        val action = action(
            RecordingStorage(failure = CancellationException("cancelled")),
            RecordingLobbyRepository(),
            RecordingResultRepository(),
            lobbyPayloads = listOf(lobbyPayload(matchId = MATCH_1, bytes = byteArrayOf(1, 2, 3))),
        )

        var propagated = false
        try {
            action(TOURNAMENT_ID, setOf(MATCH_1))
        } catch (_: CancellationException) {
            propagated = true
        }
        assertTrue(propagated)
    }

    @Test
    fun ownerBoundRestorationRejectsSignedOutOrForeignBeforeDownload() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val storage = RecordingStorage(bytes)
        val ownerProvider = TestOwnerProvider("owner-a")
        val action = action(
            storage,
            RecordingLobbyRepository(),
            RecordingResultRepository(),
            lobbyPayloads = listOf(lobbyPayload(MATCH_1, bytes)),
            ownerProvider = ownerProvider,
        )

        assertEquals(
            MatchCloudRestorationResult.AuthorizationFailure,
            action(TOURNAMENT_ID, setOf(MATCH_1), "owner-b"),
        )
        assertTrue(storage.requests.isEmpty())
        ownerProvider.ownerId = null
        assertEquals(MatchCloudRestorationResult.AuthorizationFailure, action(TOURNAMENT_ID, setOf(MATCH_1), "owner-a"))
    }

    @Test
    fun ownerSwitchDuringMetadataFetchSkipsDownloadAndRestore() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val ownerProvider = TestOwnerProvider(OWNER_ID)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val storage = RecordingStorage(bytes)
        val repository = RecordingLobbyRepository()
        val pending = async {
            action(
                storage,
                repository,
                RecordingResultRepository(),
                lobbyCloud = SuspendingLobbyCloud(started, finish, listOf(lobbyPayload(MATCH_1, bytes))),
                ownerProvider = ownerProvider,
            )(TOURNAMENT_ID, setOf(MATCH_1), OWNER_ID)
        }
        started.await()
        ownerProvider.ownerId = "other-owner"
        finish.complete(Unit)

        assertEquals(MatchCloudRestorationResult.AuthorizationFailure, pending.await())
        assertTrue(storage.requests.isEmpty())
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun ownerSwitchDuringDownloadCleansAttemptAndSkipsRestore() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val ownerProvider = TestOwnerProvider(OWNER_ID)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val preserver = localPreserver()
        val repository = RecordingLobbyRepository()
        val pending = async {
            action(
                SuspendingStorage(started, finish, bytes),
                repository,
                RecordingResultRepository(),
                preserver = preserver,
                lobbyPayloads = listOf(lobbyPayload(MATCH_1, bytes)),
                ownerProvider = ownerProvider,
            )(TOURNAMENT_ID, setOf(MATCH_1), OWNER_ID)
        }
        started.await()
        ownerProvider.ownerId = null
        finish.complete(Unit)

        assertEquals(MatchCloudRestorationResult.AuthorizationFailure, pending.await())
        assertTrue(repository.saved.isEmpty())
        assertFalse(preserver.lobbyPreservedFile(TOURNAMENT_ID, MATCH_1, 1, "png").exists())
    }

    private fun action(
        storage: AuthenticatedScreenshotStorageDownloader,
        lobbyRepository: RecordingLobbyRepository,
        resultRepository: RecordingResultRepository,
        preserver: LocalImagePreserver = localPreserver(),
        lobbyPayloads: List<MatchLobbyScreenshotAssetCloudPayload> = emptyList(),
        resultPayloads: List<MatchResultScreenshotAssetCloudPayload> = emptyList(),
        lobbyCloud: MatchLobbyScreenshotAssetCloudDataSource = FakeLobbyCloud(lobbyPayloads),
        resultCloud: MatchResultScreenshotAssetCloudDataSource = FakeResultCloud(resultPayloads),
        ownerProvider: TestOwnerProvider = TestOwnerProvider(OWNER_ID),
    ) = SupabaseMatchScreenshotRestorationAction(
        lobbyCloud = lobbyCloud,
        resultCloud = resultCloud,
        storage = storage,
        localImagePreserver = preserver,
        lobbyAssets = lobbyRepository,
        resultAssets = resultRepository,
        ownerProvider = ownerProvider,
    )

    private fun localPreserver() = LocalImagePreserver(
        appPrivateRoot = Files.createTempDirectory("rank-forge-e2").toFile(),
        sourceStreamOpener = com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener { null },
        mimeTypeReader = com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader { null },
    )

    private fun lobbyPayload(
        matchId: String,
        bytes: ByteArray,
        withCrop: Boolean = false,
    ) = MatchLobbyScreenshotAssetCloudPayload(
        matchId = matchId,
        ownerId = OWNER_ID,
        tournamentId = TOURNAMENT_ID,
        lobbyScreenshotIndex = 1,
        localFileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = bytes.size.toLong(),
        sha256 = bytes.sha256(),
        storageBucket = "ocr-screenshots",
        storageObjectPath = "users/$OWNER_ID/tournaments/$TOURNAMENT_ID/matches/$matchId/lobby/1/original.png",
        localStatus = ScreenshotLocalStatus.MISSING.name,
        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        uploadFailureCode = null,
        cropProfileId = if (withCrop) "lobby" else null,
        cropLeft = if (withCrop) 0.1 else null,
        cropTop = if (withCrop) 0.1 else null,
        cropRight = if (withCrop) 0.9 else null,
        cropBottom = if (withCrop) 0.9 else null,
        preservedAt = TIMESTAMP,
        uploadedAt = TIMESTAMP,
        revision = 4,
        createdAt = TIMESTAMP,
        updatedAt = TIMESTAMP,
    )

    private fun resultPayload(
        matchId: String,
        bytes: ByteArray,
        withCrop: Boolean = false,
    ) = MatchResultScreenshotAssetCloudPayload(
        matchId = matchId,
        ownerId = OWNER_ID,
        tournamentId = TOURNAMENT_ID,
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER.name,
        localFileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = bytes.size.toLong(),
        sha256 = bytes.sha256(),
        storageBucket = "ocr-screenshots",
        storageObjectPath = "users/$OWNER_ID/tournaments/$TOURNAMENT_ID/matches/$matchId/result/upper/original.png",
        localStatus = ScreenshotLocalStatus.MISSING.name,
        uploadStatus = ScreenshotUploadStatus.UPLOADED.name,
        uploadFailureCode = null,
        cropProfileId = if (withCrop) "match-result" else null,
        cropLeft = if (withCrop) 0.1 else null,
        cropTop = if (withCrop) 0.1 else null,
        cropRight = if (withCrop) 0.9 else null,
        cropBottom = if (withCrop) 0.9 else null,
        preservedAt = TIMESTAMP,
        uploadedAt = TIMESTAMP,
        revision = 5,
        createdAt = TIMESTAMP,
        updatedAt = TIMESTAMP,
    )

    private class FakeLobbyCloud(
        private val assets: List<MatchLobbyScreenshotAssetCloudPayload>,
    ) : MatchLobbyScreenshotAssetCloudDataSource {
        override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity) = MatchLobbyScreenshotAssetCloudResult.Success
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = MatchLobbyScreenshotAssetCloudResult.Success
        override suspend fun readByTournamentAndMatchIds(tournamentId: String, matchIds: Set<String>) =
            MatchLobbyScreenshotAssetCloudReadResult.Success(assets)
    }

    private class FakeResultCloud(
        private val assets: List<MatchResultScreenshotAssetCloudPayload>,
    ) : MatchResultScreenshotAssetCloudDataSource {
        override suspend fun upsert(asset: MatchResultScreenshotAssetEntity) = MatchResultScreenshotAssetCloudResult.Success
        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = MatchResultScreenshotAssetCloudResult.Success
        override suspend fun readByTournamentAndMatchIds(tournamentId: String, matchIds: Set<String>) =
            MatchResultScreenshotAssetCloudReadResult.Success(assets)
    }

    private class RecordingStorage(
        private val bytes: ByteArray = byteArrayOf(),
        private val failure: Throwable? = null,
    ) : AuthenticatedScreenshotStorageDownloader {
        val requests = mutableListOf<Pair<String, String>>()
        override suspend fun download(bucket: String, objectPath: String): ByteArray {
            requests += bucket to objectPath
            failure?.let { throw it }
            return bytes
        }
        override suspend fun download(expectedOwnerUserId: String, bucket: String, objectPath: String): ByteArray {
            requests += bucket to objectPath
            failure?.let { throw it }
            return bytes
        }
    }

    private class SuspendingStorage(
        private val started: CompletableDeferred<Unit>,
        private val finish: CompletableDeferred<Unit>,
        private val bytes: ByteArray,
    ) : AuthenticatedScreenshotStorageDownloader {
        override suspend fun download(bucket: String, objectPath: String): ByteArray = bytes
        override suspend fun download(expectedOwnerUserId: String, bucket: String, objectPath: String): ByteArray {
            started.complete(Unit)
            finish.await()
            return bytes
        }
    }

    private class SuspendingLobbyCloud(
        private val started: CompletableDeferred<Unit>,
        private val finish: CompletableDeferred<Unit>,
        private val assets: List<MatchLobbyScreenshotAssetCloudPayload>,
    ) : MatchLobbyScreenshotAssetCloudDataSource {
        override suspend fun upsert(asset: MatchLobbyScreenshotAssetEntity) = MatchLobbyScreenshotAssetCloudResult.Success
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = MatchLobbyScreenshotAssetCloudResult.Success
        override suspend fun readByTournamentAndMatchIds(tournamentId: String, matchIds: Set<String>): MatchLobbyScreenshotAssetCloudReadResult {
            started.complete(Unit)
            finish.await()
            return MatchLobbyScreenshotAssetCloudReadResult.Success(assets)
        }
    }

    private class TestOwnerProvider(var ownerId: String?) : com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider {
        override suspend fun currentOwnerUserId(): String? = ownerId
    }

    private class IOExceptionForTest : java.io.IOException("network")

    private class RecordingLobbyRepository : MatchLobbyScreenshotAssetRepository {
        val saved = mutableListOf<MatchLobbyScreenshotAssetEntity>()
        val savedByIdentity get() = saved.associateBy { it.matchId to it.lobbyScreenshotIndex }
        override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(emptyList())
        override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> = flowOf(getByIdentitySync(identity))
        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity) = getByIdentitySync(identity)
        override suspend fun getByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String) =
            getByIdentitySync(identity)?.takeIf { it.ownerUserId == ownerUserId }
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = flowOf(saved)
        override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String): MatchLobbyScreenshotAssetEntity? = null
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult {
            saved.removeAll { it.matchId == asset.matchId && it.lobbyScreenshotIndex == asset.lobbyScreenshotIndex }
            saved += asset
            return MatchLobbyScreenshotAssetSaveResult.Saved
        }
        override suspend fun restoreOrReplaceByOwner(asset: MatchLobbyScreenshotAssetEntity, ownerUserId: String): MatchLobbyScreenshotAssetSaveResult =
            if (asset.ownerUserId == ownerUserId) saveOrReplace(asset) else MatchLobbyScreenshotAssetSaveResult.AuthenticationRequired
        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
        override suspend fun persistConfirmedCrop(identity: MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
        override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
        private fun getByIdentitySync(identity: MatchLobbyScreenshotIdentity) = saved.firstOrNull { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }
    }

    private class RecordingResultRepository : MatchResultScreenshotAssetRepository {
        val saved = mutableListOf<MatchResultScreenshotAssetEntity>()
        val savedByIdentity get() = saved.associateBy { it.matchId to it.screenshotRole }
        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(emptyList())
        override fun observeByIdentity(identity: MatchResultScreenshotIdentity): Flow<MatchResultScreenshotAssetEntity?> = flowOf(null)
        override suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity? = null
        override suspend fun getByIdentityAndOwner(identity: MatchResultScreenshotIdentity, ownerUserId: String): MatchResultScreenshotAssetEntity? =
            saved.firstOrNull { it.matchId == identity.matchId && it.screenshotRole == identity.role.name && it.ownerUserId == ownerUserId }
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> = flowOf(saved)
        override suspend fun findDuplicateFingerprint(identity: MatchResultScreenshotIdentity, sha256: String): MatchResultScreenshotAssetEntity? = null
        override suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult {
            saved.removeAll { it.matchId == asset.matchId && it.screenshotRole == asset.screenshotRole }
            saved += asset
            return MatchResultScreenshotAssetSaveResult.Saved
        }
        override suspend fun restoreOrReplaceByOwner(asset: MatchResultScreenshotAssetEntity, ownerUserId: String): MatchResultScreenshotAssetSaveResult =
            if (asset.ownerUserId == ownerUserId) saveOrReplace(asset) else MatchResultScreenshotAssetSaveResult.AuthenticationRequired
        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun persistConfirmedCrop(identity: MatchResultScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long) = MatchResultScreenshotCropSaveResult.Saved
        override suspend fun clearConfirmedCrop(identity: MatchResultScreenshotIdentity, updatedAt: Long) = MatchResultScreenshotCropSaveResult.Saved
        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TOURNAMENT_ID = "11111111-1111-1111-1111-111111111111"
        const val MATCH_1 = "22222222-2222-2222-2222-222222222222"
        const val MATCH_2 = "33333333-3333-3333-3333-333333333333"
        const val OWNER_ID = "44444444-4444-4444-4444-444444444444"
        val TIMESTAMP: String = Instant.ofEpochMilli(1_700_000_000_000).toString()
    }
}
