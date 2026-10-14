package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalCreateResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalEntry
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalEntriesResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalMutationResult
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalOperationType
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalRepository
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalRequest
import com.hoggamers.rankforge.domain.sync.GroupRotationV4SyncJournalStatus
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.tournament.DeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchIdentityPersistenceRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.NoOpDeletionIntentRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadLocalSnapshotRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Explicit, inactive V4 upload entry point for UUID-backed Group Rotation.
 * It is intentionally not bound to the normal upload action until cloud
 * restoration and device verification are complete.
 */
@Singleton
class GroupRotationV4CloudUploadCoordinator @Inject constructor(
    private val authRepository: AuthRepository,
    private val tournamentRepository: TournamentRepository,
    private val cloudUploadSnapshotRepository: TournamentCloudUploadLocalSnapshotRepository,
    private val tournamentCloudUploadRepository: TournamentCloudUploadRepository,
    private val teamIdentityRepository: GroupRotationTeamIdentityLocalRepository,
    private val matchIdentityRepository: GroupRotationMatchIdentityPersistenceRepository,
    private val journalRepository: GroupRotationV4SyncJournalRepository,
    private val teamIdentityRemote: GroupRotationTeamIdentityCloudV4PreparedRemoteDataSource,
    private val matchRemote: GroupRotationMatchCloudV4PreparedRemoteDataSource,
    private val deletionIntentRepository: DeletionIntentRepository = NoOpDeletionIntentRepository,
    private val matchParentProvisioner: GroupRotationCloudMatchProvisioner =
        NoOpGroupRotationCloudMatchProvisioner,
    @param:javax.inject.Named(REQUEST_ID_GENERATOR)
    private val requestIdGenerator: () -> String,
) {
    private val uploadMutex = Mutex()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    suspend fun upload(tournamentId: String): GroupRotationV4CloudUploadResult =
        uploadMutex.withLock { uploadLocked(tournamentId) }

    private suspend fun uploadLocked(tournamentId: String): GroupRotationV4CloudUploadResult {
        val ownerUserId = currentOwnerUserId()
            ?: return GroupRotationV4CloudUploadResult.AuthenticationRequired
        if (deletionIntentRepository.isBlockingByTournamentIdAndOwner(tournamentId, ownerUserId)) {
            return GroupRotationV4CloudUploadResult.DeletionBlocked
        }
        val tournament = tournamentRepository.observeByIdAndOwner(tournamentId, ownerUserId).first()
            ?: return GroupRotationV4CloudUploadResult.AuthorizationFailure
        if (tournament.format != TournamentFormat.GROUP_ROTATION) {
            return GroupRotationV4CloudUploadResult.ValidationFailure
        }

        val recovered = journalRepository.recoverUnfinishedByOwner(ownerUserId, tournamentId)
        when (recovered) {
            is GroupRotationV4SyncJournalEntriesResult.Corrupt ->
                return GroupRotationV4CloudUploadResult.ValidationFailure
            is GroupRotationV4SyncJournalEntriesResult.Loaded -> if (recovered.entries.isNotEmpty()) {
                when (val recovery = processJournalEntries(recovered.entries, ownerUserId, tournamentId)) {
                    is GroupRotationV4CloudUploadResult.Success -> Unit
                    else -> return recovery
                }
            }
        }

        val parentSnapshot = cloudUploadSnapshotRepository
            .readCloudUploadSnapshotByOwner(tournamentId, ownerUserId)
            ?: return GroupRotationV4CloudUploadResult.ValidationFailure
        val parentRevision = when (
            val result = tournamentCloudUploadRepository.upload(parentSnapshot, ownerUserId)
        ) {
            is TournamentCloudUploadResult.Success -> result.confirmedCloudRevision
            TournamentCloudUploadResult.AuthenticationRequired ->
                return GroupRotationV4CloudUploadResult.AuthenticationRequired
            TournamentCloudUploadResult.AuthorizationFailure ->
                return GroupRotationV4CloudUploadResult.AuthorizationFailure
            TournamentCloudUploadResult.NetworkFailure ->
                return GroupRotationV4CloudUploadResult.NetworkFailure
            TournamentCloudUploadResult.ValidationFailure,
            TournamentCloudUploadResult.TournamentLimitReached,
            -> return GroupRotationV4CloudUploadResult.ValidationFailure
            is TournamentCloudUploadResult.Conflict ->
                return GroupRotationV4CloudUploadResult.Conflict(result.conflict)
            is TournamentCloudUploadResult.PartialFailure ->
                return GroupRotationV4CloudUploadResult.NetworkFailure
        }
        if (!isCurrentOwner(ownerUserId) || deletionIntentRepository.isBlockingByTournamentIdAndOwner(tournamentId, ownerUserId)) {
            return GroupRotationV4CloudUploadResult.AuthorizationFailure
        }
        when (tournamentRepository.confirmCloudRevisionByOwner(tournamentId, ownerUserId, parentRevision)) {
            com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.Saved -> Unit
            com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.TournamentNotFound ->
                return GroupRotationV4CloudUploadResult.AuthorizationFailure
        }

        val identitySnapshot = when (val read = teamIdentityRepository.read(tournament)) {
            is GroupRotationTeamIdentityReadResult.Loaded -> read.snapshot
            GroupRotationTeamIdentityReadResult.Unsupported,
            is GroupRotationTeamIdentityReadResult.Invalid,
            -> return GroupRotationV4CloudUploadResult.ValidationFailure
        }
        val identityRequestId = nextRequestId()
            ?: return GroupRotationV4CloudUploadResult.ValidationFailure
        val identityMapping = GroupRotationTeamIdentityCloudV4Mapper.map(
            request = GroupRotationTeamIdentityV4WriteRequest(
                tournament = tournament,
                pairingsToReplace = tournament.selectedGroupPairings
                    .distinct()
                    .sortedBy { it.canonicalKey },
                expectedRevision = parentRevision,
                requestId = identityRequestId,
            ),
            readResult = GroupRotationTeamIdentityReadResult.Loaded(identitySnapshot),
        )
        val identityParameters = (identityMapping as? GroupRotationTeamIdentityV4MappingResult.Success)
            ?.parameters
            ?: return GroupRotationV4CloudUploadResult.ValidationFailure
        val identityResult = dispatchNew(
            ownerUserId = ownerUserId,
            tournamentId = tournamentId,
            matchId = null,
            operationType = GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT,
            payloadJson = json.encodeToString(identityParameters),
            expectedRevision = parentRevision,
        )
        val revisionAfterIdentity = when (identityResult) {
            is OperationResult.Confirmed -> identityResult.revision
            is OperationResult.Failed -> return identityResult.result
        }

        val matches = tournamentRepository
            .observeMatchesByTournamentIdAndOwner(tournamentId, ownerUserId)
            .first()
            .sortedWith(compareBy<Match> { it.matchNumber }.thenBy { it.id })
        val uuidMatchSnapshots = mutableListOf<Pair<Match, com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot>>()
        matches.forEach { match ->
            when (val stored = matchIdentityRepository.read(tournament, match.id)) {
                is GroupRotationMatchPersistenceResult.Loaded ->
                    uuidMatchSnapshots += match to stored.snapshot
                is GroupRotationMatchPersistenceResult.Invalid -> {
                    if (!stored.issues.all {
                            it.code == com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceIssueCode.MATCH_IDENTITY_NOT_SAVED
                        }) {
                        return GroupRotationV4CloudUploadResult.ValidationFailure
                    }
                }
                is GroupRotationMatchPersistenceResult.Saved ->
                    return GroupRotationV4CloudUploadResult.ValidationFailure
            }
        }

        var revision = revisionAfterIdentity
        uuidMatchSnapshots.forEach { (match, snapshot) ->
            when (
                val matchResult = synchronizeMatch(
                    tournament = tournament,
                    match = match,
                    snapshot = snapshot,
                    identitySnapshot = identitySnapshot,
                    ownerUserId = ownerUserId,
                    revision = revision,
                )
            ) {
                is OperationResult.Confirmed -> revision = matchResult.revision
                is OperationResult.Failed -> return matchResult.result
            }
        }
        return GroupRotationV4CloudUploadResult.Success(revision)
    }

    private suspend fun synchronizeMatch(
        tournament: com.hoggamers.rankforge.domain.tournament.Tournament,
        match: Match,
        snapshot: com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot,
        identitySnapshot: com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalSnapshot,
        ownerUserId: String,
        revision: Int,
    ): OperationResult {
        val remoteState = matchParentProvisioner.readRemoteState(
            tournamentId = tournament.id,
            matchId = match.id,
            expectedOwnerUserId = ownerUserId,
        )
        val stateRevision = when (remoteState) {
            GroupRotationCloudMatchRemoteStateResult.NotRequired ->
                return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
            is GroupRotationCloudMatchRemoteStateResult.Missing -> remoteState.currentTournamentRevision
            is GroupRotationCloudMatchRemoteStateResult.Loaded -> remoteState.currentTournamentRevision
            GroupRotationCloudMatchRemoteStateResult.AuthenticationRequired ->
                return OperationResult.Failed(GroupRotationV4CloudUploadResult.AuthenticationRequired)
            GroupRotationCloudMatchRemoteStateResult.AuthorizationFailure ->
                return OperationResult.Failed(GroupRotationV4CloudUploadResult.AuthorizationFailure)
            GroupRotationCloudMatchRemoteStateResult.ValidationFailure ->
                return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
            GroupRotationCloudMatchRemoteStateResult.NetworkFailure ->
                return OperationResult.Failed(GroupRotationV4CloudUploadResult.NetworkFailure)
        }.let { remoteRevision ->
            if (remoteRevision <= 0) revision else remoteRevision
        }
        if (stateRevision != revision) {
            return OperationResult.Failed(
                GroupRotationV4CloudUploadResult.Conflict(
                    RevisionConflict.StaleWrite(
                        expectedRevision = com.hoggamers.rankforge.domain.sync.CloudRevision(revision),
                        currentCloudRevision = com.hoggamers.rankforge.domain.sync.CloudRevision(stateRevision),
                    ),
                ),
            )
        }

        val desiredSource = GroupRotationMatchCloudV4RequestSource(snapshot, identitySnapshot)
        when (remoteState) {
            is GroupRotationCloudMatchRemoteStateResult.Missing -> {
                val draft = dispatchDraft(
                    tournament = tournament,
                    match = match,
                    source = desiredSource,
                    ownerUserId = ownerUserId,
                    expectedRevision = stateRevision,
                )
                if (draft is OperationResult.Failed || match.status != MatchStatus.FINALIZED) return draft
                val draftRevision = (draft as OperationResult.Confirmed).revision
                return dispatchFinalization(
                    tournament = tournament,
                    match = match,
                    source = desiredSource,
                    ownerUserId = ownerUserId,
                    expectedRevision = draftRevision,
                )
            }
            is GroupRotationCloudMatchRemoteStateResult.Loaded -> {
                val identityMatches = remoteState.matchesIdentity(tournament.id, match.id, snapshot)
                val resultsMatchDraft = remoteState.matchesDomainResults(snapshot.participantResults)
                if (remoteState.snapshot.status == "finalized") {
                    return if (match.status == MatchStatus.FINALIZED && identityMatches && resultsMatchDraft) {
                        OperationResult.Confirmed(stateRevision)
                    } else {
                        finalizedDataConflict(stateRevision)
                    }
                }

                var currentRevision = stateRevision
                if (!identityMatches && match.status == MatchStatus.FINALIZED) {
                    return finalizedDataConflict(stateRevision)
                }
                if (
                    match.status == MatchStatus.FINALIZED &&
                    !resultsMatchDraft &&
                    remoteState.results.isNotEmpty()
                ) {
                    return finalizedDataConflict(stateRevision)
                }
                if (!identityMatches || !resultsMatchDraft) {
                    when (
                        val draft = dispatchDraft(
                            tournament = tournament,
                            match = match,
                            source = desiredSource,
                            ownerUserId = ownerUserId,
                            expectedRevision = currentRevision,
                        )
                    ) {
                        is OperationResult.Confirmed -> currentRevision = draft.revision
                        is OperationResult.Failed -> return draft
                    }
                }
                return if (match.status == MatchStatus.FINALIZED) {
                    dispatchFinalization(
                        tournament = tournament,
                        match = match,
                        source = desiredSource,
                        ownerUserId = ownerUserId,
                        expectedRevision = currentRevision,
                    )
                } else {
                    OperationResult.Confirmed(currentRevision)
                }
            }
            GroupRotationCloudMatchRemoteStateResult.NotRequired,
            GroupRotationCloudMatchRemoteStateResult.AuthenticationRequired,
            GroupRotationCloudMatchRemoteStateResult.AuthorizationFailure,
            GroupRotationCloudMatchRemoteStateResult.ValidationFailure,
            GroupRotationCloudMatchRemoteStateResult.NetworkFailure,
            -> error("Remote state failures are returned before matching the state.")
        }
    }

    private suspend fun dispatchDraft(
        tournament: com.hoggamers.rankforge.domain.tournament.Tournament,
        match: Match,
        source: GroupRotationMatchCloudV4RequestSource,
        ownerUserId: String,
        expectedRevision: Int,
    ): OperationResult {
        val requestId = nextRequestId()
            ?: return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
        val mapping = GroupRotationMatchCloudV4Mapper.mapDraft(
            tournament = tournament,
            request = GroupRotationV4DraftWriteRequest(
                tournament = tournament,
                tournamentId = tournament.id,
                matchId = match.id,
                source = source,
                expectedRevision = expectedRevision,
                requestId = requestId,
            ),
        )
        val parameters = (mapping as? GroupRotationV4MappingResult.Success)
            ?.parameters
            ?: return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
        return dispatchNew(
            ownerUserId = ownerUserId,
            tournamentId = tournament.id,
            matchId = match.id,
            operationType = GroupRotationV4SyncJournalOperationType.MATCH_DRAFT,
            payloadJson = json.encodeToString(parameters),
            expectedRevision = expectedRevision,
        )
    }

    private suspend fun dispatchFinalization(
        tournament: com.hoggamers.rankforge.domain.tournament.Tournament,
        match: Match,
        source: GroupRotationMatchCloudV4RequestSource,
        ownerUserId: String,
        expectedRevision: Int,
    ): OperationResult {
        val requestId = nextRequestId()
            ?: return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
        val mapping = GroupRotationMatchCloudV4Mapper.mapFinalization(
            tournament = tournament,
            request = GroupRotationV4FinalizationRequest(
                tournament = tournament,
                tournamentId = tournament.id,
                matchId = match.id,
                source = source,
                expectedRevision = expectedRevision,
                requestId = requestId,
            ),
        )
        val parameters = (mapping as? GroupRotationV4MappingResult.Success)
            ?.parameters
            ?: return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
        return dispatchNew(
            ownerUserId = ownerUserId,
            tournamentId = tournament.id,
            matchId = match.id,
            operationType = GroupRotationV4SyncJournalOperationType.MATCH_FINALIZATION,
            payloadJson = json.encodeToString(parameters),
            expectedRevision = expectedRevision,
        )
    }

    private fun finalizedDataConflict(revision: Int): OperationResult.Failed =
        OperationResult.Failed(GroupRotationV4CloudUploadResult.Conflict(finalizedConflict(revision)))

    private fun finalizedConflict(revision: Int): RevisionConflict =
        RevisionConflict.LocalCloudDivergence(
            baseRevision = com.hoggamers.rankforge.domain.sync.CloudRevision(revision),
            localRevision = revision,
            cloudRevision = com.hoggamers.rankforge.domain.sync.CloudRevision(revision),
        )

    private fun GroupRotationCloudMatchRemoteStateResult.Loaded.matchesIdentity(
        tournamentId: String,
        matchId: String,
        snapshot: com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot,
    ): Boolean =
        this.snapshot.matchId == matchId &&
            this.snapshot.tournamentId == tournamentId &&
            this.snapshot.pairingKey == snapshot.identity.pairing.canonicalKey &&
            assignments
                .map { it.lobbySlotNumber to it.teamUUID }
                .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second }) ==
            snapshot.identity.teams
                .map { it.lobbySlotNumber to it.teamUUID }
                .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second })

    private fun GroupRotationCloudMatchRemoteStateResult.Loaded.matchesDomainResults(
        results: List<com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult>,
    ): Boolean =
        this.results
            .map { CloudResultShape(it.teamUUID, it.participationStatus, it.placement, it.kills, it.pointAdjustment) }
            .sortedBy { it.teamUUID } ==
            results
                .map { CloudResultShape(it.teamUUID, it.participationStatus.name, it.placement, it.kills, it.pointAdjustment) }
                .sortedBy { it.teamUUID }

    private fun GroupRotationCloudMatchRemoteStateResult.Loaded.matchesPayloadResults(
        results: List<GroupRotationUuidParticipantResultPayload>,
    ): Boolean =
        this.results
            .map { CloudResultShape(it.teamUUID, it.participationStatus, it.placement, it.kills, it.pointAdjustment) }
            .sortedBy { it.teamUUID } ==
            results
                .map { CloudResultShape(it.teamUUID, it.participationStatus, it.placement, it.kills, it.pointAdjustment) }
                .sortedBy { it.teamUUID }

    private suspend fun processJournalEntries(
        entries: List<GroupRotationV4SyncJournalEntry>,
        ownerUserId: String,
        tournamentId: String,
    ): GroupRotationV4CloudUploadResult {
        var revision = entries.firstOrNull()?.expectedCloudRevision
            ?: return GroupRotationV4CloudUploadResult.ValidationFailure
        entries.sortedBy { it.createdAtEpochMillis }.forEach { entry ->
            if (entry.tournamentId != tournamentId || entry.ownerUserId != ownerUserId) {
                return GroupRotationV4CloudUploadResult.AuthorizationFailure
            }
            when (
                val result = dispatchEntry(entry, ownerUserId)
            ) {
                is OperationResult.Confirmed -> revision = result.revision
                is OperationResult.Failed -> return result.result
            }
        }
        return GroupRotationV4CloudUploadResult.Success(revision)
    }

    private suspend fun dispatchEntry(
        entry: GroupRotationV4SyncJournalEntry,
        ownerUserId: String,
        parentAlreadyEnsured: Boolean = false,
    ): OperationResult {
        if (entry.operationType == GroupRotationV4SyncJournalOperationType.MATCH_DRAFT ||
            entry.operationType == GroupRotationV4SyncJournalOperationType.MATCH_FINALIZATION
        ) {
            val matchId = entry.matchId
                ?: return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
            if (!parentAlreadyEnsured) when (matchParentProvisioner.ensureParent(entry.tournamentId, matchId, ownerUserId)) {
                GroupRotationCloudMatchProvisionResult.Ensured,
                GroupRotationCloudMatchProvisionResult.NotRequired,
                -> Unit
                GroupRotationCloudMatchProvisionResult.AuthenticationRequired ->
                    return OperationResult.Failed(GroupRotationV4CloudUploadResult.AuthenticationRequired)
                GroupRotationCloudMatchProvisionResult.AuthorizationFailure ->
                    return OperationResult.Failed(GroupRotationV4CloudUploadResult.AuthorizationFailure)
                GroupRotationCloudMatchProvisionResult.ValidationFailure ->
                    return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
                GroupRotationCloudMatchProvisionResult.NetworkFailure ->
                    return OperationResult.Failed(GroupRotationV4CloudUploadResult.NetworkFailure)
            }
        }
        val marked = journalRepository.markInProgress(entry.journalId, ownerUserId)
        if (marked !is GroupRotationV4SyncJournalMutationResult.Updated) {
            return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
        }
        val remoteCall: suspend () -> Any = try {
            when (entry.operationType) {
                GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT -> {
                    val parameters = json.decodeFromString<GroupRotationTeamIdentityV4WriteParameters>(entry.payloadJson)
                    suspend { teamIdentityRemote.writePrepared(parameters) }
                }
                GroupRotationV4SyncJournalOperationType.MATCH_DRAFT -> {
                    val parameters = json.decodeFromString<GroupRotationV4DraftWriteParameters>(entry.payloadJson)
                    suspend { matchRemote.writeDraftPrepared(parameters) }
                }
                GroupRotationV4SyncJournalOperationType.MATCH_FINALIZATION -> {
                    val parameters = json.decodeFromString<GroupRotationV4FinalizationParameters>(entry.payloadJson)
                    suspend { matchRemote.finalizePrepared(parameters) }
                }
            }
        } catch (_: Throwable) {
            journalRepository.recordTerminalFailure(
                journalId = entry.journalId,
                ownerUserId = ownerUserId,
                status = GroupRotationV4SyncJournalStatus.TERMINAL_VALIDATION_FAILURE,
                failureCategory = "invalid_frozen_payload",
            )
            return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
        }
        return try {
            val remote = remoteCall()
            applyRemoteResult(entry, ownerUserId, remote)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            journalRepository.recordRetryableFailure(
                journalId = entry.journalId,
                ownerUserId = ownerUserId,
                failureCategory = "network",
            )
            OperationResult.Failed(GroupRotationV4CloudUploadResult.NetworkFailure)
        }
    }

    private suspend fun dispatchNew(
        ownerUserId: String,
        tournamentId: String,
        matchId: String?,
        operationType: GroupRotationV4SyncJournalOperationType,
        payloadJson: String,
        expectedRevision: Int,
    ): OperationResult {
        val requestId = when (operationType) {
                GroupRotationV4SyncJournalOperationType.IDENTITY_SNAPSHOT ->
                    json.decodeFromString<GroupRotationTeamIdentityV4WriteParameters>(payloadJson).requestId
                GroupRotationV4SyncJournalOperationType.MATCH_DRAFT ->
                    json.decodeFromString<GroupRotationV4DraftWriteParameters>(payloadJson).requestId
                GroupRotationV4SyncJournalOperationType.MATCH_FINALIZATION ->
                    json.decodeFromString<GroupRotationV4FinalizationParameters>(payloadJson).requestId
            }
        val parentAlreadyEnsured = operationType == GroupRotationV4SyncJournalOperationType.MATCH_DRAFT ||
            operationType == GroupRotationV4SyncJournalOperationType.MATCH_FINALIZATION
        if (parentAlreadyEnsured) {
            val newMatchId = matchId
                ?: return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
            when (matchParentProvisioner.ensureParent(tournamentId, newMatchId, ownerUserId)) {
                GroupRotationCloudMatchProvisionResult.Ensured,
                GroupRotationCloudMatchProvisionResult.NotRequired,
                -> Unit
                GroupRotationCloudMatchProvisionResult.AuthenticationRequired ->
                    return OperationResult.Failed(GroupRotationV4CloudUploadResult.AuthenticationRequired)
                GroupRotationCloudMatchProvisionResult.AuthorizationFailure ->
                    return OperationResult.Failed(GroupRotationV4CloudUploadResult.AuthorizationFailure)
                GroupRotationCloudMatchProvisionResult.ValidationFailure ->
                    return OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
                GroupRotationCloudMatchProvisionResult.NetworkFailure ->
                    return OperationResult.Failed(GroupRotationV4CloudUploadResult.NetworkFailure)
            }
        }
        val create = journalRepository.createPending(
            GroupRotationV4SyncJournalRequest(
                ownerUserId = ownerUserId,
                tournamentId = tournamentId,
                matchId = matchId,
                operationType = operationType,
                requestId = requestId,
                payloadJson = payloadJson,
                expectedCloudRevision = expectedRevision,
            ),
        )
        val entry = when (create) {
            is GroupRotationV4SyncJournalCreateResult.Created -> create.entry
            is GroupRotationV4SyncJournalCreateResult.Reused -> create.entry
            else -> return OperationResult.Failed(create.toUploadResult())
        }
        return dispatchEntry(entry, ownerUserId, parentAlreadyEnsured)
    }

    private suspend fun applyRemoteResult(
        entry: GroupRotationV4SyncJournalEntry,
        ownerUserId: String,
        remote: Any,
    ): OperationResult = when (remote) {
        is GroupRotationTeamIdentityV4ExecutionResult -> when (remote) {
            is GroupRotationTeamIdentityV4ExecutionResult.Success -> confirm(entry, ownerUserId, remote.confirmedCloudRevision)
            is GroupRotationTeamIdentityV4ExecutionResult.Failure -> recordFailure(entry, ownerUserId, remote.category, remote.conflict)
        }
        is GroupRotationMatchCloudV4ExecutionResult -> when (remote) {
            is GroupRotationMatchCloudV4ExecutionResult.Success -> confirm(entry, ownerUserId, remote.confirmedCloudRevision)
            is GroupRotationMatchCloudV4ExecutionResult.Failure -> {
                if (remote.outcome == "already_finalized" &&
                    entry.operationType == GroupRotationV4SyncJournalOperationType.MATCH_FINALIZATION
                ) {
                    reconcileAlreadyFinalized(entry, ownerUserId)
                } else {
                    recordFailure(entry, ownerUserId, remote.category, remote.conflict)
                }
            }
        }
        else -> OperationResult.Failed(GroupRotationV4CloudUploadResult.ValidationFailure)
    }

    private suspend fun reconcileAlreadyFinalized(
        entry: GroupRotationV4SyncJournalEntry,
        ownerUserId: String,
    ): OperationResult {
        val matchId = entry.matchId ?: return recordFailure(
            entry,
            ownerUserId,
            GroupRotationMatchCloudV4FailureCategory.VALIDATION,
            null,
        )
        return when (
            val state = matchParentProvisioner.readRemoteState(
                tournamentId = entry.tournamentId,
                matchId = matchId,
                expectedOwnerUserId = ownerUserId,
            )
        ) {
            is GroupRotationCloudMatchRemoteStateResult.Loaded -> {
                val parameters = runCatching {
                    json.decodeFromString<GroupRotationV4FinalizationParameters>(entry.payloadJson)
                }.getOrNull()
                if (parameters != null &&
                    state.snapshot.status == "finalized" &&
                    state.snapshot.matchId == parameters.matchId &&
                    state.snapshot.tournamentId == parameters.tournamentId &&
                    state.matchesPayloadResults(parameters.matchResults)
                ) {
                    confirm(entry, ownerUserId, state.currentTournamentRevision)
                } else {
                    recordFailure(
                        entry,
                        ownerUserId,
                        GroupRotationMatchCloudV4FailureCategory.CONFLICT,
                        finalizedConflict(state.currentTournamentRevision),
                    )
                }
            }
            GroupRotationCloudMatchRemoteStateResult.AuthenticationRequired ->
                recordFailure(entry, ownerUserId, GroupRotationMatchCloudV4FailureCategory.AUTHENTICATION, null)
            GroupRotationCloudMatchRemoteStateResult.AuthorizationFailure ->
                recordFailure(entry, ownerUserId, GroupRotationMatchCloudV4FailureCategory.AUTHORIZATION, null)
            GroupRotationCloudMatchRemoteStateResult.NetworkFailure ->
                recordFailure(entry, ownerUserId, GroupRotationMatchCloudV4FailureCategory.NETWORK, null)
            GroupRotationCloudMatchRemoteStateResult.NotRequired,
            is GroupRotationCloudMatchRemoteStateResult.Missing,
            GroupRotationCloudMatchRemoteStateResult.ValidationFailure,
            -> recordFailure(
                entry,
                ownerUserId,
                GroupRotationMatchCloudV4FailureCategory.CONFLICT,
                finalizedConflict(entry.expectedCloudRevision),
            )
        }
    }

    private suspend fun confirm(
        entry: GroupRotationV4SyncJournalEntry,
        ownerUserId: String,
        revision: Int,
    ): OperationResult {
        if (!isCurrentOwner(ownerUserId) ||
            deletionIntentRepository.isBlockingByTournamentIdAndOwner(entry.tournamentId, ownerUserId)
        ) {
            return OperationResult.Failed(GroupRotationV4CloudUploadResult.AuthorizationFailure)
        }
        when (tournamentRepository.confirmCloudRevisionByOwner(entry.tournamentId, ownerUserId, revision)) {
            com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.Saved -> Unit
            com.hoggamers.rankforge.domain.tournament.OwnerScopedTournamentMutationResult.TournamentNotFound ->
                return OperationResult.Failed(GroupRotationV4CloudUploadResult.AuthorizationFailure)
        }
        val result = journalRepository.markConfirmed(entry.journalId, ownerUserId, revision)
        return if (result is GroupRotationV4SyncJournalMutationResult.Updated) {
            OperationResult.Confirmed(revision)
        } else {
            OperationResult.Failed(GroupRotationV4CloudUploadResult.NetworkFailure)
        }
    }

    private suspend fun recordFailure(
        entry: GroupRotationV4SyncJournalEntry,
        ownerUserId: String,
        category: Enum<*>,
        conflict: RevisionConflict?,
    ): OperationResult {
        val mapped = when (category.name) {
            "AUTHENTICATION" -> GroupRotationV4CloudUploadResult.AuthenticationRequired
            "AUTHORIZATION" -> GroupRotationV4CloudUploadResult.AuthorizationFailure
            "VALIDATION" -> GroupRotationV4CloudUploadResult.ValidationFailure
            "CONFLICT" -> GroupRotationV4CloudUploadResult.Conflict(
                conflict ?: RevisionConflict.MissingRevision,
            )
            else -> GroupRotationV4CloudUploadResult.NetworkFailure
        }
        val terminal = mapped is GroupRotationV4CloudUploadResult.ValidationFailure ||
            mapped is GroupRotationV4CloudUploadResult.AuthorizationFailure ||
            mapped is GroupRotationV4CloudUploadResult.Conflict
        if (terminal) {
            journalRepository.recordTerminalFailure(
                journalId = entry.journalId,
                ownerUserId = ownerUserId,
                status = when (mapped) {
                    is GroupRotationV4CloudUploadResult.Conflict -> GroupRotationV4SyncJournalStatus.TERMINAL_CONFLICT
                    is GroupRotationV4CloudUploadResult.AuthorizationFailure ->
                        GroupRotationV4SyncJournalStatus.TERMINAL_AUTHORIZATION_FAILURE
                    else -> GroupRotationV4SyncJournalStatus.TERMINAL_VALIDATION_FAILURE
                },
                failureCategory = category.name.lowercase(),
            )
        } else {
            journalRepository.recordRetryableFailure(entry.journalId, ownerUserId, category.name.lowercase())
        }
        return OperationResult.Failed(mapped)
    }

    private suspend fun currentOwnerUserId(): String? =
        (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }

    private suspend fun isCurrentOwner(ownerUserId: String): Boolean = currentOwnerUserId() == ownerUserId

    private fun nextRequestId(): String? = requestIdGenerator().takeIf {
        runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false)
    }

    private fun GroupRotationV4SyncJournalCreateResult.toUploadResult() = when (this) {
        GroupRotationV4SyncJournalCreateResult.Unauthorized -> GroupRotationV4CloudUploadResult.AuthorizationFailure
        GroupRotationV4SyncJournalCreateResult.DeletionBlocked -> GroupRotationV4CloudUploadResult.DeletionBlocked
        is GroupRotationV4SyncJournalCreateResult.Invalid,
        is GroupRotationV4SyncJournalCreateResult.Corrupt,
        -> GroupRotationV4CloudUploadResult.ValidationFailure
        is GroupRotationV4SyncJournalCreateResult.Created,
        is GroupRotationV4SyncJournalCreateResult.Reused,
        -> error("Successful journal creation is handled before mapping the result.")
    }

    private sealed interface OperationResult {
        data class Confirmed(val revision: Int) : OperationResult
        data class Failed(val result: GroupRotationV4CloudUploadResult) : OperationResult
    }

    companion object {
        const val REQUEST_ID_GENERATOR = "group_rotation_v4_request_id_generator"
    }

}

sealed interface GroupRotationV4CloudUploadResult {
    data class Success(val confirmedCloudRevision: Int) : GroupRotationV4CloudUploadResult
    data object AuthenticationRequired : GroupRotationV4CloudUploadResult
    data object AuthorizationFailure : GroupRotationV4CloudUploadResult
    data object ValidationFailure : GroupRotationV4CloudUploadResult
    data object NetworkFailure : GroupRotationV4CloudUploadResult
    data object DeletionBlocked : GroupRotationV4CloudUploadResult
    data class Conflict(val conflict: RevisionConflict) : GroupRotationV4CloudUploadResult
}

private data class CloudResultShape(
    val teamUUID: String,
    val participationStatus: String,
    val placement: Int?,
    val kills: Int,
    val pointAdjustment: Int,
)
