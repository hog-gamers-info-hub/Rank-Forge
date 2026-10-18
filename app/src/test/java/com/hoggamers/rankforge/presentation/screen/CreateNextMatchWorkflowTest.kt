package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchUseCase
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot
import com.hoggamers.rankforge.domain.tournament.MatchTeamIdentityContextRepository
import com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult
import com.hoggamers.rankforge.domain.tournament.ReadMatchTeamIdentityContextUseCase
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.defaultTeamNameForSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateNextMatchWorkflowTest {
    @Test
    fun groupRotationDefaultsValidateOnlyAndNeverRewriteConfirmedNames() = runTest {
        val repository = RecordingTournamentRepository()
        val auth = SignedInTournamentTestAuthRepository()
        val tournamentId = "rotation-defaults"
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        val canonicalSlots = listOf(14, 3, 18, 1, 16, 5, 6, 15, 2, 17, 4, 13)
        repository.create(
            Tournament(
                id = tournamentId,
                name = "Rotation Cup",
                stageName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT,
                format = TournamentFormat.GROUP_ROTATION,
                groupCount = 3,
                selectedGroupPairings = listOf(pairing),
                ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
            ),
        )
        val names = canonicalSlots.associateWith { slot -> "Team $slot" }
        repository.saveTeamNames(tournamentId, names)
        repository.replaceGroupRotationPairingLobbySlots(
            canonicalSlots.mapIndexed { index, teamSlotNumber ->
                GroupRotationPairingLobbySlot(tournamentId, pairing, index + 1, teamSlotNumber)
            },
        )
        assertTrue(repository.confirmTournament(tournamentId))
        val slotsBefore = repository.observeSlotsByTournamentId(tournamentId).first()
        repository.resetSaveTeamNamesInvocationCount()

        val upload = RecordingTournamentCloudUploadAction()
        val workflow = workflow(repository, auth, uploadTournament = upload)

        assertTrue(workflow.applyDefaults(tournamentId, pairing))
        assertEquals(0, repository.saveTeamNamesInvocationCount)
        assertEquals(0, upload.calls)
        assertEquals(slotsBefore, repository.observeSlotsByTournamentId(tournamentId).first())
        assertEquals(
            TournamentStatus.CONFIRMED,
            repository.observeById(tournamentId).first()!!.status,
        )
    }

    @Test
    fun standardDefaultsPreserveDefaultNameBehaviorAndSaveNames() = runTest {
        val repository = RecordingTournamentRepository()
        val auth = SignedInTournamentTestAuthRepository()
        val tournamentId = "standard-defaults"
        repository.create(
            Tournament(
                id = tournamentId,
                name = "Standard Cup",
                stageName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT,
                format = TournamentFormat.STANDARD,
                ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
            ),
        )

        val events = mutableListOf<String>()
        repository.onSaveTeamNames = { events += "save" }
        val upload = RecordingTournamentCloudUploadAction(onInvoke = { events += "upload" })
        val workflow = workflow(repository, auth, uploadTournament = upload)

        assertTrue(workflow.applyDefaults(tournamentId))

        assertEquals(1, repository.saveTeamNamesInvocationCount)
        assertEquals(1, upload.calls)
        assertEquals(listOf("save", "upload"), events)
        assertTrue(workflow.applyDefaults(tournamentId))
        assertEquals(2, repository.saveTeamNamesInvocationCount)
        assertEquals(2, upload.calls)
        assertEquals(listOf("save", "upload", "save", "upload"), events)
        assertEquals(
            (1..12).associateWith(::defaultTeamNameForSlot),
            repository.observeSlotsByTournamentId(tournamentId).first()
                .associate { slot -> slot.slotNumber to slot.teamName },
        )
    }

    @Test
    fun defaultNamesRemainLocalWhenTournamentUploadFailsBeforeDraftSync() = runTest {
        val repository = RecordingTournamentRepository()
        val auth = SignedInTournamentTestAuthRepository()
        val tournamentId = "standard-defaults-offline"
        repository.create(
            Tournament(
                id = tournamentId,
                name = "Standard Cup",
                stageName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT,
                format = TournamentFormat.STANDARD,
                ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
            ),
        )
        val events = mutableListOf<String>()
        repository.onSaveTeamNames = { events += "save" }
        val upload = RecordingTournamentCloudUploadAction(
            result = QueueAwareActionResult(
                primaryResult = TournamentCloudUploadResult.NetworkFailure,
                queueRecordingResult = QueueRecordingResult.RECORDED,
            ),
            onInvoke = { events += "upload" },
        )
        val workflow = workflow(
            repository = repository,
            auth = auth,
            uploadTournament = upload,
            syncDraftMatches = DraftMatchCloudSyncAction {
                events += "sync"
                QueueAwareActionResult(
                    primaryResult = DraftMatchCloudSyncResult.Success,
                    queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
                )
            },
        )

        assertTrue(workflow.applyDefaults(tournamentId))
        val result = workflow.create(tournamentId)

        assertTrue(result is CreateNextMatchResult.Created)
        assertEquals(listOf("save", "upload", "sync"), events)
        assertEquals(
            (1..12).associateWith(::defaultTeamNameForSlot),
            repository.observeSlotsByTournamentId(tournamentId).first()
                .associate { slot -> slot.slotNumber to slot.teamName },
        )
    }

    @Test
    fun successfulTemplateInheritanceDoesNotInvokeLobbyUploadCheckpoint() = runTest {
        val repository = RecordingTournamentRepository()
        val auth = SignedInTournamentTestAuthRepository()
        val tournamentId = "next-match-template"
        repository.create(
            Tournament(
                id = tournamentId,
                name = "Template Cup",
                stageName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT,
                format = TournamentFormat.STANDARD,
                ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
            ),
        )
        repository.saveTeamNames(tournamentId, mapOf(1 to "Team 1"))

        val appliedMatches = mutableListOf<Pair<String, String>>()
        var draftSyncCalls = 0
        var lobbyUploadCheckpointCalls = 0
        val workflow = workflow(
            repository = repository,
            auth = auth,
            syncDraftMatches = DraftMatchCloudSyncAction {
                draftSyncCalls += 1
                QueueAwareActionResult(
                    primaryResult = DraftMatchCloudSyncResult.Success,
                    queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
                )
            },
            applyLobbyTemplate = ApplyLobbyTemplateAction { appliedTournamentId, matchId ->
                appliedMatches += appliedTournamentId to matchId
                ApplyLobbyTemplateResult.Applied
            },
            lobbyUploadCheckpoint = MatchLobbyScreenshotUploadCheckpointAction {
                lobbyUploadCheckpointCalls += 1
                MatchLobbyScreenshotUploadCheckpointResult.Skipped
            },
        )

        val result = workflow.create(tournamentId)

        assertTrue(result is CreateNextMatchResult.Created)
        val createdMatch = (result as CreateNextMatchResult.Created).match
        assertEquals(listOf(tournamentId to createdMatch.id), appliedMatches)
        assertEquals(1, draftSyncCalls)
        assertEquals(0, lobbyUploadCheckpointCalls)
    }

    private fun workflow(
        repository: RecordingTournamentRepository,
        auth: SignedInTournamentTestAuthRepository,
        syncDraftMatches: DraftMatchCloudSyncAction = DraftMatchCloudSyncAction {
            QueueAwareActionResult(
                primaryResult = DraftMatchCloudSyncResult.Success,
                queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
            )
        },
        uploadTournament: TournamentCloudUploadAction = RecordingTournamentCloudUploadAction(),
        applyLobbyTemplate: ApplyLobbyTemplateAction = ApplyLobbyTemplateAction { _, _ ->
            ApplyLobbyTemplateResult.Unavailable
        },
        lobbyUploadCheckpoint: MatchLobbyScreenshotUploadCheckpointAction =
            MatchLobbyScreenshotUploadCheckpointAction {
                MatchLobbyScreenshotUploadCheckpointResult.Skipped
            },
    ): CreateNextMatchWorkflow = CreateNextMatchWorkflow(
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository, auth),
        validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
        createNextMatch = CreateNextMatchUseCase(repository, auth),
        syncDraftMatches = syncDraftMatches,
        uploadTournament = uploadTournament,
        applyLobbyTemplate = applyLobbyTemplate,
        lobbyUploadCheckpoint = lobbyUploadCheckpoint,
        readMatchTeamIdentityContext = ReadMatchTeamIdentityContextUseCase(repository, auth),
    )

    private class RecordingTournamentRepository(
        private val delegate: InMemoryTournamentRepository = InMemoryTournamentRepository(),
    ) : TournamentRepository by delegate, MatchTeamIdentityContextRepository by delegate {
        var saveTeamNamesInvocationCount: Int = 0
            private set
        var onSaveTeamNames: (() -> Unit)? = null

        override suspend fun saveTeamNames(
            tournamentId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ) {
            saveTeamNamesInvocationCount += 1
            onSaveTeamNames?.invoke()
            delegate.saveTeamNames(tournamentId, teamNamesBySlotNumber)
        }

        override suspend fun saveTeamNamesByOwner(
            tournamentId: String,
            ownerUserId: String,
            teamNamesBySlotNumber: Map<Int, String>,
        ): OwnerScopedTournamentMutationResult {
            saveTeamNamesInvocationCount += 1
            onSaveTeamNames?.invoke()
            return delegate.saveTeamNamesByOwner(
                tournamentId,
                ownerUserId,
                teamNamesBySlotNumber,
            )
        }

        suspend fun replaceGroupRotationPairingLobbySlots(
            assignments: List<GroupRotationPairingLobbySlot>,
        ) {
            delegate.replaceGroupRotationPairingLobbySlots(assignments)
        }

        fun resetSaveTeamNamesInvocationCount() {
            saveTeamNamesInvocationCount = 0
        }
    }

    private class RecordingTournamentCloudUploadAction(
        private val result: QueueAwareActionResult<TournamentCloudUploadResult> = QueueAwareActionResult(
            primaryResult = TournamentCloudUploadResult.Success(1),
            queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        ),
        private val onInvoke: () -> Unit = {},
    ) : TournamentCloudUploadAction {
        var calls: Int = 0
            private set

        override suspend fun invoke(tournamentId: String): QueueAwareActionResult<TournamentCloudUploadResult> {
            calls += 1
            onInvoke()
            return result
        }
    }
}
