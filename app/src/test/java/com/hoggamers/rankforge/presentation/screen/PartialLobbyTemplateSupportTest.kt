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
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import java.nio.file.Files
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialLobbyTemplateSupportTest {
    private val tournamentId = "partial-lobby-tournament"
    private val sourceMatchId = "partial-lobby-source"
    private val targetMatchId = "partial-lobby-target"

    @Test
    fun saveReadinessRequiresAtLeastOneReadyScreenshotButNotAllThree() {
        val base = MatchLobbyScreenshotIntakeUiState(
            isLoading = false,
            isAvailable = true,
            tournamentId = tournamentId,
            matchId = sourceMatchId,
            status = MatchStatus.DRAFT,
        )

        assertFalse(base.canSaveLobbyForNextMatches)
        assertTrue(base.copy(slots = readySlots(setOf(1))).canSaveLobbyForNextMatches)
        assertTrue(base.copy(slots = readySlots(setOf(1, 2))).canSaveLobbyForNextMatches)
        assertTrue(base.copy(slots = readySlots(setOf(1, 2, 3))).canSaveLobbyForNextMatches)
    }

    @Test
    fun singleReadyScreenshotCanBeSavedAndAppliedToNextMatch() = runBlocking {
        assertPartialTemplateRoundTrip(setOf(1))
    }

    @Test
    fun twoReadyScreenshotsCanBeSavedAndAppliedToNextMatch() = runBlocking {
        assertPartialTemplateRoundTrip(setOf(1, 2))
    }

    @Test
    fun saveWithoutLobbyScreenshotsRemainsNotReady() = runBlocking {
        val preserver = preserver()
        val repository = FakeLobbyRepository()
        val templateRepository = FakeTemplateRepository()

        val result = SaveLobbyTemplateUseCase(
            assetRepository = repository,
            templateRepository = templateRepository,
            localImagePreserver = preserver,
            clock = Clock.systemUTC(),
        )(tournamentId, sourceMatchId)

        assertEquals(SaveLobbyTemplateResult.NotReady, result)
        assertTrue(templateRepository.getByTournamentId(tournamentId).isEmpty())
    }

    private suspend fun assertPartialTemplateRoundTrip(indices: Set<Int>) {
        val preserver = preserver()
        val repository = FakeLobbyRepository()
        indices.forEach { index ->
            val file = preserver.lobbyPreservedFile(tournamentId, sourceMatchId, index, "png")
            file.parentFile?.mkdirs()
            file.writeBytes(byteArrayOf(index.toByte(), 7))
            repository.saveOrReplace(
                asset(
                    index = index,
                    matchId = sourceMatchId,
                    path = preserver.relativePathFor(file)!!,
                    sha = "sha-$index",
                ),
            )
        }
        val templateRepository = FakeTemplateRepository()
        val save = SaveLobbyTemplateUseCase(
            assetRepository = repository,
            templateRepository = templateRepository,
            localImagePreserver = preserver,
            clock = Clock.systemUTC(),
        )

        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val saved = templateRepository.getByTournamentId(tournamentId)
        assertEquals(indices.sorted(), saved.map { it.lobbyScreenshotIndex }.sorted())
        assertTrue(isCompleteLobbyTemplate(tournamentId, saved, preserver))

        val apply = ApplyLobbyTemplateToMatchUseCase(
            templateRepository = templateRepository,
            assetRepository = repository,
            localImagePreserver = preserver,
            screenshotOwnerProvider = object : ScreenshotOwnerProvider {
                override suspend fun currentOwnerUserId(): String = "next-owner"
            },
            clock = Clock.systemUTC(),
        )
        assertEquals(ApplyLobbyTemplateResult.Applied, apply(tournamentId, targetMatchId))

        (1..3).forEach { index ->
            val inherited = repository.getByIdentity(
                MatchLobbyScreenshotIdentity(tournamentId, targetMatchId, index),
            )
            if (index in indices) {
                assertTrue(inherited != null)
                assertEquals("sha-$index", inherited?.sha256)
                assertEquals("next-owner", inherited?.ownerUserId)
            } else {
                assertNull(inherited)
            }
        }
    }

    private fun readySlots(indices: Set<Int>): List<MatchLobbyScreenshotSlotUiState> =
        defaultMatchLobbyScreenshotSlots().map { slot ->
            if (slot.index in indices) {
                slot.copy(
                    selectedScreenshotUri = "file:///lobby-${slot.index}.png",
                    hasLinkedAsset = true,
                    confirmedCrop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9),
                    cropProfileId = "lobby",
                )
            } else {
                slot
            }
        }

    private fun preserver(): LocalImagePreserver = LocalImagePreserver(
        appPrivateRoot = Files.createTempDirectory("partial-lobby-template").toFile(),
        sourceStreamOpener = ImageSourceStreamOpener { byteArrayOf(1).inputStream() },
        mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun asset(index: Int, matchId: String, path: String, sha: String) =
        MatchLobbyScreenshotAssetEntity(
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
            flowOf(state.value.firstOrNull {
                it.tournamentId == identity.tournamentId &&
                    it.matchId == identity.matchId &&
                    it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex
            })

        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotAssetEntity? =
            state.value.firstOrNull {
                it.tournamentId == identity.tournamentId &&
                    it.matchId == identity.matchId &&
                    it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex
            }

        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
            state.asStateFlow()

        override suspend fun findDuplicateFingerprint(
            identity: MatchLobbyScreenshotIdentity,
            sha256: String,
        ): MatchLobbyScreenshotAssetEntity? = null

        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult {
            state.value = state.value.filterNot {
                it.tournamentId == asset.tournamentId &&
                    it.matchId == asset.matchId &&
                    it.lobbyScreenshotIndex == asset.lobbyScreenshotIndex
            } + asset
            return MatchLobbyScreenshotAssetSaveResult.Saved
        }

        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit
        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) {
            state.value = state.value.filterNot {
                it.tournamentId == identity.tournamentId &&
                    it.matchId == identity.matchId &&
                    it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex
            }
        }

        override suspend fun deleteByMatchId(matchId: String) {
            state.value = state.value.filterNot { it.matchId == matchId }
        }

        override suspend fun persistConfirmedCrop(
            identity: MatchLobbyScreenshotIdentity,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ) = MatchLobbyScreenshotCropSaveResult.Saved

        override suspend fun clearConfirmedCrop(
            identity: MatchLobbyScreenshotIdentity,
            updatedAt: Long,
        ) = MatchLobbyScreenshotCropSaveResult.Saved
    }

    private class FakeTemplateRepository : TournamentLobbyTemplateAssetRepository {
        private val state = MutableStateFlow<List<TournamentLobbyTemplateAssetEntity>>(emptyList())

        override fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>> =
            state.asStateFlow()

        override suspend fun getByTournamentId(tournamentId: String): List<TournamentLobbyTemplateAssetEntity> =
            state.value.filter { it.tournamentId == tournamentId }

        override suspend fun replaceForTournament(
            tournamentId: String,
            assets: List<TournamentLobbyTemplateAssetEntity>,
        ) {
            state.value = state.value.filterNot { it.tournamentId == tournamentId } + assets
        }

        override suspend fun deleteByTournamentId(tournamentId: String) {
            state.value = state.value.filterNot { it.tournamentId == tournamentId }
        }
    }
}
