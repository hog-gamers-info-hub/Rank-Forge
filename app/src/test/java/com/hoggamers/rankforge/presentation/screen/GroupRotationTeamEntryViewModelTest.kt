package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamEntry
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityWriteResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupCandidate
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupDraftSaveResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupLocalSaveResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupReadRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupReadResult
import com.hoggamers.rankforge.domain.tournament.ReadGroupRotationTeamSetupUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamSetupUseCase
import com.hoggamers.rankforge.domain.tournament.SaveGroupRotationTeamIdentitySetupUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
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
import java.util.UUID
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
    private lateinit var identityRepository: FakeIdentityRepository
    private lateinit var draftRepository: FakeDraftRepository
    private lateinit var uploadAction: RecordingUploadAction

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        readRepository = FakeReadRepository()
        saveRepository = FakeSaveRepository()
        identityRepository = FakeIdentityRepository()
        draftRepository = FakeDraftRepository()
        uploadAction = RecordingUploadAction()
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
        assertEquals(
            24,
            viewModel.uiState.value.pairingSections.sumOf { it.rows.size },
        )
        assertTrue(
            viewModel.uiState.value.pairingSections
                .flatMap { it.rows }
                .all { it.teamName.isEmpty() },
        )
        assertEquals(18, viewModel.uiState.value.maximumUniqueTeams)
    }

    @Test
    fun groupRotationUuidSaveTriggersV4UploadAfterLocalSave() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val teamUuid = "00000000-0000-0000-0000-000000000001"
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Saved(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(
                    GroupRotationTeamIdentity(
                        tournamentId = TOURNAMENT_ID,
                        teamUUID = teamUuid,
                        displayName = "Phoenix",
                        normalizedName = "phoenix",
                    ),
                ),
                assignments = listOf(
                    GroupRotationPairingAssignment(
                        tournamentId = TOURNAMENT_ID,
                        pairingKey = GroupPairing.fromCanonicalKey("A:B"),
                        lobbySlotNumber = 1,
                        teamUUID = teamUuid,
                    ),
                ),
            ),
        )
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.onTeamNameChanged(GroupPairing.fromCanonicalKey("A:B"), 1, "Phoenix")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(1, identityRepository.saveCalls)
        assertEquals(0, saveRepository.saveCalls)
        assertEquals(1, uploadAction.calls)
        assertEquals("Phoenix", viewModel.uiState.value.pairingSections.first().rows.first().teamName)
    }

    @Test
    fun groupRotationUuidSaveIncludesEditedPairingsAfterSwitchingTabsAndUploadsOnce() = runTest {
        val pairingAb = GroupPairing.fromCanonicalKey("A:B")
        val pairingAc = GroupPairing.fromCanonicalKey("A:C")
        val alphaUuid = "00000000-0000-0000-0000-000000000011"
        val betaUuid = "00000000-0000-0000-0000-000000000012"
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Saved(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(
                    GroupRotationTeamIdentity(TOURNAMENT_ID, alphaUuid, "Alpha", "alpha"),
                    GroupRotationTeamIdentity(TOURNAMENT_ID, betaUuid, "Beta", "beta"),
                ),
                assignments = listOf(
                    GroupRotationPairingAssignment(TOURNAMENT_ID, pairingAb, 1, alphaUuid),
                    GroupRotationPairingAssignment(TOURNAMENT_ID, pairingAc, 1, betaUuid),
                ),
            ),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.onTeamNameChanged(pairingAb, 1, "Alpha")
        viewModel.onPairingSelected(pairingAc)
        viewModel.onTeamNameChanged(pairingAc, 1, "Beta")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(2, identityRepository.saveCalls)
        assertEquals(listOf("Alpha"), identityRepository.savedEntriesByPairing["A:B"]?.map { it.displayName })
        assertEquals(listOf("Beta"), identityRepository.savedEntriesByPairing["A:C"]?.map { it.displayName })
        assertEquals(1, uploadAction.calls)
        assertEquals(
            "Alpha",
            viewModel.uiState.value.pairingSections.first { it.pairing == pairingAb }.rows.first().teamName,
        )
        assertEquals(
            "Beta",
            viewModel.uiState.value.pairingSections.first { it.pairing == pairingAc }.rows.first().teamName,
        )
    }

    @Test
    fun groupRotationUuidSaveIncludesEditedPairingsForFourGroups() = runTest {
        val pairingAb = GroupPairing.fromCanonicalKey("A:B")
        val pairingAc = GroupPairing.fromCanonicalKey("A:C")
        val pairingAd = GroupPairing.fromCanonicalKey("A:D")
        val identities = (1..3).map { number ->
            GroupRotationTeamIdentity(
                tournamentId = TOURNAMENT_ID,
                teamUUID = "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}",
                displayName = "Team $number",
                normalizedName = "team $number",
            )
        }
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Saved(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = identities,
                assignments = listOf(pairingAb, pairingAc, pairingAd).mapIndexed { index, pairing ->
                    GroupRotationPairingAssignment(
                        tournamentId = TOURNAMENT_ID,
                        pairingKey = pairing,
                        lobbySlotNumber = 1,
                        teamUUID = identities[index].teamUUID,
                    )
                },
            ),
        )
        val fourGroupTournament = tournament(
            groupCount = 4,
            pairings = listOf("A:B", "A:C", "A:D"),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(fourGroupTournament)
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.onTeamNameChanged(pairingAb, 1, "Team 1")
        viewModel.onTeamNameChanged(pairingAc, 1, "Team 2")
        viewModel.onTeamNameChanged(pairingAd, 1, "Team 3")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(3, identityRepository.saveCalls)
        assertEquals(1, uploadAction.calls)
        assertEquals(3, viewModel.uiState.value.pairingSections.size)
    }

    @Test
    fun groupRotationUuidSaveFailureLeavesAllPairingDraftsAndSkipsUpload() = runTest {
        val pairingAb = GroupPairing.fromCanonicalKey("A:B")
        val pairingAc = GroupPairing.fromCanonicalKey("A:C")
        identityRepository.saveResultsByPairing[pairingAc.canonicalKey] = GroupRotationTeamIdentityWriteResult.Invalid(
            listOf(
                com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssue(
                    code = com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssueCode.DUPLICATE_NORMALIZED_NAME,
                    pairingKey = pairingAc.canonicalKey,
                ),
            ),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.onTeamNameChanged(pairingAb, 1, "Alpha")
        viewModel.onPairingSelected(pairingAc)
        viewModel.onTeamNameChanged(pairingAc, 1, "Alpha")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(2, identityRepository.saveCalls)
        assertEquals(0, uploadAction.calls)
        assertTrue(viewModel.uiState.value.validationIssues.isNotEmpty())
        assertTrue(draftRepository.raw.any { it.pairing == pairingAb && it.teamName == "Alpha" })
        assertTrue(draftRepository.raw.any { it.pairing == pairingAc && it.teamName == "Alpha" })
        assertTrue(draftRepository.clearedPairings.isEmpty())
    }

    @Test
    fun groupRotationUuidSaveRemainsLocalWhenCloudSyncIsQueuedForRetry() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Saved(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(
                    GroupRotationTeamIdentity(
                        tournamentId = TOURNAMENT_ID,
                        teamUUID = "00000000-0000-0000-0000-000000000004",
                        displayName = "Phoenix",
                        normalizedName = "phoenix",
                    ),
                ),
                assignments = listOf(
                    GroupRotationPairingAssignment(
                        tournamentId = TOURNAMENT_ID,
                        pairingKey = GroupPairing.fromCanonicalKey("A:B"),
                        lobbySlotNumber = 1,
                        teamUUID = "00000000-0000-0000-0000-000000000004",
                    ),
                ),
            ),
        )
        uploadAction.result = QueueAwareActionResult(
            primaryResult = TournamentCloudUploadResult.NetworkFailure,
            queueRecordingResult = QueueRecordingResult.RECORDED,
        )
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.onTeamNameChanged(GroupPairing.fromCanonicalKey("A:B"), 1, "Phoenix")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(1, identityRepository.saveCalls)
        assertEquals(1, uploadAction.calls)
        assertEquals(null, viewModel.uiState.value.saveError)
        assertEquals(null, viewModel.uiState.value.cloudSyncError)
    }

    @Test
    fun stalePairingDraftIsPreservedForConflictReview() = runTest {
        val teamUuid = "00000000-0000-0000-0000-000000000002"
        identityRepository.readResult = GroupRotationTeamIdentityReadResult.Loaded(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(
                    GroupRotationTeamIdentity(TOURNAMENT_ID, teamUuid, "Phoenix Renamed", "phoenix renamed"),
                ),
                assignments = listOf(
                    GroupRotationPairingAssignment(
                        TOURNAMENT_ID,
                        GroupPairing.fromCanonicalKey("A:B"),
                        1,
                        teamUuid,
                    ),
                ),
            ),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = (1..12).map { lobbySlot ->
            entry("A:B", lobbySlot, if (lobbySlot == 1) "Phoenix" else "")
        }
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals(
            "Phoenix",
            viewModel.uiState.value.pairingSections.first().rows.first().teamName,
        )
    }

    @Test
    fun staleRenameDraftPreservesTheAuthoritativeUuid() = runTest {
        val teamUuid = "00000000-0000-0000-0000-000000000003"
        identityRepository.readResult = GroupRotationTeamIdentityReadResult.Loaded(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(GroupRotationTeamIdentity(TOURNAMENT_ID, teamUuid, "Phoenix Renamed", "phoenix renamed")),
                assignments = listOf(
                    GroupRotationPairingAssignment(
                        TOURNAMENT_ID,
                        GroupPairing.fromCanonicalKey("A:B"),
                        1,
                        teamUuid,
                    ),
                ),
            ),
        )
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Saved(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(GroupRotationTeamIdentity(TOURNAMENT_ID, teamUuid, "Phoenix", "phoenix")),
                assignments = listOf(GroupRotationPairingAssignment(TOURNAMENT_ID, GroupPairing.fromCanonicalKey("A:B"), 1, teamUuid)),
            ),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = (1..12).map { lobbySlot ->
            entry("A:B", lobbySlot, if (lobbySlot == 1) "Phoenix" else "")
        }
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(1, identityRepository.saveCalls)
        assertTrue(viewModel.uiState.value.validationIssues.isEmpty())
        assertEquals("Phoenix", viewModel.uiState.value.pairingSections.first().rows.first().teamName)
    }

    @Test
    fun explicitClearingInSavedDraftIsSubmittedAfterReload() = runTest {
        val teamUuid = "00000000-0000-0000-0000-000000000007"
        val pairing = GroupPairing.fromCanonicalKey("A:B")
        identityRepository.readResult = GroupRotationTeamIdentityReadResult.Loaded(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(GroupRotationTeamIdentity(TOURNAMENT_ID, teamUuid, "Alpha", "alpha")),
                assignments = listOf(GroupRotationPairingAssignment(TOURNAMENT_ID, pairing, 1, teamUuid)),
            ),
        )
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Saved(
            GroupRotationTeamIdentityLocalSnapshot(
                teams = listOf(GroupRotationTeamIdentity(TOURNAMENT_ID, teamUuid, "Alpha", "alpha")),
                assignments = listOf(GroupRotationPairingAssignment(TOURNAMENT_ID, pairing, 1, teamUuid, nameCleared = true)),
            ),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = (1..12).map { lobbySlot -> entry("A:B", lobbySlot, "") }

        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(1, identityRepository.saveCalls)
        assertEquals("", identityRepository.lastEntries.single { it.lobbySlotNumber == 1 }.displayName)
    }

    @Test
    fun clearingOneExistingAssignmentIsAllowedAndPreservesOtherPairingAssignments() = runTest {
        val alpha = "00000000-0000-0000-0000-000000000005"
        val beta = "00000000-0000-0000-0000-000000000006"
        val pairingAb = GroupPairing.fromCanonicalKey("A:B")
        val pairingAc = GroupPairing.fromCanonicalKey("A:C")
        val existing = GroupRotationTeamIdentityLocalSnapshot(
            teams = listOf(
                GroupRotationTeamIdentity(TOURNAMENT_ID, alpha, "Alpha", "alpha"),
                GroupRotationTeamIdentity(TOURNAMENT_ID, beta, "Beta", "beta"),
            ),
            assignments = listOf(
                GroupRotationPairingAssignment(TOURNAMENT_ID, pairingAb, 1, alpha),
                GroupRotationPairingAssignment(TOURNAMENT_ID, pairingAc, 1, beta),
            ),
        )
        identityRepository.readResult = GroupRotationTeamIdentityReadResult.Loaded(existing)
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Saved(
            existing.copy(
                assignments = listOf(
                    existing.assignments.first(),
                    existing.assignments[1].copy(nameCleared = true),
                ),
            ),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.onPairingSelected(pairingAc)
        viewModel.onTeamNameChanged(pairingAc, 1, "")
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.validationIssues.isEmpty())
        assertEquals(1, identityRepository.saveCalls)
        assertEquals(1, uploadAction.calls)
        assertEquals(
            listOf(existing.assignments.first(), existing.assignments[1].copy(nameCleared = true)),
            (identityRepository.saveResult as GroupRotationTeamIdentityWriteResult.Saved).snapshot.assignments,
        )
    }

    @Test
    fun clearingAllExistingAssignmentsInPairingIsAllowedAndLeavesOtherPairingIntact() = runTest {
        val teamUuids = (1..6).map { number ->
            "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
        }
        val pairingAb = GroupPairing.fromCanonicalKey("A:B")
        val pairingAc = GroupPairing.fromCanonicalKey("A:C")
        val existing = GroupRotationTeamIdentityLocalSnapshot(
            teams = teamUuids.mapIndexed { index, teamUUID ->
                GroupRotationTeamIdentity(TOURNAMENT_ID, teamUUID, "Team ${index + 1}", "team ${index + 1}")
            },
            assignments = teamUuids.flatMapIndexed { index, teamUUID ->
                listOf(
                    GroupRotationPairingAssignment(TOURNAMENT_ID, pairingAb, index + 1, teamUUID),
                    GroupRotationPairingAssignment(TOURNAMENT_ID, pairingAc, index + 1, teamUUID),
                )
            },
        )
        identityRepository.readResult = GroupRotationTeamIdentityReadResult.Loaded(existing)
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Saved(
            existing.copy(
                assignments = existing.assignments.map { assignment ->
                    if (assignment.pairingKey == pairingAc) assignment.copy(nameCleared = true) else assignment
                },
            ),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.onPairingSelected(pairingAc)
        (1..6).forEach { lobbySlot ->
            viewModel.onTeamNameChanged(pairingAc, lobbySlot, "")
        }
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.validationIssues.isEmpty())
        assertEquals(1, identityRepository.saveCalls)
        assertEquals(1, uploadAction.calls)
        val saved = (identityRepository.saveResult as GroupRotationTeamIdentityWriteResult.Saved).snapshot
        assertEquals(12, saved.assignments.size)
        assertTrue(saved.assignments.filter { it.pairingKey == pairingAb }.all { !it.nameCleared })
        assertTrue(saved.assignments.filter { it.pairingKey == pairingAc }.all { it.nameCleared })
    }

    @Test
    fun legitimateDraftForUnassignedSlotSurvivesRestartAndCanBeSaved() = runTest {
        identityRepository.readResult = GroupRotationTeamIdentityReadResult.Loaded(
            GroupRotationTeamIdentityLocalSnapshot(emptyList(), emptyList()),
        )
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = pairingCandidate("A:B", "Draft").entries
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals("Draft 1", viewModel.uiState.value.pairingSections.first().rows.first().teamName)
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(1, identityRepository.saveCalls)
        assertEquals(null, saveRepository.savedCandidate)
    }

    @Test
    fun uuidSaveRejectsTournamentOwnedByAnotherSignedInAccount() = runTest {
        val ownedByOther = tournament(ownerUserId = OTHER_OWNER_ID)
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(ownedByOther)
        draftRepository.raw = pairingCandidate("A:B", "Team").entries
        val viewModel = viewModel()
        viewModel.load(ownedByOther.id)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(GroupRotationTeamEntrySaveError.Unexpected, viewModel.uiState.value.saveError)
        assertEquals(0, identityRepository.saveCalls)
        assertEquals(0, saveRepository.saveCalls)
        assertEquals(0, uploadAction.calls)
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
    fun partialDraftOverlaysOnlyItsPairingAndBlankOverridesSavedValue() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.Loaded(
            com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupRead(
                tournament(),
                completeCandidate("Saved"),
            ),
        )
        draftRepository.raw = pairingCandidate("A:C", "Draft", blankSlots = setOf(1)).entries
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        assertEquals("Saved 1", viewModel.uiState.value.pairingSections[0].rows[0].teamName)
        assertEquals("", viewModel.uiState.value.pairingSections[1].rows[0].teamName)
        assertEquals("Draft 2", viewModel.uiState.value.pairingSections[1].rows[1].teamName)
    }

    @Test
    fun savingOnePairingLeavesOtherPairingsBlankAndSubmitsOnlyTwelveRows() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = pairingCandidate("A:B", "Team").entries
        val viewModel = viewModel()

        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(1, identityRepository.saveCalls)
        assertEquals(null, saveRepository.savedCandidate)
        assertTrue(viewModel.uiState.value.pairingSections[1].rows.all { it.teamName.isEmpty() })
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
    fun editingOneLocalRowWritesOnlyTheEditedPairingDraft() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        viewModel.onTeamNameChanged(GroupPairing.fromCanonicalKey("A:B"), 1, "Alpha")
        advanceUntilIdle()

        assertEquals(12, draftRepository.raw.size)
        assertEquals("Alpha", draftRepository.raw.single { it.pairing.canonicalKey == "A:B" && it.lobbySlotNumber == 1 }.teamName)
        assertTrue(draftRepository.raw.none { it.pairing.canonicalKey == "A:C" })
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
    fun emptyUnassignedPairingSavesWithoutCreatingIdentities() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.validationIssues.isEmpty())
        assertEquals(1, identityRepository.saveCalls)
        assertTrue(saveRepository.savedCandidate == null)
        assertEquals(1, uploadAction.calls)
    }

    @Test
    fun validSetupSavesLocallyClearsOnlyTheSavedPairingDraftAndNavigates() = runTest {
        val complete = completeCandidate("Team")
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = complete.entries
        uploadAction.onInvoke = { draftRepository.events += "cloud-upload" }
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        val navigation = async { viewModel.navigationEvents.first() }

        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(2, identityRepository.saveCalls)
        assertEquals(null, saveRepository.savedCandidate)
        assertEquals(listOf("A:B", "A:C"), draftRepository.clearedPairings)
        assertTrue(draftRepository.raw.isEmpty())
        assertEquals(
            GroupRotationTeamEntryNavigationEvent.BackToTournamentDetails,
            navigation.await(),
        )
        assertEquals(1, uploadAction.calls)
        assertEquals(
            listOf("draft-cleared:A:B", "draft-cleared:A:C", "cloud-upload"),
            draftRepository.events,
        )
    }

    @Test
    fun localSaveWithQueuedNetworkFailureNavigatesWithoutLocalSaveError() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = completeCandidate("Team").entries
        uploadAction.result = QueueAwareActionResult(
            primaryResult = TournamentCloudUploadResult.NetworkFailure,
            queueRecordingResult = QueueRecordingResult.RECORDED,
        )
        uploadAction.onInvoke = { draftRepository.events += "cloud-upload" }
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        val navigation = async { viewModel.navigationEvents.first() }

        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(GroupRotationTeamEntryNavigationEvent.BackToTournamentDetails, navigation.await())
        assertEquals(null, viewModel.uiState.value.saveError)
        assertEquals(null, viewModel.uiState.value.cloudSyncError)
        assertEquals(listOf("A:B", "A:C"), draftRepository.clearedPairings)
        assertEquals(
            listOf("draft-cleared:A:B", "draft-cleared:A:C", "cloud-upload"),
            draftRepository.events,
        )
    }

    @Test
    fun localSaveWithQueuePersistenceFailureStaysOnScreenWithCloudError() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = completeCandidate("Team").entries
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Unsupported
        uploadAction.result = QueueAwareActionResult(
            primaryResult = TournamentCloudUploadResult.NetworkFailure,
            queueRecordingResult = QueueRecordingResult.PERSISTENCE_FAILED,
        )
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        val navigation = async { viewModel.navigationEvents.first() }

        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(GroupRotationTeamEntrySaveError.Unexpected, viewModel.uiState.value.saveError)
        assertEquals(null, viewModel.uiState.value.cloudSyncError)
        assertEquals(0, saveRepository.saveCalls)
        assertEquals(0, uploadAction.calls)
        assertFalse(draftRepository.cleared)
        assertFalse(navigation.isCompleted)
        navigation.cancel()
    }

    @Test
    fun localSaveWithNonNetworkCloudFailureStaysOnScreenAsCloudFailure() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = completeCandidate("Team").entries
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Unsupported
        uploadAction.result = QueueAwareActionResult(
            primaryResult = TournamentCloudUploadResult.ValidationFailure,
            queueRecordingResult = QueueRecordingResult.RECORDED,
        )
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        val navigation = async { viewModel.navigationEvents.first() }

        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertEquals(GroupRotationTeamEntrySaveError.Unexpected, viewModel.uiState.value.saveError)
        assertEquals(null, viewModel.uiState.value.cloudSyncError)
        assertEquals(0, saveRepository.saveCalls)
        assertEquals(0, uploadAction.calls)
        assertTrue(draftRepository.clearedPairings.isEmpty())
        assertTrue(draftRepository.raw.any { it.pairing.canonicalKey == "A:B" })
        assertTrue(draftRepository.raw.any { it.pairing.canonicalKey == "A:C" })
        assertTrue(viewModel.uiState.value.pairingSections[1].rows.first().teamName.isNotEmpty())
        assertFalse(navigation.isCompleted)
        navigation.cancel()
    }

    @Test
    fun staleCloudCompletionDoesNotNavigateOrOverwriteNewerDraft() = runTest {
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournament())
        draftRepository.raw = completeCandidate("Team").entries
        identityRepository.blocked = true
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()
        val navigation = async { viewModel.navigationEvents.first() }
        viewModel.saveTeamNames()
        runCurrent()
        identityRepository.saveStarted.await()

        viewModel.onTeamNameChanged(GroupPairing.fromCanonicalKey("A:B"), 1, "Newer")
        identityRepository.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("Newer", draftRepository.raw.first { it.pairing.canonicalKey == "A:B" && it.lobbySlotNumber == 1 }.teamName)
        assertFalse(draftRepository.cleared)
        assertFalse(navigation.isCompleted)
        assertFalse(viewModel.uiState.value.cloudSyncError != null)
        navigation.cancel()
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
        viewModel.onPairingSelected(GroupPairing.fromCanonicalKey("A:C"))
        viewModel.saveTeamNames()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.validationIssues.any {
            it.code == com.hoggamers.rankforge.domain.tournament.GroupRotationTeamSetupIssueCode.DUPLICATE_TEAM_IDENTITY_WITHIN_PAIRING
        })
        assertEquals("A:C", viewModel.uiState.value.selectedPairingKey)
        assertEquals(null, saveRepository.savedCandidate)
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
            "alpha team",
            viewModel.uiState.value.pairingSections.first().rows.first().teamName,
        )
        assertEquals(
            "alpha team",
            viewModel.uiState.value.pairingSections[1].rows.first().teamName,
        )
        assertEquals(listOf("A:B", "A:C"), draftRepository.clearedPairings)
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
        identityRepository.blocked = true
        val viewModel = viewModel()
        viewModel.load(tournamentA.id)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        runCurrent()
        assertTrue(identityRepository.saveStarted.isCompleted)

        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentB)
        draftRepository.raw = completeCandidate("B").entries
        viewModel.load(tournamentB.id)
        advanceUntilIdle()
        val navigation = async { viewModel.navigationEvents.first() }

        identityRepository.release.complete(Unit)
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
        identityRepository.blocked = true
        val viewModel = viewModel()
        viewModel.load(tournamentA.id)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        runCurrent()
        assertTrue(identityRepository.saveStarted.isCompleted)

        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentB)
        draftRepository.raw = completeCandidate("B").entries
        viewModel.load(tournamentB.id)
        advanceUntilIdle()
        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentA)
        draftRepository.raw = completeCandidate("A-new").entries
        viewModel.load(tournamentA.id)
        advanceUntilIdle()

        identityRepository.release.complete(Unit)
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
        identityRepository.blocked = true
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.ProtectedHistory
        val viewModel = viewModel()
        viewModel.load(tournamentA.id)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        runCurrent()
        assertTrue(identityRepository.saveStarted.isCompleted)

        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentB)
        draftRepository.raw = completeCandidate("B").entries
        viewModel.load(tournamentB.id)
        advanceUntilIdle()
        identityRepository.release.complete(Unit)
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
        identityRepository.blocked = true
        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Invalid(
            listOf(
                com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssue(
                    com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssueCode.INVALID_LOBBY_SLOT,
                ),
            ),
        )
        val viewModel = viewModel()
        viewModel.load(tournamentA.id)
        advanceUntilIdle()
        viewModel.saveTeamNames()
        runCurrent()
        assertTrue(identityRepository.saveStarted.isCompleted)

        readRepository.result = GroupRotationTeamSetupReadResult.NoSavedSetup(tournamentB)
        draftRepository.raw = completeCandidate("B").entries
        viewModel.load(tournamentB.id)
        advanceUntilIdle()
        identityRepository.release.complete(Unit)
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
        identityRepository.blocked = true
        val viewModel = viewModel()
        viewModel.load(TOURNAMENT_ID)
        advanceUntilIdle()

        viewModel.saveTeamNames()
        runCurrent()
        viewModel.saveTeamNames()
        assertEquals(1, identityRepository.saveCalls)

        val navigation = async { viewModel.navigationEvents.first() }
        viewModel.onTeamNameChanged(GroupPairing.fromCanonicalKey("A:B"), 1, "Newer")
        advanceUntilIdle()
        identityRepository.release.complete(Unit)
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
            GroupRotationTeamIdentityWriteResult.ProtectedHistory to GroupRotationTeamEntrySaveError.ProtectedHistory,
            GroupRotationTeamIdentityWriteResult.Unsupported to GroupRotationTeamEntrySaveError.Unexpected,
        )
        results.forEach { (identityResult, expectedError) ->
            identityRepository.saveResult = identityResult
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

        identityRepository.saveResult = GroupRotationTeamIdentityWriteResult.Invalid(
            listOf(com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssue(
                com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssueCode.INVALID_LOBBY_SLOT,
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
        readGroupRotationTeamSetup = ReadGroupRotationTeamSetupUseCase(
            readRepository,
            TestAuthRepository,
            identityRepository,
        ),
        saveGroupRotationTeamSetup = SaveGroupRotationTeamSetupUseCase(saveRepository, TestAuthRepository),
        saveGroupRotationTeamIdentitySetup = SaveGroupRotationTeamIdentitySetupUseCase(
            identityRepository,
            TestAuthRepository,
        ),
        draftRepository = draftRepository,
        uploadTournament = uploadAction,
    )

    private fun tournament(
        id: String = TOURNAMENT_ID,
        groupCount: Int = 3,
        pairings: List<String> = listOf("A:B", "A:C"),
        ownerUserId: String? = null,
    ): Tournament = Tournament(
        id = id,
        name = "Rotation",
        stageName = "Stage",
        organizerContactNumber = "123",
        status = TournamentStatus.DRAFT,
        ownerUserId = ownerUserId,
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

    private fun pairingCandidate(
        pairingKey: String,
        prefix: String,
        blankSlots: Set<Int> = emptySet(),
    ): GroupRotationTeamSetupCandidate = GroupRotationTeamSetupCandidate(
        tournamentId = TOURNAMENT_ID,
        entries = (1..12).map { slot ->
            entry(pairingKey, slot, if (slot in blankSlots) "" else "$prefix $slot")
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

    private class FakeIdentityRepository : GroupRotationTeamIdentityLocalRepository {
        var saveCalls = 0
        var lastEntries: List<com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamIdentityEntry> = emptyList()
        val savedEntriesByPairing = mutableMapOf<String, List<com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamIdentityEntry>>()
        var readResult: GroupRotationTeamIdentityReadResult = GroupRotationTeamIdentityReadResult.Unsupported
        var saveResult: GroupRotationTeamIdentityWriteResult? = null
        val saveResultsByPairing = mutableMapOf<String, GroupRotationTeamIdentityWriteResult>()
        private var defaultSnapshot: GroupRotationTeamIdentityLocalSnapshot? = null
        var blocked = false
        val saveStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun read(
            tournament: Tournament,
        ): GroupRotationTeamIdentityReadResult = readResult

        override suspend fun savePairing(
            tournament: Tournament,
            pairingKey: GroupPairing,
            entries: List<com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamIdentityEntry>,
        ): GroupRotationTeamIdentityWriteResult {
            saveCalls += 1
            lastEntries = entries
            savedEntriesByPairing[pairingKey.canonicalKey] = entries
            if (blocked) {
                saveStarted.complete(Unit)
                release.await()
            }
            return saveResultsByPairing[pairingKey.canonicalKey]
                ?: saveResult
                ?: defaultSavedResult(tournament, pairingKey, entries)
        }

        private fun defaultSavedResult(
            tournament: Tournament,
            pairingKey: GroupPairing,
            entries: List<com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamIdentityEntry>,
        ): GroupRotationTeamIdentityWriteResult {
            val normalizer = com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityNormalizer()
            val duplicate = entries
                .map { normalizer.normalize(it.displayName) }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .firstOrNull()
            if (duplicate != null) {
                return GroupRotationTeamIdentityWriteResult.Invalid(
                    listOf(
                        com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssue(
                            com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityIssueCode.DUPLICATE_NORMALIZED_NAME,
                            normalizedName = duplicate,
                            pairingKey = pairingKey.canonicalKey,
                        ),
                    ),
                )
            }
            val current = defaultSnapshot
                ?: (readResult as? GroupRotationTeamIdentityReadResult.Loaded)?.snapshot
                ?: GroupRotationTeamIdentityLocalSnapshot(emptyList(), emptyList())
            val teams = current.teams.toMutableList()
            val assignments = current.assignments.filterNot { it.pairingKey == pairingKey }.toMutableList()
            entries.filter { normalizer.normalize(it.displayName).isNotBlank() }.forEach { entry ->
                val cleanedName = normalizer.cleanDisplayName(entry.displayName)
                val normalized = normalizer.normalize(cleanedName)
                val savedAssignment = current.assignments.firstOrNull {
                    it.pairingKey == pairingKey && it.lobbySlotNumber == entry.lobbySlotNumber
                }
                val existing = savedAssignment?.let { assignment ->
                    teams.firstOrNull { it.teamUUID == assignment.teamUUID }
                } ?: teams.firstOrNull { it.normalizedName == normalized }
                val identity = existing?.copy(
                    displayName = cleanedName,
                    normalizedName = normalized,
                ) ?: GroupRotationTeamIdentity(
                    tournamentId = tournament.id,
                    teamUUID = UUID(0, (teams.size + 1).toLong()).toString(),
                    displayName = cleanedName,
                    normalizedName = normalized,
                )
                if (existing == null) teams += identity else teams.replaceAll {
                    if (it.teamUUID == identity.teamUUID) identity else it
                }
                assignments += GroupRotationPairingAssignment(
                    tournamentId = tournament.id,
                    pairingKey = pairingKey,
                    lobbySlotNumber = entry.lobbySlotNumber,
                    teamUUID = identity.teamUUID,
                )
            }
            val snapshot = GroupRotationTeamIdentityLocalSnapshot(teams, assignments)
            defaultSnapshot = snapshot
            return GroupRotationTeamIdentityWriteResult.Saved(snapshot)
        }

        override suspend fun renameTeam(
            tournament: Tournament,
            teamUUID: String,
            displayName: String,
        ): GroupRotationTeamIdentityWriteResult = GroupRotationTeamIdentityWriteResult.Unsupported
    }

    private class FakeDraftRepository : GroupRotationTeamSetupDraftRepository {
        var raw: List<GroupRotationPairingTeamEntry> = emptyList()
        var cleared = false
        val clearedPairings = mutableListOf<String>()
        val events = mutableListOf<String>()
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
            val submittedPairingKeys = candidate.entries.map { it.pairing.canonicalKey }.toSet()
            val existing = persistedByTournament[tournament.id] ?: raw
            val merged = existing.filterNot {
                it.pairing.canonicalKey in submittedPairingKeys
            } + candidate.entries
            persistedByTournament[tournament.id] = merged
            if (tournament.id == TOURNAMENT_ID) raw = merged
            return GroupRotationTeamSetupDraftSaveResult.Saved
        }

        override suspend fun clearDraft(tournamentId: String) {
            raw = emptyList()
            persistedByTournament[tournamentId] = emptyList()
            cleared = true
            events += "draft-cleared"
        }

        override suspend fun clearDraftForPairing(tournamentId: String, pairing: GroupPairing) {
            val existing = persistedByTournament[tournamentId] ?: raw
            val retained = existing.filterNot { it.pairing == pairing }
            persistedByTournament[tournamentId] = retained
            if (tournamentId == TOURNAMENT_ID) raw = retained
            clearedPairings += pairing.canonicalKey
            events += "draft-cleared:${pairing.canonicalKey}"
        }
    }

    private class RecordingUploadAction : TournamentCloudUploadAction {
        var result: QueueAwareActionResult<TournamentCloudUploadResult> = QueueAwareActionResult(
            primaryResult = TournamentCloudUploadResult.Success(1),
            queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        )
        var calls = 0
        var onInvoke: (() -> Unit)? = null
        var blocked = false
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun invoke(tournamentId: String): QueueAwareActionResult<TournamentCloudUploadResult> {
            calls += 1
            onInvoke?.invoke()
            if (blocked) {
                started.complete(Unit)
                release.await()
            }
            return result
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
        const val OTHER_OWNER_ID = "other-owner"
    }
}
