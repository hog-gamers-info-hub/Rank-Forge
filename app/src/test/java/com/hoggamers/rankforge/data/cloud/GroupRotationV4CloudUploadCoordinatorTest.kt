package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalCreateResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalEntriesResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalEntry
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalMutationResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalOperationType
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalPayload
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalRepository
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalRequest
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalStatus
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalValidationFailure
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityPersistenceRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentitySnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceIssue
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceIssueCode
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityWriteResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.NoOpDeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadLocalSnapshotRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.formatDerivedSlots
import com.hoggamers.rankforge.domain.tournament.SignedInTournamentTestAuthRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationV4CloudUploadCoordinatorTest {
    @Test
    fun uploadsParentIdentityDraftAndFinalizationWithChainedRevisions() = runBlocking {
        val fixture = Fixture()
        fixture.addDraftAndFinalizedMatches()

        val result = fixture.coordinator().upload(TOURNAMENT_ID)

        assertEquals(GroupRotationV4CloudUploadResult.Success(6), result)
        assertEquals(1, fixture.parentUpload.calls)
        assertEquals(listOf(MATCH_ONE, MATCH_TWO, MATCH_TWO), fixture.matchParentProvisioner.matchIds)
        assertEquals(fixture.matchParentProvisioner.provisioned, fixture.matchRemote.provisioned)
        assertEquals(
            listOf("draft:$MATCH_ONE", "draft:$MATCH_TWO", "final:$MATCH_TWO"),
            fixture.matchRemote.operations,
        )
        assertEquals(listOf(2), fixture.identityRemote.expectedRevisions)
        assertEquals(listOf(3, 4), fixture.matchRemote.draftExpectedRevisions)
        assertEquals(listOf(5), fixture.matchRemote.finalExpectedRevisions)
        assertEquals(
            listOf(
                GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT,
                GroupRotationV4SyncJournalOperationType.MATCH_DRAFT,
                GroupRotationV4SyncJournalOperationType.MATCH_DRAFT,
                GroupRotationV4SyncJournalOperationType.MATCH_FINALIZATION,
            ),
            fixture.journal.entries.map { it.operationType },
        )
        assertTrue(fixture.journal.entries.all { it.status == GroupRotationV4SyncJournalStatus.CONFIRMED })
    }

    @Test
    fun retryUsesTheOriginalFrozenPayloadAndRequestWithoutReuploadingParent() = runBlocking {
        val fixture = Fixture()
        fixture.identityRemote.results += GroupRotationTeamIdentityV4ExecutionResult.Failure(
            category = GroupRotationTeamIdentityV4FailureCategory.NETWORK,
        )

        val first = fixture.coordinator().upload(TOURNAMENT_ID)
        assertEquals(1, fixture.journal.entries.size)
        val frozenEntry = fixture.journal.entries.single()
        fixture.identitySnapshot = fixture.identitySnapshot.copy(
            teams = fixture.identitySnapshot.teams.map { team ->
                if (team.teamUUID == TEAM_A) team.copy(displayName = "Changed locally") else team
            },
        )
        val second = fixture.coordinator().upload(TOURNAMENT_ID)

        assertEquals(GroupRotationV4CloudUploadResult.NetworkFailure, first)
        assertEquals(GroupRotationV4CloudUploadResult.Success(3), second)
        assertEquals(1, fixture.parentUpload.calls)
        assertEquals(2, fixture.identityRemote.parameters.size)
        assertEquals(fixture.identityRemote.parameters[0], fixture.identityRemote.parameters[1])
        assertEquals(
            frozenEntry.requestId,
            fixture.identityRemote.parameters[1].requestId,
        )
        assertEquals(GroupRotationV4SyncJournalStatus.CONFIRMED, fixture.journal.entries.single().status)
    }

    @Test
    fun legacyNumericMatchesAreNotSentThroughV4MatchRpcs() = runBlocking {
        val fixture = Fixture()
        fixture.addLegacyNumericMatch()

        val result = fixture.coordinator().upload(TOURNAMENT_ID)

        assertEquals(GroupRotationV4CloudUploadResult.Success(3), result)
        assertEquals(emptyList<String>(), fixture.matchRemote.operations)
        assertEquals(1, fixture.identityRemote.parameters.size)
        assertTrue(fixture.matchParentProvisioner.matchIds.isEmpty())
    }

    @Test
    fun missingParentPreventsDependentV4MatchUploadAndRemainsRetryable() = runBlocking {
        val fixture = Fixture()
        fixture.matchParentProvisioner.result = GroupRotationCloudMatchProvisionResult.NetworkFailure
        fixture.addDraftAndFinalizedMatches()

        assertEquals(
            GroupRotationV4CloudUploadResult.NetworkFailure,
            fixture.coordinator().upload(TOURNAMENT_ID),
        )
        assertEquals(listOf(MATCH_ONE), fixture.matchParentProvisioner.matchIds)
        assertTrue(fixture.matchRemote.operations.isEmpty())
        assertEquals(1, fixture.journal.entries.count {
            it.operationType == GroupRotationV4SyncJournalOperationType.MATCH_DRAFT &&
                it.status == GroupRotationV4SyncJournalStatus.PENDING
        })
    }

    @Test
    fun standardTournamentDoesNotEnterUuidCoordinator() = runBlocking {
        val fixture = Fixture(standard = true)

        val result = fixture.coordinator().upload(TOURNAMENT_ID)

        assertEquals(GroupRotationV4CloudUploadResult.ValidationFailure, result)
        assertEquals(0, fixture.parentUpload.calls)
        assertTrue(fixture.operations.isEmpty())
    }

    @Test
    fun ownerMismatchAndDeletionIntentFailBeforeAnyCloudWrite() = runBlocking {
        val unauthorized = Fixture(tournamentOwner = "another-owner")
        assertEquals(
            GroupRotationV4CloudUploadResult.AuthorizationFailure,
            unauthorized.coordinator().upload(TOURNAMENT_ID),
        )

        val blocked = Fixture(deletionBlocked = true)
        assertEquals(
            GroupRotationV4CloudUploadResult.DeletionBlocked,
            blocked.coordinator().upload(TOURNAMENT_ID),
        )
        assertEquals(0, blocked.parentUpload.calls)
        assertTrue(blocked.operations.isEmpty())
    }

    private class Fixture(
        standard: Boolean = false,
        tournamentOwner: String = OWNER_ID,
        private val deletionBlocked: Boolean = false,
    ) {
        val repository = InMemoryTournamentRepository()
        val tournament = if (standard) {
            Tournament(
                id = TOURNAMENT_ID,
                name = "Standard",
                stageName = "Stage",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT,
                ownerUserId = tournamentOwner,
            )
        } else {
            Tournament(
                id = TOURNAMENT_ID,
                name = "Rotation",
                stageName = "Stage",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT,
                ownerUserId = tournamentOwner,
                format = TournamentFormat.GROUP_ROTATION,
                groupCount = 3,
                selectedGroupPairings = listOf(pairing("A:B"), pairing("A:C")),
            )
        }
        var identitySnapshot = GroupRotationTeamIdentityLocalSnapshot(
            teams = listOf(
                team(TEAM_A, "Alpha"),
                team(TEAM_B, "Beta"),
            ),
            assignments = listOf(
                assignment(pairing("A:B"), 1, TEAM_A),
                assignment(pairing("A:B"), 2, TEAM_B),
                assignment(pairing("A:C"), 7, TEAM_A),
                assignment(pairing("A:C"), 11, TEAM_B),
            ),
        )
        val identityRemote = RecordingIdentityRemote()
        val matchParentProvisioner = RecordingMatchParentProvisioner()
        val matchRemote = RecordingMatchRemote(matchParentProvisioner.provisioned)
        val journal = InMemoryJournal()
        val parentUpload = RecordingParentUpload()
        val operations = mutableListOf<String>()

        init {
            runBlocking {
                repository.create(tournament)
                if (!standard) {
                    repository.saveTeamNames(
                        TOURNAMENT_ID,
                        (1..12).associateWith { slotNumber -> "Legacy $slotNumber" },
                    )
                    repository.replaceGroupRotationPairingLobbySlots(
                        (1..12).map { lobbySlotNumber ->
                            com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot(
                                tournamentId = TOURNAMENT_ID,
                                pairing = pairing("A:B"),
                                lobbySlotNumber = lobbySlotNumber,
                                teamSlotNumber = lobbySlotNumber,
                            )
                        },
                    )
                    repository.replaceGroupRotationPairingLobbySlots(
                        (1..12).map { lobbySlotNumber ->
                            com.hoggamers.rankforge.domain.tournament.GroupRotationPairingLobbySlot(
                                tournamentId = TOURNAMENT_ID,
                                pairing = pairing("A:C"),
                                lobbySlotNumber = lobbySlotNumber,
                                teamSlotNumber = lobbySlotNumber,
                            )
                        },
                    )
                }
            }
        }

        fun coordinator(): GroupRotationV4CloudUploadCoordinator = GroupRotationV4CloudUploadCoordinator(
            authRepository = SignedInTournamentTestAuthRepository(OWNER_ID),
            tournamentRepository = repository,
            cloudUploadSnapshotRepository = object : TournamentCloudUploadLocalSnapshotRepository {
                override suspend fun readCloudUploadSnapshotByOwner(
                    tournamentId: String,
                    ownerUserId: String,
                ) = repository.observeByIdAndOwner(tournamentId, ownerUserId).first()?.let { value ->
                    com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot(
                        tournament = value,
                        slots = value.formatDerivedSlots(),
                        rosters = emptyMap(),
                        expectedCloudRevision = 1,
                    )
                }
            },
            tournamentCloudUploadRepository = parentUpload,
            teamIdentityRepository = object : GroupRotationTeamIdentityLocalRepository {
                override suspend fun read(tournament: Tournament) =
                    GroupRotationTeamIdentityReadResult.Loaded(identitySnapshot)

                override suspend fun savePairing(
                    tournament: Tournament,
                    pairingKey: GroupPairing,
                    entries: List<com.hoggamers.rankforge.domain.tournament.GroupRotationPairingTeamIdentityEntry>,
                ) = GroupRotationTeamIdentityWriteResult.Unsupported

                override suspend fun renameTeam(
                    tournament: Tournament,
                    teamUUID: String,
                    displayName: String,
                ) = GroupRotationTeamIdentityWriteResult.Unsupported
            },
            matchIdentityRepository = object : GroupRotationMatchIdentityPersistenceRepository {
                override suspend fun read(tournament: Tournament, matchId: String) =
                    matchSnapshots[matchId]
                        ?: GroupRotationMatchPersistenceResult.Invalid(
                            listOf(
                                GroupRotationMatchPersistenceIssue(
                                    GroupRotationMatchPersistenceIssueCode.MATCH_IDENTITY_NOT_SAVED,
                                    matchId = matchId,
                                ),
                            ),
                        )

                override suspend fun save(
                    tournament: Tournament,
                    matchId: String,
                    snapshot: GroupRotationMatchIdentitySnapshot,
                    participantResults: List<GroupRotationMatchParticipantResult>,
                ) = GroupRotationMatchPersistenceResult.Invalid(emptyList())
            },
            journalRepository = journal,
            teamIdentityRemote = identityRemote,
            matchRemote = matchRemote,
            deletionIntentRepository = if (deletionBlocked) BlockingDeletionRepository else NoOpDeletionIntentRepository,
            matchParentProvisioner = matchParentProvisioner,
            requestIdGenerator = RequestIds,
        )

        suspend fun addDraftAndFinalizedMatches() {
            val draft = match(MATCH_ONE, 1, pairing("A:B"), MatchStatus.DRAFT)
            val finalized = match(MATCH_TWO, 2, pairing("A:C"), MatchStatus.FINALIZED)
            assertEquals(
                com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult.Created,
                repository.createDraftMatch(draft),
            )
            assertEquals(
                com.hoggamers.rankforge.domain.tournament.CreateMatchRepositoryResult.Created,
                repository.createDraftMatch(finalized),
            )
            matchSnapshots[draft.id] = persistenceSnapshot(
                pairing("A:B"),
                listOf(1 to TEAM_A, 2 to TEAM_B),
                emptyList(),
            )
            matchSnapshots[finalized.id] = persistenceSnapshot(
                pairing("A:C"),
                listOf(7 to TEAM_A, 11 to TEAM_B),
                listOf(
                    GroupRotationMatchParticipantResult(TEAM_A, MatchParticipationStatus.PARTICIPATED, 1, 4),
                    GroupRotationMatchParticipantResult(TEAM_B, MatchParticipationStatus.PARTICIPATED, 2, 2),
                ),
            )
        }

        suspend fun addLegacyNumericMatch() {
            val legacy = match(MATCH_ONE, 1, pairing("A:B"), MatchStatus.DRAFT)
            repository.createDraftMatch(legacy)
        }

        private val matchSnapshots = linkedMapOf<String, GroupRotationMatchPersistenceResult.Loaded>()

        private fun persistenceSnapshot(
            pairing: GroupPairing,
            teams: List<Pair<Int, String>>,
            results: List<GroupRotationMatchParticipantResult>,
        ) = GroupRotationMatchPersistenceResult.Loaded(
            com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot(
                identity = GroupRotationMatchIdentitySnapshot(
                    tournamentId = TOURNAMENT_ID,
                    pairing = pairing,
                    teams = teams.map { (lobby, uuid) ->
                        GroupRotationMatchTeamIdentity(lobby, uuid, identitySnapshot.teams.first { it.teamUUID == uuid }.displayName)
                    },
                ),
                participantResults = results,
            ),
        )

        private fun match(id: String, number: Int, pairing: GroupPairing, status: MatchStatus) = Match(
            id = id,
            tournamentId = TOURNAMENT_ID,
            matchNumber = number,
            date = LocalDate.of(2026, 10, 12),
            mapName = "Bermuda",
            status = status,
            groupPairing = pairing,
        )
    }

    private class RecordingParentUpload : TournamentCloudUploadRepository {
        var calls = 0
        override suspend fun upload(
            snapshot: com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot,
            ownerId: String,
        ) = TournamentCloudUploadResult.Success(2).also { calls++ }
    }

    private class RecordingIdentityRemote : GroupRotationTeamIdentityCloudV4PreparedRemoteDataSource {
        val parameters = mutableListOf<GroupRotationTeamIdentityV4WriteParameters>()
        val expectedRevisions = mutableListOf<Int>()
        val results = ArrayDeque<GroupRotationTeamIdentityV4ExecutionResult>()
        override suspend fun writePrepared(
            parameters: GroupRotationTeamIdentityV4WriteParameters,
        ): GroupRotationTeamIdentityV4ExecutionResult {
            this.parameters += parameters
            expectedRevisions += parameters.expectedRevision
            return if (results.isNotEmpty()) {
                results.removeFirst()
            } else {
                GroupRotationTeamIdentityV4ExecutionResult.Success(parameters.expectedRevision + 1)
            }
        }
    }

    private class RecordingMatchRemote(
        val provisioned: MutableSet<String>,
    ) : GroupRotationMatchCloudV4PreparedRemoteDataSource {
        val operations = mutableListOf<String>()
        val draftExpectedRevisions = mutableListOf<Int>()
        val finalExpectedRevisions = mutableListOf<Int>()
        override suspend fun writeDraftPrepared(parameters: GroupRotationV4DraftWriteParameters) =
            GroupRotationMatchCloudV4ExecutionResult.Success(parameters.expectedRevision + 1).also {
                check(parameters.match.id in provisioned)
                operations += "draft:${parameters.match.id}"
                draftExpectedRevisions += parameters.expectedRevision
            }

        override suspend fun finalizePrepared(parameters: GroupRotationV4FinalizationParameters) =
            GroupRotationMatchCloudV4ExecutionResult.Success(parameters.expectedRevision + 1).also {
                check(parameters.matchId in provisioned)
                operations += "final:${parameters.matchId}"
                finalExpectedRevisions += parameters.expectedRevision
            }
    }

    private class RecordingMatchParentProvisioner : GroupRotationCloudMatchProvisioner {
        val matchIds = mutableListOf<String>()
        val provisioned = mutableSetOf<String>()
        var result: GroupRotationCloudMatchProvisionResult = GroupRotationCloudMatchProvisionResult.Ensured

        override suspend fun ensureParent(
            tournamentId: String,
            matchId: String,
            expectedOwnerUserId: String?,
        ): GroupRotationCloudMatchProvisionResult {
            matchIds += matchId
            if (result == GroupRotationCloudMatchProvisionResult.Ensured) {
                provisioned += matchId
            }
            return result
        }

        override suspend fun resolveCloudMatchId(
            tournamentId: String,
            matchId: String,
            expectedOwnerUserId: String?,
        ): String = matchId
    }

    private class InMemoryJournal : GroupRotationV4SyncJournalRepository {
        val entries = mutableListOf<GroupRotationV4SyncJournalEntry>()
        private var sequence = 0

        override suspend fun createPending(request: GroupRotationV4SyncJournalRequest): GroupRotationV4SyncJournalCreateResult {
            val existing = entries.firstOrNull { it.requestId == request.requestId }
            if (existing != null) return GroupRotationV4SyncJournalCreateResult.Reused(existing)
            val entry = GroupRotationV4SyncJournalEntry(
                journalId = "journal-${++sequence}",
                ownerUserId = request.ownerUserId,
                tournamentId = request.tournamentId,
                matchId = request.matchId,
                operationType = request.operationType,
                requestId = request.requestId,
                payloadJson = request.payloadJson,
                payloadFormatVersion = request.payloadFormatVersion,
                payloadSha256 = GroupRotationV4SyncJournalPayload.sha256(request.payloadJson),
                expectedCloudRevision = request.expectedCloudRevision,
                status = GroupRotationV4SyncJournalStatus.PENDING,
                attemptCount = 0,
                lastFailureCategory = null,
                confirmedCloudRevision = null,
                createdAtEpochMillis = sequence.toLong(),
                updatedAtEpochMillis = sequence.toLong(),
            )
            entries += entry
            return GroupRotationV4SyncJournalCreateResult.Created(entry)
        }

        override suspend fun readPendingOrUnresolvedByOwner(ownerUserId: String, tournamentId: String) =
            GroupRotationV4SyncJournalEntriesResult.Loaded(unresolved(ownerUserId, tournamentId))

        override suspend fun readByJournalIdAndOwner(journalId: String, ownerUserId: String) =
            com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalReadResult.NotFound

        override suspend fun markInProgress(journalId: String, ownerUserId: String) = update(journalId) {
            it.copy(status = GroupRotationV4SyncJournalStatus.IN_PROGRESS)
        }

        override suspend fun recordRetryableFailure(journalId: String, ownerUserId: String, failureCategory: String) = update(journalId) {
            it.copy(
                status = GroupRotationV4SyncJournalStatus.RETRYABLE_FAILURE,
                attemptCount = it.attemptCount + 1,
                lastFailureCategory = failureCategory,
            )
        }

        override suspend fun recordTerminalFailure(
            journalId: String,
            ownerUserId: String,
            status: GroupRotationV4SyncJournalStatus,
            failureCategory: String,
        ) = update(journalId) { it.copy(status = status, lastFailureCategory = failureCategory) }

        override suspend fun markConfirmed(journalId: String, ownerUserId: String, confirmedCloudRevision: Int) = update(journalId) {
            it.copy(
                status = GroupRotationV4SyncJournalStatus.CONFIRMED,
                confirmedCloudRevision = confirmedCloudRevision,
            )
        }

        override suspend fun recoverUnfinishedByOwner(ownerUserId: String, tournamentId: String) =
            GroupRotationV4SyncJournalEntriesResult.Loaded(unresolved(ownerUserId, tournamentId).map {
                if (it.status == GroupRotationV4SyncJournalStatus.IN_PROGRESS) {
                    it.copy(status = GroupRotationV4SyncJournalStatus.RETRYABLE_FAILURE, attemptCount = it.attemptCount + 1)
                } else it
            })

        override suspend fun invalidateByTournamentAndOwner(tournamentId: String, ownerUserId: String) = 0

        private fun unresolved(ownerUserId: String, tournamentId: String) = entries.filter {
            it.ownerUserId == ownerUserId && it.tournamentId == tournamentId && it.status in setOf(
                GroupRotationV4SyncJournalStatus.PENDING,
                GroupRotationV4SyncJournalStatus.IN_PROGRESS,
                GroupRotationV4SyncJournalStatus.RETRYABLE_FAILURE,
            )
        }

        private fun update(
            journalId: String,
            change: (GroupRotationV4SyncJournalEntry) -> GroupRotationV4SyncJournalEntry,
        ): GroupRotationV4SyncJournalMutationResult {
            val index = entries.indexOfFirst { it.journalId == journalId }
            if (index < 0) return GroupRotationV4SyncJournalMutationResult.NotFound
            val updated = change(entries[index])
            entries[index] = updated
            return GroupRotationV4SyncJournalMutationResult.Updated(updated)
        }
    }

    private object BlockingDeletionRepository : DeletionIntentRepository {
        override suspend fun isBlockingByTournamentIdAndOwner(tournamentId: String, ownerUserId: String) = true
    }

    private object RequestIds : () -> String {
        private var next = 0
        override fun invoke(): String = "00000000-0000-0000-0000-%012d".format(++next)
    }

    private companion object {
        const val OWNER_ID = "test-owner"
        const val TOURNAMENT_ID = "00000000-0000-0000-0000-000000000001"
        const val MATCH_ONE = "00000000-0000-0000-0000-000000000011"
        const val MATCH_TWO = "00000000-0000-0000-0000-000000000022"
        const val TEAM_A = "00000000-0000-0000-0000-0000000000a1"
        const val TEAM_B = "00000000-0000-0000-0000-0000000000b2"

        fun pairing(key: String) = GroupPairing.fromCanonicalKey(key)

        fun team(uuid: String, name: String) = GroupRotationTeamIdentity(
            tournamentId = TOURNAMENT_ID,
            teamUUID = uuid,
            displayName = name,
            normalizedName = name.lowercase(),
        )

        fun assignment(pairing: GroupPairing, lobby: Int, uuid: String) =
            com.hoggamers.rankforge.domain.tournament.GroupRotationPairingAssignment(
                tournamentId = TOURNAMENT_ID,
                pairingKey = pairing,
                lobbySlotNumber = lobby,
                teamUUID = uuid,
            )
    }
}
