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
import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.time.LocalDate
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedLobbyTemplateUseCasesTest {
    private val tournamentId = "template-tournament"
    private val sourceMatchId = "match-1"
    private val targetMatchId = "match-2"

    private fun auth(state: AuthState = AuthState.SignedIn(AuthUser("owner", "owner@example.test"))) =
        object : AuthRepository {
            override fun observeAuthState(): Flow<AuthState> = flowOf(state)
            override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
            override suspend fun signUp(email: String, password: String) = failure()
            override suspend fun login(email: String, password: String) = failure()
            override suspend fun logout() = AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
            private fun failure() = AuthOperationResult.Failure(AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure))
        }

    private fun tournamentRepository(
        ownerUserId: String? = "owner",
        sourceOwnerUserId: String? = ownerUserId,
        targetOwnerUserId: String? = ownerUserId,
        templateTournamentId: String = tournamentId,
    ) = FakeTournamentRepository(
        tournamentId = templateTournamentId,
        ownerUserId = ownerUserId,
        matches = listOf(
            Match(sourceMatchId, templateTournamentId, 1, LocalDate.of(2026, 1, 1), "map", MatchStatus.DRAFT),
            Match(targetMatchId, templateTournamentId, 2, LocalDate.of(2026, 1, 1), "map", MatchStatus.DRAFT),
        ),
        sourceOwnerUserId = sourceOwnerUserId,
        targetOwnerUserId = targetOwnerUserId,
    )

    @Test
    fun signedOutSaveApplyAndUnsaveFailClosedBeforeAnyTemplateOrFileAccess() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-auth").toFile()
        val preserver = preserver(root)
        val assets = FakeLobbyRepository()
        val templates = FakeTemplateRepository()
        val tournamentRepository = tournamentRepository()
        assertEquals(
            SaveLobbyTemplateResult.AuthenticationRequired,
            SaveLobbyTemplateUseCase(assets, templates, preserver, Clock.systemUTC(), auth(AuthState.SignedOut), tournamentRepository)(tournamentId, sourceMatchId),
        )
        assertEquals(
            ApplyLobbyTemplateResult.AuthenticationRequired,
            ApplyLobbyTemplateToMatchUseCase(templates, assets, preserver, Clock.systemUTC(), auth(AuthState.SignedOut), tournamentRepository)(tournamentId, targetMatchId),
        )
        assertEquals(
            UnsaveLobbyTemplateResult.AuthenticationRequired,
            UnsaveLobbyTemplateUseCase(templates, preserver, auth(AuthState.SignedOut), tournamentRepository)(tournamentId),
        )
        assertEquals(
            UnsaveLobbyTemplateResult.Failed,
            UnsaveLobbyTemplateUseCase(templates, preserver, auth(), tournamentRepository(ownerUserId = "owner-b"))(tournamentId),
        )
        assertEquals(
            UnsaveLobbyTemplateResult.Failed,
            UnsaveLobbyTemplateUseCase(templates, preserver, auth(), tournamentRepository(ownerUserId = null))(tournamentId),
        )
        assertEquals(0, assets.ownerReadCalls)
        assertEquals(0, templates.ownerReadCalls)
        assertEquals(0, templates.ownerWriteCalls)
    }

    @Test
    fun blankOwnerAndForeignOrNullParentRejectSaveWithoutReadingAssets() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-owner-reject").toFile()
        val preserver = preserver(root)
        val assets = FakeLobbyRepository()
        val templates = FakeTemplateRepository()
        assertEquals(
            SaveLobbyTemplateResult.AuthenticationRequired,
            SaveLobbyTemplateUseCase(assets, templates, preserver, Clock.systemUTC(), auth(AuthState.SignedIn(AuthUser("   ", null))), tournamentRepository())(tournamentId, sourceMatchId),
        )
        assertEquals(
            SaveLobbyTemplateResult.Failed,
            SaveLobbyTemplateUseCase(assets, templates, preserver, Clock.systemUTC(), auth(), tournamentRepository(ownerUserId = "owner-b"))(tournamentId, sourceMatchId),
        )
        assertEquals(
            SaveLobbyTemplateResult.Failed,
            SaveLobbyTemplateUseCase(assets, templates, preserver, Clock.systemUTC(), auth(), tournamentRepository(sourceOwnerUserId = "owner-b"))(tournamentId, sourceMatchId),
        )
        assertEquals(
            SaveLobbyTemplateResult.Failed,
            SaveLobbyTemplateUseCase(assets, templates, preserver, Clock.systemUTC(), auth(), tournamentRepository(ownerUserId = null))(tournamentId, sourceMatchId),
        )
        assertEquals(0, assets.ownerReadCalls)
        assertEquals(0, templates.ownerWriteCalls)
    }

    @Test
    fun applyForeignTargetOrSourceAndNullParentRejectsBeforeCopyOrWrite() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-apply-reject").toFile()
        val preserver = preserver(root)
        val assets = FakeLobbyRepository()
        val templates = FakeTemplateRepository()
        val ownerRepository = tournamentRepository()
        seedSourceAssets(preserver, assets, listOf("A", "B", "C"))
        assertEquals(SaveLobbyTemplateResult.Saved, SaveLobbyTemplateUseCase(assets, templates, preserver, Clock.systemUTC(), auth(), ownerRepository)(tournamentId, sourceMatchId))

        val foreignTargetRepository = tournamentRepository(targetOwnerUserId = "owner-b")
        assertEquals(
            ApplyLobbyTemplateResult.Unavailable,
            ApplyLobbyTemplateToMatchUseCase(templates, assets, preserver, Clock.systemUTC(), auth(), foreignTargetRepository)(tournamentId, targetMatchId),
        )
        val foreignSourceRepository = tournamentRepository(sourceOwnerUserId = "owner-b")
        assertEquals(
            ApplyLobbyTemplateResult.Unavailable,
            ApplyLobbyTemplateToMatchUseCase(templates, assets, preserver, Clock.systemUTC(), auth(), foreignSourceRepository)(tournamentId, targetMatchId),
        )
        val nullParentRepository = tournamentRepository(ownerUserId = null)
        assertEquals(
            ApplyLobbyTemplateResult.Unavailable,
            ApplyLobbyTemplateToMatchUseCase(templates, assets, preserver, Clock.systemUTC(), auth(), nullParentRepository)(tournamentId, targetMatchId),
        )
        assertEquals(0, assets.ownerWriteCalls)
    }

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
        val tournamentRepository = tournamentRepository()
        val authRepository = auth()
        val save = SaveLobbyTemplateUseCase(
            assetRepository = assetRepository,
            templateRepository = templateRepository,
            localImagePreserver = preserver,
            clock = Clock.systemUTC(),
            authRepository = authRepository,
            tournamentRepository = tournamentRepository,
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
            clock = Clock.systemUTC(),
            authRepository = authRepository,
            tournamentRepository = tournamentRepository,
        )
        assertEquals(ApplyLobbyTemplateResult.Applied, apply(tournamentId, targetMatchId))

        (1..3).forEach { index ->
            val inherited = assetRepository.getByIdentity(
                MatchLobbyScreenshotIdentity(tournamentId, targetMatchId, index),
            )
            assertNotNull(inherited)
            assertEquals("owner", inherited?.ownerUserId)
            assertEquals("sha-$index", inherited?.sha256)
            assertEquals(ScreenshotUploadStatus.PENDING.name, inherited?.uploadStatus)
            assertEquals(null, inherited?.storageObjectPath)
            assertEquals("lobby", inherited?.cropProfileId)
            assertTrue(inherited?.localRelativePath != saved[index - 1].localRelativePath)
            assertTrue(preserver.resolveRelativePath(inherited!!.localRelativePath)?.isFile == true)
        }
    }

    @Test
    fun partialStagingFailurePreservesActiveTemplateAndCleansNewGeneration() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-partial").toFile()
        val sourceRepository = FakeLobbyRepository()
        val normalPreserver = preserver(root)
        seedSourceAssets(normalPreserver, sourceRepository, listOf("A", "B", "C"))
        val templateRepository = FakeTemplateRepository()
        val save = SaveLobbyTemplateUseCase(
            sourceRepository,
            templateRepository,
            normalPreserver,
            Clock.systemUTC(),
            auth(),
            tournamentRepository(),
        )
        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val previous = templateRepository.getByTournamentId(tournamentId)
        val previousFiles = previous.map { normalPreserver.resolveRelativePath(it.localRelativePath)!! }

        val failingOps = FailingTemplateFileOperations(failAfterMoves = 1)
        val failingPreserver = preserver(root, failingOps)
        assertEquals(
            SaveLobbyTemplateResult.Failed,
            SaveLobbyTemplateUseCase(
                sourceRepository,
                templateRepository,
                failingPreserver,
                Clock.systemUTC(),
                auth(),
                tournamentRepository(),
            )(tournamentId, sourceMatchId),
        )

        assertEquals(previous, templateRepository.getByTournamentId(tournamentId))
        previousFiles.forEach { assertTrue(it.isFile) }
        assertEquals(previousFiles.toSet(), allTemplateOriginals(root).toSet())
    }

    @Test
    fun roomReplacementFailurePreservesPreviousTemplateAndCleansStagedFiles() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-room-failure").toFile()
        val repository = FakeLobbyRepository()
        val preserver = preserver(root)
        seedSourceAssets(preserver, repository, listOf("A", "B", "C"))
        val templateRepository = FakeTemplateRepository()
        val save = SaveLobbyTemplateUseCase(repository, templateRepository, preserver, Clock.systemUTC(), auth(), tournamentRepository())
        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val previous = templateRepository.getByTournamentId(tournamentId)
        val previousFiles = allTemplateOriginals(root)
        templateRepository.throwOnReplace = true

        assertEquals(SaveLobbyTemplateResult.Failed, save(tournamentId, sourceMatchId))
        assertEquals(previous, templateRepository.getByTournamentId(tournamentId))
        assertEquals(previousFiles.toSet(), allTemplateOriginals(root).toSet())
    }

    @Test
    fun replacementCancellationCleansNewGenerationAndPreservesActiveTemplate() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-cancellation").toFile()
        val repository = FakeLobbyRepository()
        val preserver = preserver(root)
        seedSourceAssets(preserver, repository, listOf("A", "B", "C"))
        val templateRepository = FakeTemplateRepository()
        val save = SaveLobbyTemplateUseCase(repository, templateRepository, preserver, Clock.systemUTC(), auth(), tournamentRepository())
        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val previous = templateRepository.getByTournamentId(tournamentId)
        val previousFiles = allTemplateOriginals(root)
        var stagedFileCountAtReplacement = 0
        templateRepository.beforeReplace = {
            stagedFileCountAtReplacement = allTemplateOriginals(root).size
        }
        templateRepository.cancelOnReplace = true

        var thrownCancellation: CancellationException? = null
        try {
            save(tournamentId, sourceMatchId)
        } catch (cancellation: CancellationException) {
            thrownCancellation = cancellation
        }

        assertNotNull(thrownCancellation)
        assertEquals(6, stagedFileCountAtReplacement)
        assertEquals(previous, templateRepository.getByTournamentId(tournamentId))
        previousFiles.forEach { assertTrue(it.isFile) }
        assertEquals(previousFiles.toSet(), allTemplateOriginals(root).toSet())
    }

    @Test
    fun successfulResaveCleansPreviousGenerationOnlyAfterReplacement() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-resave").toFile()
        val repository = FakeLobbyRepository()
        val preserver = preserver(root)
        seedSourceAssets(preserver, repository, listOf("A", "B", "C"))
        val templateRepository = FakeTemplateRepository()
        val save = SaveLobbyTemplateUseCase(repository, templateRepository, preserver, Clock.systemUTC(), auth(), tournamentRepository())
        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val previous = templateRepository.getByTournamentId(tournamentId)
        val previousFiles = previous.map { preserver.resolveRelativePath(it.localRelativePath)!! }

        seedSourceAssets(preserver, repository, listOf("A", "D", "C"))
        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val current = templateRepository.getByTournamentId(tournamentId)
        assertTrue(current.all { preserver.resolveRelativePath(it.localRelativePath)?.isFile == true })
        assertTrue(current.any { it.sha256 == "sha-D" })
        previousFiles.forEach { assertFalse(it.isFile) }
        assertEquals(3, allTemplateOriginals(root).size)
    }

    @Test
    fun unsaveDeletesActiveTemplateAndCleansItsGenerationFiles() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-unsave").toFile()
        val repository = FakeLobbyRepository()
        val preserver = preserver(root)
        seedSourceAssets(preserver, repository, listOf("A", "B", "C"))
        val templateRepository = FakeTemplateRepository()
        val save = SaveLobbyTemplateUseCase(repository, templateRepository, preserver, Clock.systemUTC(), auth(), tournamentRepository())
        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val activeFiles = allTemplateOriginals(root)

        assertEquals(
            UnsaveLobbyTemplateResult.Unsaved,
            UnsaveLobbyTemplateUseCase(templateRepository, preserver, auth(), tournamentRepository())(tournamentId),
        )
        assertTrue(templateRepository.getByTournamentId(tournamentId).isEmpty())
        activeFiles.forEach { file -> assertFalse(file.isFile) }
    }

    @Test
    fun unsaveLeavesCurrentAndPreviouslyInheritedMatchAssetsUntouched() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-preserve").toFile()
        val repository = FakeLobbyRepository()
        val preserver = preserver(root)
        seedSourceAssets(preserver, repository, listOf("A", "B", "C"))
        val templateRepository = FakeTemplateRepository()
        val save = SaveLobbyTemplateUseCase(repository, templateRepository, preserver, Clock.systemUTC(), auth(), tournamentRepository())
        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val apply = ApplyLobbyTemplateToMatchUseCase(
            templateRepository,
            repository,
            preserver,
            Clock.systemUTC(),
            auth(),
            tournamentRepository(),
        )
        assertEquals(ApplyLobbyTemplateResult.Applied, apply(tournamentId, targetMatchId))
        val before = repository.snapshot()

        assertEquals(
            UnsaveLobbyTemplateResult.Unsaved,
            UnsaveLobbyTemplateUseCase(templateRepository, preserver, auth(), tournamentRepository())(tournamentId),
        )
        assertEquals(before, repository.snapshot())
    }

    @Test
    fun unsaveDeletionFailurePreservesRowsAndFiles() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-unsave-room-failure").toFile()
        val repository = FakeLobbyRepository()
        val preserver = preserver(root)
        seedSourceAssets(preserver, repository, listOf("A", "B", "C"))
        val templateRepository = FakeTemplateRepository()
        val save = SaveLobbyTemplateUseCase(repository, templateRepository, preserver, Clock.systemUTC(), auth(), tournamentRepository())
        assertEquals(SaveLobbyTemplateResult.Saved, save(tournamentId, sourceMatchId))
        val previous = templateRepository.getByTournamentId(tournamentId)
        val previousFiles = allTemplateOriginals(root)
        templateRepository.throwOnDelete = true

        assertEquals(
            UnsaveLobbyTemplateResult.Failed,
            UnsaveLobbyTemplateUseCase(templateRepository, preserver, auth(), tournamentRepository())(tournamentId),
        )
        assertEquals(previous, templateRepository.getByTournamentId(tournamentId))
        previousFiles.forEach { file -> assertTrue(file.isFile) }
    }

    @Test
    fun cleanupFailureAfterUnsaveLeavesRepositoryOff() = runBlocking {
        val root = Files.createTempDirectory("saved-lobby-template-unsave-cleanup-failure").toFile()
        val repository = FakeLobbyRepository()
        val normalPreserver = preserver(root)
        seedSourceAssets(normalPreserver, repository, listOf("A", "B", "C"))
        val templateRepository = FakeTemplateRepository()
        assertEquals(
            SaveLobbyTemplateResult.Saved,
            SaveLobbyTemplateUseCase(repository, templateRepository, normalPreserver, Clock.systemUTC(), auth(), tournamentRepository())(
                tournamentId,
                sourceMatchId,
            ),
        )
        val failingPreserver = preserver(root, CleanupFailingTemplateFileOperations())

        assertEquals(
            UnsaveLobbyTemplateResult.Unsaved,
            UnsaveLobbyTemplateUseCase(templateRepository, failingPreserver, auth(), tournamentRepository())(tournamentId),
        )
        assertTrue(templateRepository.getByTournamentId(tournamentId).isEmpty())
    }

    @Test
    fun unsaveWithoutActiveTemplateIsIdempotentlySuccessful() = runBlocking {
        val templateRepository = FakeTemplateRepository()
        val preserver = preserver(Files.createTempDirectory("saved-lobby-template-unsave-empty").toFile())

        assertEquals(
            UnsaveLobbyTemplateResult.Unsaved,
            UnsaveLobbyTemplateUseCase(templateRepository, preserver, auth(), tournamentRepository())(tournamentId),
        )
    }

    private suspend fun seedSourceAssets(
        preserver: LocalImagePreserver,
        repository: FakeLobbyRepository,
        labels: List<String>,
    ) {
        labels.forEachIndexed { offset, label ->
            val index = offset + 1
            val source = preserver.lobbyPreservedFile(tournamentId, sourceMatchId, index, "png")
            source.parentFile?.mkdirs()
            source.writeBytes(label.toByteArray())
            repository.saveOrReplace(
                asset(index, sourceMatchId, preserver.relativePathFor(source)!!, "sha-$label"),
            )
        }
    }

    private fun preserver(
        root: java.io.File,
        fileOperations: LocalImageFileOperations = TestTemplateFileOperations(),
    ) = LocalImagePreserver(
        appPrivateRoot = root,
        sourceStreamOpener = ImageSourceStreamOpener { byteArrayOf(1).inputStream() },
        mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
        fileOperations = fileOperations,
        ioDispatcher = Dispatchers.Unconfined,
    )

    private fun allTemplateOriginals(root: java.io.File): List<java.io.File> =
        if (!root.exists()) emptyList() else Files.walk(root.toPath()).use { paths ->
            paths.filter { it.fileName.toString().startsWith("original.") }
                .filter { it.toString().replace('\\', '/').contains("/lobby-template/") }
                .map { it.toFile() }
                .toList()
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
        var ownerReadCalls = 0
        var ownerWriteCalls = 0
        override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
            state.asStateFlow()
        override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> =
            flowOf(state.value.firstOrNull { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex })
        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity) =
            state.value.firstOrNull { it.matchId == identity.matchId && it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex }
        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> = state.asStateFlow()
        override suspend fun getByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String) =
            state.value.also { ownerReadCalls++ }.firstOrNull {
                it.tournamentId == identity.tournamentId && it.matchId == identity.matchId &&
                    it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex && it.ownerUserId == ownerUserId
            }
        override suspend fun saveOrReplaceByOwner(asset: MatchLobbyScreenshotAssetEntity, ownerUserId: String) =
            saveOrReplace(asset.copy(ownerUserId = ownerUserId)).also { ownerWriteCalls++ }
        override suspend fun deleteByIdentityAndOwner(identity: MatchLobbyScreenshotIdentity, ownerUserId: String): Boolean {
            val before = state.value.size
            state.value = state.value.filterNot {
                it.tournamentId == identity.tournamentId && it.matchId == identity.matchId &&
                    it.lobbyScreenshotIndex == identity.lobbyScreenshotIndex && it.ownerUserId == ownerUserId
            }
            return state.value.size != before
        }
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
        fun snapshot() = state.value
    }

    private class FakeTemplateRepository : TournamentLobbyTemplateAssetRepository {
        private val state = MutableStateFlow<List<TournamentLobbyTemplateAssetEntity>>(emptyList())
        var ownerReadCalls = 0
        var ownerWriteCalls = 0
        var throwOnReplace: Boolean = false
        var throwOnDelete: Boolean = false
        var cancelOnReplace: Boolean = false
        var beforeReplace: (() -> Unit)? = null
        override fun observeByTournamentId(tournamentId: String): Flow<List<TournamentLobbyTemplateAssetEntity>> = state
        override suspend fun getByTournamentId(tournamentId: String) = state.value.filter { it.tournamentId == tournamentId }
        override fun observeByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Flow<List<TournamentLobbyTemplateAssetEntity>> =
            state.asStateFlow().map { templates -> templates.filter { it.tournamentId == tournamentId && it.ownerUserId == ownerUserId } }
        override suspend fun getByTournamentIdAndOwner(tournamentId: String, ownerUserId: String) =
            state.value.also { ownerReadCalls++ }.filter { it.tournamentId == tournamentId && it.ownerUserId == ownerUserId }
        override suspend fun replaceForTournament(tournamentId: String, assets: List<TournamentLobbyTemplateAssetEntity>) {
            beforeReplace?.invoke()
            if (cancelOnReplace) throw CancellationException("replacement cancelled")
            if (throwOnReplace) error("replacement failed")
            state.value = state.value.filterNot { it.tournamentId == tournamentId } + assets
        }

        override suspend fun deleteByTournamentId(tournamentId: String) {
            if (throwOnDelete) error("deletion failed")
            state.value = state.value.filterNot { it.tournamentId == tournamentId }
        }
        override suspend fun replaceForTournamentByOwner(
            tournamentId: String,
            ownerUserId: String,
            assets: List<TournamentLobbyTemplateAssetEntity>,
        ): Boolean {
            ownerWriteCalls++
            beforeReplace?.invoke()
            if (cancelOnReplace) throw CancellationException("replacement cancelled")
            if (throwOnReplace) error("replacement failed")
            state.value = state.value.filterNot { it.tournamentId == tournamentId } + assets
            return true
        }
        override suspend fun deleteByTournamentIdAndOwner(tournamentId: String, ownerUserId: String): Boolean {
            ownerWriteCalls++
            if (throwOnDelete) error("deletion failed")
            val before = state.value.size
            state.value = state.value.filterNot { it.tournamentId == tournamentId && it.ownerUserId == ownerUserId }
            return state.value.size != before || before == 0
        }
    }

    private class FakeTournamentRepository(
        private val tournamentId: String,
        private val ownerUserId: String?,
        private val matches: List<Match>,
        private val sourceOwnerUserId: String?,
        private val targetOwnerUserId: String?,
    ) : TournamentRepository {
        private val tournament = Tournament(
            id = tournamentId,
            name = "template",
            date = LocalDate.of(2026, 1, 1),
            organizerName = "organizer",
            organizerContactNumber = "contact",
            status = TournamentStatus.DRAFT,
            ownerUserId = ownerUserId,
        )

        override suspend fun create(tournament: Tournament) = Unit
        override fun observeAll(): Flow<List<Tournament>> = flowOf(listOf(tournament))
        override fun observeById(tournamentId: String): Flow<Tournament?> = flowOf(tournament.takeIf { it.id == tournamentId })
        override fun observeSlotsByTournamentId(tournamentId: String): Flow<List<TeamSlot>> = flowOf(emptyList())
        override suspend fun saveTeamNames(tournamentId: String, teamNamesBySlotNumber: Map<Int, String>) = Unit
        override fun observeRosterByTournamentAndSlot(tournamentId: String, slotNumber: Int): Flow<List<RosterPlayer>> = flowOf(emptyList())
        override suspend fun saveRoster(tournamentId: String, slotNumber: Int, players: List<RosterPlayer>) = Unit
        override suspend fun confirmTournament(tournamentId: String): Boolean = true
        override fun observeMatchById(matchId: String): Flow<Match?> = flowOf(matches.firstOrNull { it.id == matchId })
        override fun observeMatchByIdAndOwner(matchId: String, ownerUserId: String): Flow<Match?> = flowOf(
            matches.firstOrNull { match ->
                match.id == matchId && match.tournamentId == tournamentId &&
                    when (match.id) {
                        matches.firstOrNull()?.id -> sourceOwnerUserId == ownerUserId
                        matches.getOrNull(1)?.id -> targetOwnerUserId == ownerUserId
                        else -> this.ownerUserId == ownerUserId
                    }
            },
        )
    }

    private open class TestTemplateFileOperations : LocalImageFileOperations {
        override fun ensureDirectory(directory: java.io.File): Boolean =
            directory.isDirectory || (directory.mkdirs() && directory.isDirectory)

        override fun createTempFile(directory: java.io.File): java.io.File =
            java.io.File.createTempFile("original-", ".tmp", directory)

        override fun openOutput(file: java.io.File): java.io.OutputStream = file.outputStream()

        override fun atomicMove(source: java.io.File, target: java.io.File): Boolean = try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            true
        } catch (_: Exception) {
            false
        }

        override fun listFiles(directory: java.io.File): List<java.io.File>? =
            if (!directory.exists()) emptyList() else directory.listFiles()?.toList()

        override fun delete(file: java.io.File): Boolean = !file.exists() || file.delete()
    }

    private class FailingTemplateFileOperations(
        private val failAfterMoves: Int,
    ) : TestTemplateFileOperations() {
        private var moves = 0

        override fun atomicMove(source: java.io.File, target: java.io.File): Boolean {
            if (moves++ >= failAfterMoves) return false
            return super.atomicMove(source, target)
        }
    }

    private class CleanupFailingTemplateFileOperations : TestTemplateFileOperations() {
        override fun delete(file: java.io.File): Boolean =
            if (file.path.replace('\\', '/').contains("/lobby-template/")) false else super.delete(file)
    }
}
