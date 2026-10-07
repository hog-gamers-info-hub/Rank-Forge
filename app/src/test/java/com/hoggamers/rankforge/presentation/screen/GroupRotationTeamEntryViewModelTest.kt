package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupCandidate
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftSaveResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupLocalSaveResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupReadRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupReadResult
import com.hoggamers.rankforge.domain.tournament.ReadGroupRotationTeamSetupUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamSetupUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupRotationTeamEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var readRepository: FakeReadRepository
    private lateinit var saveRepository: FakeSaveRepository
    private lateinit var draftRepository: FakeDraftRepository

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        readRepository = FakeReadRepository()
        saveRepository = FakeSaveRepository()
        draftRepository = FakeDraftRepository()
        TestAuthRepository.state = AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test"))
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun noSavedSetupStartsWithOrderedPairingsAndTwelveLocalRows() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("A:B", "A:C"), viewModel.uiState.value.pairingSections.map { it.pairing.canonicalKey })
        assertEquals((1..12).toList(), viewModel.uiState.value.pairingSections.first().rows.map { it.lobbySlotNumber })
        assertEquals(18, viewModel.uiState.value.maximumUniqueTeams)
    }

    @Test
    fun validDraftWinsOverAuthoritativeSetup() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.Loaded(
            com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupRead(
                tournament(),
                completeCandidate("Authoritative"),
            ),
        )
        draftRepository.raw = completeCandidate("Draft").entries
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(
            "Draft 1",
            viewModel.uiState.value.pairingSections.first().rows.first().teamName,
        )
    }

    @Test
    fun invalidNonEmptyDraftShowsErrorInsteadOfFallingBack() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.Loaded(
            com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupRead(
                tournament(),
                completeCandidate("Authoritative"),
            ),
        )
        draftRepository.raw = listOf(entry("A:B", 1, "Invalid draft"))
        val rawBeforeLoad = draftRepository.raw
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(GroupRotationTeamEntryLoadError.InvalidStoredDraft, viewModel.uiState.value.loadError)
        assertTrue(viewModel.uiState.value.pairingSections.isEmpty())
        assertEquals(rawBeforeLoad, draftRepository.raw)
        assertFalse(viewModel.uiState.value.tournamentName == "Authoritative")
    }

    @Test
    fun editingOneLocalRowWritesCompleteDraftAcrossAllSelectedPairings() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        viewModel.onTeamNameChanged(GroupPairing.fromCanonicalKey("A:B"), 1, "Alpha")
        advanceUntilIdle()

        assertEquals(24, draftRepository.raw.size)
        assertEquals("Alpha", draftRepository.raw.single { it.pairing.canonicalKey == "A:B" && it.lobbySlotNumber == 1 }.teamName)
        assertEquals(12, draftRepository.raw.count { it.pairing.canonicalKey == "A:C" })
        assertTrue(draftRepository.raw.filter { it.pairing.canonicalKey == "A:C" }.all { it.teamName.isEmpty() })
    }

    @Test
    fun pasteReplacesTheSelectedPairAndClearsRemainingRows() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        val pairing = GroupPairing.fromCanonicalKey("A:B")
        viewModel.onTeamNameChanged(pairing, 1, "Old 1")
        viewModel.onTeamNameChanged(pairing, 2, "Old 2")
        viewModel.onTeamNameChanged(pairing, 3, "Old 3")

        viewModel.onBulkTeamNamesApplied(listOf("New 1", "New 2"))

        val selectedRows = viewModel.uiState.value.pairingSections.first().rows
        assertEquals(listOf("New 1", "New 2", "", ""), selectedRows.take(4).map { it.teamName })
        assertEquals(0, viewModel.uiState.value.pairingSections[1].filledCount)
    }

    @Test
    fun invalidBlankSetupStaysReviewRequiredAndDoesNotSave() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.validationIssues.isNotEmpty())
        assertTrue(saveRepository.savedCandidate == null)
    }

    @Test
    fun validSetupSavesLocallyClearsDraftAndNavigates() = runTest {
        val complete = completeCandidate("Team")
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = complete.entries
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        val navigation = async { viewModel.navigationEvents.first() }

        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(complete.entries, saveRepository.savedCandidate?.entries)
        assertTrue(draftRepository.cleared)
        assertEquals(
            GroupRotationTeamEntryNavigationEvent.BackToTournamentDetails,
            navigation.await(),
        )
    }

    @Test
    fun fourGroupSetupUsesTwentyFourTeamMaximumAndDeterministicPairingOrder() = runTest {
        val fourGroupTournament = tournament(
            id = "group-rotation-entry-four",
            groupCount = 4,
            pairings = listOf("A:C", "A:B", "B:D", "B:C", "C:D", "A:D"),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(fourGroupTournament)
        val viewModel = viewModel()

        viewModel.load(fourGroupTournament.id)
        advanceUntilIdle()

        assertEquals(24, viewModel.uiState.value.maximumUniqueTeams)
        assertEquals(
            listOf("A:B", "B:C", "C:D", "A:D", "A:C", "B:D"),
            viewModel.uiState.value.pairingSections.map { it.pairing.canonicalKey },
        )
    }

    @Test
    fun authenticationAndStoredReadFailuresAreExposed() = runTest {
        TestAuthRepository.state = AuthState.SignedOut
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        assertEquals(GroupRotationTeamEntryLoadError.AuthenticationRequired, viewModel.uiState.value.loadError)

        TestAuthRepository.state = AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test"))
        listOf(
            GroupRotationTeamSetupReadResult.TournamentNotFound to GroupRotationTeamEntryLoadError.TournamentNotFound,
            GroupRotationTeamSetupReadResult.InvalidStoredSetup to GroupRotationTeamEntryLoadError.InvalidStoredSetup,
        ).forEach { (result, expectedError) ->
            readRepository.result = result
            val nextViewModel = viewModel()
            nextViewModel.load(TOURNAMENT_ID)
            advanceUntilIdle()
            assertEquals(expectedError, nextViewModel.uiState.value.loadError)
        }
    }

    @Test
    fun switchingPairingPreservesEditsAndDoesNotWriteAnotherDraft() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        val first = GroupPairing.fromCanonicalKey("A:B")
        val second = GroupPairing.fromCanonicalKey("A:C")
        viewModel.onTeamNameChanged(first, 1, "Alpha")
        advanceUntilIdle()
        val writesBeforeSwitch = draftRepository.replaceCalls

        viewModel.onPairingSelected(second)

        assertEquals(writesBeforeSwitch, draftRepository.replaceCalls)
        assertEquals("Alpha", viewModel.uiState.value.pairingSections.first().rows.first().teamName)
        assertEquals("A:C", viewModel.uiState.value.selectedPairingKey)
    }

    @Test
    fun rawWhitespaceIsPreservedInDraftAndNormalizedCountsIgnoreIt() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        val first = GroupPairing.fromCanonicalKey("A:B")
        viewModel.onTeamNameChanged(first, 1, "Alpha")
        viewModel.onTeamNameChanged(first, 2, " ALPHA ")
        viewModel.onTeamNameChanged(first, 3, "Alpha   Team")
        viewModel.onTeamNameChanged(first, 4, " alpha team ")
        advanceUntilIdle()

        assertEquals(" ALPHA ", draftRepository.raw.single { it.pairing == first && it.lobbySlotNumber == 2 }.teamName)
        assertEquals(2, viewModel.uiState.value.uniqueTeamCount)
    }

    @Test
    fun uniqueCountIgnoresBlanksNormalizesWhitespaceAndKeepsPunctuationSignificant() {
        val pairing = GroupPairing.fromCanonicalKey("A:B")
        val state = GroupRotationTeamEntryUiState(
            pairingSections = listOf(
                GroupRotationPairingEntryUiState(
                    pairing = pairing,
                    rows = listOf("Alpha", " ALPHA ", "Alpha   Team", " alpha team ", "Alpha-Team", "Alpha Team")
                        .mapIndexed { index, value -> GroupRotationLobbyTeamUiState(index + 1, value) } +
                        (7..12).map { GroupRotationLobbyTeamUiState(it, "") },
                ),
            ),
        )

        assertEquals(3, state.uniqueNormalizedTeamCount())
    }

    @Test
    fun duplicateAndTooManyUniqueIdentitiesBlockSaveAndSelectFirstAffectedPairing() = runTest {
        val duplicate = completeCandidate("Team").copy(
            entries = completeCandidate("Team").entries.map {
                if (it.pairing.canonicalKey == "A:C" && it.lobbySlotNumber == 2) {
                    it.copy(teamName = "Team 1")
                } else {
                    it
                }
            },
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = duplicate.entries
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.validationIssues.any {
            it.code == com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssueCode.DUPLICATE_TEAM_IDENTITY_WITHIN_PAIRING
        })
        assertEquals("A:C", viewModel.uiState.value.selectedPairingKey)
        assertEquals(null, saveRepository.savedCandidate)

        val tooMany = GroupRotationTeamSetupCandidate(
            TOURNAMENT_ID,
            (1..12).map { entry("A:B", it, "Unique $it") } +
                (1..12).map { entry("A:C", it, "Unique ${it + 12}") },
        )
        draftRepository.raw = tooMany.entries
        val tooManyViewModel = viewModel()
        tooManyViewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        tooManyViewModel.saveTeamNames()
        advanceUntilIdle()
        assertTrue(tooManyViewModel.uiState.value.validationIssues.any {
            it.code == com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssueCode.TOO_MANY_UNIQUE_TEAMS
        })
    }

    @Test
    fun unchangedSuccessfulSaveUsesCleanedDisplayNames() = runTest {
        val candidate = completeCandidate("Team").copy(
            entries = completeCandidate("Team").entries.map {
                when {
                    it.pairing.canonicalKey == "A:B" && it.lobbySlotNumber == 1 ->
                        it.copy(teamName = "  Alpha   Team  ")
                    it.pairing.canonicalKey == "A:C" && it.lobbySlotNumber == 1 ->
                        it.copy(teamName = " alpha team ")
                    else -> it
                }
            },
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = candidate.entries
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(
            "Alpha Team",
            viewModel.uiState.value.pairingSections.first().rows.first().teamName,
        )
        assertTrue(draftRepository.cleared)
    }

    @Test
    fun queuedDraftWriteUsesCapturedTournamentAfterLoadingAnotherTournament() = runTest {
        val tournamentA = tournament(id = TOURNAMENT_ID)
        val tournamentB = tournament(id = "other-tournament")
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentA)
        draftRepository.blockedTournamentId = tournamentA.id
        val viewModel = viewModel()
        viewModel.load(tournamentA.id)
        advanceUntilIdle()
        viewModel.onTeamNameChanged(GroupPairing.fromCanonicalKey("A:B"), 1, "Alpha")
        runCurrent()
        assertTrue(draftRepository.writeStarted.isCompleted)

        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentB)
        viewModel.load(tournamentB.id)
        advanceUntilIdle()
        draftRepository.releaseWrite.complete(Unit)
        advanceUntilIdle()

        assertEquals("Alpha", draftRepository.persistedByTournament[tournamentA.id]
            ?.single { it.pairing.canonicalKey == "A:B" && it.lobbySlotNumber == 1 }
            ?.teamName)
    }

    @Test
    fun staleSuccessfulSaveDoesNotChangeNewerTournamentOrClearItsDraft() = runTest {
        val tournamentA = tournament(id = TOURNAMENT_ID)
        val tournamentB = tournament(id = "other-tournament")
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentA)
        draftRepository.raw = completeCandidate("A").entries
        saveRepository.blocked = true
        val viewModel = viewModel()
        viewModel.load(tournamentA.id)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        runCurrent()
        assertTrue(saveRepository.saveStarted.isCompleted)

        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentB)
        draftRepository.raw = completeCandidate("B").entries
        viewModel.load(tournamentB.id)
        advanceUntilIdle()
        val navigation = async { viewModel.navigationEvents.first() }

        saveRepository.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("B 1", viewModel.uiState.value.pairingSections.first().rows.first().teamName)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(draftRepository.cleared)
        assertFalse(navigation.isCompleted)
        navigation.cancel()
    }

    @Test
    fun staleSuccessfulSaveCannotClearDraftAfterLoadingBAndAAgain() = runTest {
        val tournamentA = tournament(id = TOURNAMENT_ID)
        val tournamentB = tournament(id = "other-tournament")
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentA)
        draftRepository.raw = completeCandidate("A-old").entries
        saveRepository.blocked = true
        val viewModel = viewModel()
        viewModel.load(tournamentA.id)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        runCurrent()
        assertTrue(saveRepository.saveStarted.isCompleted)

        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentB)
        draftRepository.raw = completeCandidate("B").entries
        viewModel.load(tournamentB.id)
        advanceUntilIdle()
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentA)
        draftRepository.raw = completeCandidate("A-new").entries
        viewModel.load(tournamentA.id)
        advanceUntilIdle()

        saveRepository.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("A-new 1", viewModel.uiState.value.pairingSections.first().rows.first().teamName)
        assertFalse(viewModel.uiState.value.isSaving)
        assertFalse(draftRepository.cleared)
    }

    @Test
    fun staleProtectedHistoryDoesNotSurfaceAfterLoadingAnotherTournament() = runTest {
        val tournamentA = tournament(id = TOURNAMENT_ID)
        val tournamentB = tournament(id = "other-tournament")
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentA)
        draftRepository.raw = completeCandidate("A").entries
        saveRepository.blocked = true
        saveRepository.result = GroupRotationTeamSetupLocalSaveResult.ProtectedHistory
        val viewModel = viewModel()
        viewModel.load(tournamentA.id)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        runCurrent()
        assertTrue(saveRepository.saveStarted.isCompleted)

        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentB)
        draftRepository.raw = completeCandidate("B").entries
        viewModel.load(tournamentB.id)
        advanceUntilIdle()
        saveRepository.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("B 1", viewModel.uiState.value.pairingSections.first().rows.first().teamName)
        assertEquals(null, viewModel.uiState.value.saveError)
        assertTrue(viewModel.uiState.value.validationIssues.isEmpty())
        assertFalse(draftRepository.cleared)
    }

    @Test
    fun staleInvalidSetupDoesNotSurfaceAfterLoadingAnotherTournament() = runTest {
        val tournamentA = tournament(id = TOURNAMENT_ID)
        val tournamentB = tournament(id = "other-tournament")
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentA)
        draftRepository.raw = completeCandidate("A").entries
        saveRepository.blocked = true
        saveRepository.result = GroupRotationTeamSetupLocalSaveResult.InvalidSetup(
            listOf(
                com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssue(
                    com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssueCode.INVALID_LOBBY_SLOT,
                ),
            ),
        )
        val viewModel = viewModel()
        viewModel.load(tournamentA.id)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        runCurrent()
        assertTrue(saveRepository.saveStarted.isCompleted)

        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentB)
        draftRepository.raw = completeCandidate("B").entries
        viewModel.load(tournamentB.id)
        advanceUntilIdle()
        saveRepository.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("B 1", viewModel.uiState.value.pairingSections.first().rows.first().teamName)
        assertTrue(viewModel.uiState.value.validationIssues.isEmpty())
        assertEquals(null, viewModel.uiState.value.saveError)
        assertFalse(draftRepository.cleared)
    }

    @Test
    fun duplicateSaveIsIgnoredAndNewerEditSurvivesBlockedSave() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = completeCandidate("Team").entries
        saveRepository.blocked = true
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        viewModel.saveTeamNames()
        runCurrent()
        viewModel.saveTeamNames()
        assertEquals(1, saveRepository.saveCalls)

        val navigation = async { viewModel.navigationEvents.first() }
        viewModel.onTeamNameChanged(GroupPairing.fromCanonicalKey("A:B"), 1, "Newer")
        advanceUntilIdle()
        saveRepository.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("Newer", viewModel.uiState.value.pairingSections.first().rows.first().teamName)
        assertEquals("Newer", draftRepository.raw.first { it.pairing.canonicalKey == "A:B" && it.lobbySlotNumber == 1 }.teamName)
        assertFalse(draftRepository.cleared)
        assertTrue(viewModel.uiState.value.validationIssues.isEmpty())
        assertEquals(null, viewModel.uiState.value.saveError)
        assertFalse(navigation.isCompleted)
        navigation.cancel()
    }

    @Test
    fun protectedHistoryAuthenticationNotFoundAndInvalidSetupAreSurfaced() = runTest {
        val complete = completeCandidate("Team")
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = complete.entries
        val results = listOf(
            GroupRotationTeamSetupLocalSaveResult.ProtectedHistory to GroupRotationTeamEntrySaveError.ProtectedHistory,
            GroupRotationTeamSetupLocalSaveResult.TournamentNotFound to GroupRotationTeamEntrySaveError.TournamentNotFound,
        )
        results.forEach { (localResult, expectedError) ->
            saveRepository.result = localResult
            val viewModel = viewModel()
            viewModel.load(TOURNAMENT_ID)
            advanceUntilIdle()
            viewModel.saveTeamNames()
            advanceUntilIdle()
            assertEquals(expectedError, viewModel.uiState.value.saveError)
        }

        TestAuthRepository.state = AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test"))
        val authenticationViewModel = viewModel()
        authenticationViewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        TestAuthRepository.state = AuthState.SignedOut
        authenticationViewModel.saveTeamNames()
        advanceUntilIdle()
        assertEquals(
            GroupRotationTeamEntrySaveError.AuthenticationRequired,
            authenticationViewModel.uiState.value.saveError,
        )

        saveRepository.result = GroupRotationTeamSetupLocalSaveResult.InvalidSetup(
            listOf(com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssue(
                com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssueCode.INVALID_LOBBY_SLOT,
            )),
        )
        TestAuthRepository.state = AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test"))
        val invalidViewModel = viewModel()
        invalidViewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        invalidViewModel.saveTeamNames()
        advanceUntilIdle()
        assertTrue(invalidViewModel.uiState.value.validationIssues.isNotEmpty())
    }

    private fun viewModel(): GroupRotationTeamEntryViewModel = GroupRotationTeamEntryViewModel(
        readGroupRotationTeamSetup = ReadGroupRotationTeamSetupUseCase(readRepository, TestAuthRepository),
        saveGroupRotationTeamSetup = SaveGroupRotationTeamSetupUseCase(saveRepository, TestAuthRepository),
        draftRepository = draftRepository,
    )

    private fun tournament(
        id: String = TOURNAMENT_ID,
        groupCount: Int = 3,
        pairings: List<String> = listOf("A:B", "A:C"),
    ): Tournament = Tournament(
        id = id,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        format = TournamentFormat.GROUP_ROTATION,
        groupCount = groupCount,
        selectedGroupPairings = pairings.map(GroupPairing::fromCanonicalKey),
    )

    private fun completeCandidate(prefix: String): GroupRotationTeamSetupCandidate =
        GroupRotationTeamSetupCandidate(
            tournamentId = TOURNAMENT_ID,
            entries = (1..12).map { slot -> entry("A:B", slot, "$prefix $slot") } +
                (1..12).map { slot ->
                    entry("A:C", slot, if (slot <= 6) "$prefix $slot" else "$prefix ${slot + 6}")
                },
        )

    private fun entry(pairingKey: String, lobbySlot: Int, teamName: String): GroupRotationPairingTeamEntry =
        GroupRotationPairingTeamEntry(GroupPairing.fromCanonicalKey(pairingKey), lobbySlot, teamName)

    private class FakeReadRepository : GroupRotationTeamSetupReadRepository {
        var result: GroupRotationTeamSetupReadResult = GroupRotationTeamSetupReadResult.TournamentNotFound

        override suspend fun readGroupRotationTeamSetup(
            tournamentId: String,
            ownerUserId: String,
        ): GroupRotationTeamSetupReadResult = result
    }

    private class FakeSaveRepository : GroupRotationTeamSetupLocalRepository {
        var savedCandidate: GroupRotationTeamSetupCandidate? = null
        var result: GroupRotationTeamSetupLocalSaveResult = GroupRotationTeamSetupLocalSaveResult.Saved
        var saveCalls = 0
        var blocked = false
        val saveStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun saveGroupRotationTeamSetup(
            candidate: GroupRotationTeamSetupCandidate,
            ownerUserId: String,
        ): GroupRotationTeamSetupLocalSaveResult {
            saveCalls += 1
            savedCandidate = candidate
            if (blocked) {
                saveStarted.complete(Unit)
                release.await()
            }
            return result
        }
    }

    private class FakeDraftRepository : GroupRotationTeamSetupDraftRepository {
        var raw: List<GroupRotationPairingTeamEntry> = emptyList()
        var cleared = false
        var replaceCalls = 0
        var blockedTournamentId: String? = null
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        val persistedByTournament = mutableMapOf<String, List<GroupRotationPairingTeamEntry>>()

        override fun observeDraft(tournamentId: String): Flow<List<GroupRotationPairingTeamEntry>> = flowOf(raw)

        override suspend fun readDraft(tournamentId: String): List<GroupRotationPairingTeamEntry> =
            persistedByTournament[tournamentId] ?: raw

        override suspend fun replaceDraft(
            tournament: Tournament,
            candidate: GroupRotationTeamSetupCandidate,
        ): GroupRotationTeamSetupDraftSaveResult {
            replaceCalls += 1
            if (tournament.id == blockedTournamentId) {
                writeStarted.complete(Unit)
                releaseWrite.await()
            }
            persistedByTournament[tournament.id] = candidate.entries
            if (tournament.id == TOURNAMENT_ID) raw = candidate.entries
            return GroupRotationTeamSetupDraftSaveResult.Saved
        }

        override suspend fun clearDraft(tournamentId: String) {
            raw = emptyList()
            cleared = true
        }
    }

    private object TestAuthRepository : AuthRepository {
        var state: AuthState = AuthState.SignedIn(AuthUser(OWNER_ID, "owner@example.test"))

        override fun observeAuthState(): Flow<AuthState> = flowOf(state)

        override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String): AuthOperationResult = error("unused")
        override suspend fun login(email: String, password: String): AuthOperationResult = error("unused")
        override suspend fun logout(): AuthOperationResult = error("unused")
    }

    private companion object {
        const val TOURNAMENT_ID = "group-rotation-entry"
        const val OWNER_ID = "owner"
    }
}
