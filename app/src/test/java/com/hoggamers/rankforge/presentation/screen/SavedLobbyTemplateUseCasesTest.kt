package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetEntity
import com.hoggamers.rankforge.data.local.TournamentLobbyTemplateAssetRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import java.nio.file.Files
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedLobbyTemplateUseCasesTest {
    private val tournamentId = "template-tournament"
    private val sourceMatchId = "match-1"
    private val targetMatchId = "match-2"

    @Test
    fun saveSnapshotsAllThreeAndApplyCreatesIndependentMatchAssets() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template").toFile()
        val preserver = LocalImagePreserver(
            appPrivateRoot = root,
            sourceStreamOpener = ImageSourceStreamOpener { byteArrayOf(9).inputStream() },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        )
        val assetRepository = FakeLobbyRepository()
        (1..3).forEach { index ->
            val source = preserver.lobbyPreservedFile(tournamentId, sourceMatchId, index, "png")
            source.parentFile?.mkdirs()
            source.writeBytes(byteArrayOf(index.toByte(), 7))
            assetRepository.saveOrReplace(
                asset(index, sourceMatchId, preserver.relativePathFor(source)!!, "sha-$index"),
            )
        }
        val templateRepository = FakeTemplateRepository()
        val save = SaveLobbyTemplateUseCase(
            assetRepository = assetRepository,
            templateRepository = templateRepository,
            localImagePreserver = preserver,
            clock = Clock.systemUTC(),
        )

        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val saved = templateRepository.getByTournamentId(tournamentId)
        assertEquals(listOf(1, 2, 3), saved.map { it.lobbyScreenshotIndex })
        saved.forEach { template ->
            assertTrue(preserver.resolveRelativePath(template.localRelativePath)?.isFile == true)
        }

        val sourceFile = preserver.lobbyPreservedFile(tournamentId, sourceMatchId, 1, "png")
        sourceFile.writeBytes(byteArrayOf(1))
        val apply = ApplyLobbyTemplateToMatchUseCase(
            templateRepository = templateRepository,
            assetRepository = assetRepository,
            localImagePreserver = preserver,
            screenshotOwnerProvider = object : ScreenshotOwnerProvider {
                override suspend fun currentOwnerUserId(): String = "owner-2"
            },
            clock = Clock.systemUTC(),
        )
        assertEquals(ApplyLobbyTemplateResult.Applied, apply(tournamentId, targetMatchId))

        (1..3).forEach { index ->
            val inherited = assetRepository.getByIdentity(
                MatchLobbyScreenshotIdentity(tournamentId, targetMatchId, index),
            )
            assertNotNull(inherited)
            assertEquals("owner-2", inherited?.ownerUserId)
            assertEquals("sha-$index", inherited?.sha256)
            assertEquals(ScreenshotUploadStatus.PENDING.name, inherited?.uploadStatus)
            assertEquals(null, inherited?.storageObjectPath)
            assertEquals("lobby", inherited?.cropProfileId)
            assertTrue(inherited?.localRelativePath != saved[index - 1].localRelativePath)
            assertTrue(preserver.resolveRelativePath(inherited!!.localRelativePath)?.isFile == true)
        }
    }

    private fun asset(index: Int, matchId: String, path: String, sha: String) = MatchLobbyScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = matchId,
        lobbyScreenshotIndex = index,
        ownerUserId = "owner",
        localRelativePath = path,
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 100,
        originalHeight = 100,
        byteSize = 2,
        sha256 = sha,
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.PENDING.name,
        uploadFailureCode = null,
        storageBucket = null,
        storageObjectPath = null,
        cropProfileId = "lobby",
        cropLeft = 0.1,
        cropTop = 0.1,
        cropRight = 0.9,
        cropBottom = 0.9,
        createdAt = 1,
        updatedAt = 1,
        preservedAt = 1,
        uploadedAt = null,
        revision = 1,
    )

    private class FakeLobbyRepository : MatchLobbyScreenshotAssetRepository {
        private val state = MutableStateFlow<List<MatchLobbyScreenshotAssetEntity>>(emptyList())
        override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
            state.asStateFlow()
        override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> =
            flowOf(state.value.firstOrNull { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex })
        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity) =
            state.value.firstOrNull { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = state.asStateFlow()
        override suspend fun findDuplicateFingerprint(identity: MatchLobbyScreenshotIdentity, sha256: String) = null
        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult {
            state.value = state.value.filterNot { it.matchId == asset.matchId && it.lobbyScreenshotIndex == asset.lobbyScreenshotIndex } + asset
            return MatchLobbyScreenshotAssetSaveResult.Saved
        }
        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit
        override suspend fun deleteByMatchId(matchId: String) = Unit
        override suspend fun persistConfirmedCrop(identity: MatchLobbyScreenshotIdentity, crop: OcrNormalizedCropRect, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
        override suspend fun clearConfirmedCrop(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = MatchLobbyScreenshotCropSaveResult.Saved
    }

    private class FakeTemplateRepository : TournamentLobbyTemplateAssetRepository {
        private val state = MutableStateFlow<List<TournamentLobbyTemplateAssetEntity>>(emptyList())
        override fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>> = state
        override suspend fun getByTournamentId(tournamentId: String) = state.value.filter { it.tournamentId == tournamentId }
        override suspend fun replaceForTournament(tournamentId: String, assets: List<TournamentLobbyTemplateAssetEntity>) {
            state.value = state.value.filterNot { it.tournamentId == tournamentId } + assets
        }
    }
}
