package com.hoggamers.rankforge.data.cloud

import android.util.Log
import com.hoggamers.rankforge.BuildConfig
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.isSignedInAs
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.sync.queueFailureCategory
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncRetryAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncRetryAction
import com.hoggamers.rankforge.domain.tournament.FinalizedMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentity
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityLocalRepository
import com.hoggamers.rankforge.domain.tournament.GroupRotationTeamIdentityReadResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationUuidCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.RestoreGroupRotationUuidCloudUseCase
import com.hoggamers.rankforge.domain.tournament.RestoreTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.SyncDraftMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.SyncFinalizedMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRetryAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRetryAction
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Connects the already implemented UUID cloud workflow to the normal Dev
 * actions. Standard and legacy Group Rotation data stay on their existing
 * actions; release builds never select this route.
 */
@Singleton
class GroupRotationDevCloudRoute @Inject constructor(
    private val authRepository: AuthRepository,
    private val tournamentRepository: TournamentRepository,
    private val teamIdentityRepository: GroupRotationTeamIdentityLocalRepository,
    private val queueRecorder: RecordSyncQueueOutcome,
    private val uploadDelegate: com.hoggamers.rankforge.domain.tournament.UploadTournamentUseCase,
    private val uploadCoordinator: GroupRotationV4CloudUploadCoordinator,
    private val restorationDelegate: RestoreTournamentUseCase,
    private val restorationCloudRepository: TournamentCloudRestorationRepository,
    private val uuidRestoration: RestoreGroupRotationUuidCloudUseCase,
    private val draftDelegate: SyncDraftMatchesUseCase,
    private val finalizedDelegate: SyncFinalizedMatchesUseCase,
    @param:Named(DEV_V4_ENABLED) private val devV4Enabled: Boolean,
) {

    suspend fun uploadAction(
        tournamentId: String,
    ): QueueAwareActionResult<TournamentCloudUploadResult> {
        val owner = currentOwnerUserId() ?: return uploadDelegate(tournamentId)
        if (!shouldUseV4(tournamentId, owner)) return uploadDelegate(tournamentId)
        val result = uploadCoordinator.upload(tournamentId).toTournamentUploadResult()
        return result.withQueue(owner, SyncQueueOperationType.TOURNAMENT_UPLOAD, tournamentId)
    }

    suspend fun uploadRetry(tournamentId: String): TournamentCloudUploadResult =
        currentOwnerUserId()?.let { uploadRetryForOwner(tournamentId, it) }
            ?: TournamentCloudUploadResult.AuthenticationRequired

    suspend fun uploadRetryForOwner(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): TournamentCloudUploadResult {
        if (!shouldUseV4(tournamentId, expectedOwnerUserId)) {
            return uploadDelegate.executeForRetry(tournamentId, expectedOwnerUserId)
        }
        return uploadCoordinator.upload(tournamentId).toTournamentUploadResult()
    }

    suspend fun loadAvailableAction(): TournamentCloudRestorationResult =
        restorationDelegate.loadAvailable()

    suspend fun restoreAction(
        tournamentId: String,
    ): QueueAwareActionResult<TournamentCloudRestorationResult> {
        val owner = currentOwnerUserId() ?: return restorationDelegate.restore(tournamentId).also {
            debugRestore("ROUTE_LEGACY_AUTH_REQUIRED")
            debugRestore("ROUTE_RESULT_${it.primaryResult.diagnosticCode()}")
        }
        if (!devV4Enabled) return restorationDelegate.restore(tournamentId).also {
            debugRestore("ROUTE_LEGACY_V4_DISABLED")
            debugRestore("ROUTE_RESULT_${it.primaryResult.diagnosticCode()}")
        }
        return when (val selection = selectRestorationEngine(tournamentId, owner)) {
            RestorationEngine.LEGACY ->
                restorationDelegate.restore(tournamentId).also {
                    debugRestore("ROUTE_LEGACY_SELECTED")
                    debugRestore("ROUTE_RESULT_${it.primaryResult.diagnosticCode()}")
                }
            is RestorationEngine.UUID_BACKED -> selection.result.toTournamentRestorationResult()
                .withQueue(owner, SyncQueueOperationType.TOURNAMENT_RESTORATION, tournamentId)
                .also {
                    debugRestore("ROUTE_UUID_SELECTED")
                    debugRestore("ROUTE_RESULT_${it.primaryResult.diagnosticCode()}")
                }
            is RestorationEngine.FAILURE -> selection.result
                .withQueue(owner, SyncQueueOperationType.TOURNAMENT_RESTORATION, tournamentId)
                .also { debugRestore("ROUTE_RESULT_${it.primaryResult.diagnosticCode()}") }
        }
    }

    suspend fun restorationRetry(tournamentId: String): TournamentCloudRestorationResult =
        currentOwnerUserId()?.let { restorationRetryForOwner(tournamentId, it) }
            ?: TournamentCloudRestorationResult.AuthenticationRequired

    suspend fun restorationRetryForOwner(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): TournamentCloudRestorationResult {
        if (currentOwnerUserId() != expectedOwnerUserId) {
            return TournamentCloudRestorationResult.AuthorizationFailure
        }
        if (!devV4Enabled) {
            return restorationDelegate.executeForRetry(tournamentId, expectedOwnerUserId).also {
                debugRestore("ROUTE_RETRY_LEGACY_V4_DISABLED")
                debugRestore("ROUTE_RETRY_RESULT_${it.diagnosticCode()}")
            }
        }
        return when (val selection = selectRestorationEngine(tournamentId, expectedOwnerUserId)) {
            RestorationEngine.LEGACY ->
                restorationDelegate.executeForRetry(tournamentId, expectedOwnerUserId).also {
                    debugRestore("ROUTE_RETRY_LEGACY_SELECTED")
                    debugRestore("ROUTE_RETRY_RESULT_${it.diagnosticCode()}")
                }
            is RestorationEngine.UUID_BACKED -> selection.result.toTournamentRestorationResult().also {
                debugRestore("ROUTE_RETRY_UUID_SELECTED")
                debugRestore("ROUTE_RETRY_RESULT_${it.diagnosticCode()}")
            }
            is RestorationEngine.FAILURE -> selection.result.also {
                debugRestore("ROUTE_RETRY_RESULT_${it.diagnosticCode()}")
            }
        }
    }

    /** One authoritative cloud classification is shared by normal restore and retry. */
    private suspend fun selectRestorationEngine(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): RestorationEngine {
        val engine = try {
            when (val parent = restorationCloudRepository.readOwnedTournament(tournamentId)) {
                is TournamentCloudRestorationRemoteResult.Failure ->
                    RestorationEngine.FAILURE(parent.category.toRestorationResult())
                is TournamentCloudRestorationRemoteResult.Success -> {
                    val tournament = parent.value.tournament
                    if (tournament.id != tournamentId || tournament.ownerUserId != expectedOwnerUserId) {
                        RestorationEngine.FAILURE(TournamentCloudRestorationResult.AuthorizationFailure)
                    } else if (tournament.format != TournamentFormat.GROUP_ROTATION) {
                        RestorationEngine.LEGACY
                    } else {
                        when (val result = uuidRestoration(tournamentId)) {
                            GroupRotationUuidCloudRestorationResult.NotUuidBacked ->
                                RestorationEngine.LEGACY
                            else -> RestorationEngine.UUID_BACKED(result)
                        }
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            RestorationEngine.FAILURE(TournamentCloudRestorationResult.NetworkFailure)
        }
        debugRestore("ROUTE_ENGINE_${engine.diagnosticCode()}")
        return engine
    }

    private sealed interface RestorationEngine {
        data object LEGACY : RestorationEngine
        data class UUID_BACKED(
            val result: GroupRotationUuidCloudRestorationResult,
        ) : RestorationEngine

        data class FAILURE(
            val result: TournamentCloudRestorationResult,
        ) : RestorationEngine
    }

    suspend fun draftAction(
        tournamentId: String,
    ): QueueAwareActionResult<DraftMatchCloudSyncResult> {
        val owner = currentOwnerUserId() ?: return draftDelegate(tournamentId)
        if (!shouldUseV4(tournamentId, owner)) return draftDelegate(tournamentId)
        val result = uploadCoordinator.upload(tournamentId).toDraftSyncResult()
        return result.withQueue(owner, SyncQueueOperationType.DRAFT_MATCH_SYNC, tournamentId)
    }

    suspend fun draftActionForOwner(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): QueueAwareActionResult<DraftMatchCloudSyncResult> {
        if (!shouldUseV4(tournamentId, expectedOwnerUserId)) {
            return draftDelegate(tournamentId, expectedOwnerUserId)
        }
        val result = uploadCoordinator.upload(tournamentId).toDraftSyncResult()
        return result.withQueue(expectedOwnerUserId, SyncQueueOperationType.DRAFT_MATCH_SYNC, tournamentId)
    }

    suspend fun draftRetry(tournamentId: String): DraftMatchCloudSyncResult =
        currentOwnerUserId()?.let { draftRetryForOwner(tournamentId, it) }
            ?: DraftMatchCloudSyncResult.AuthenticationRequired

    suspend fun draftRetryForOwner(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): DraftMatchCloudSyncResult = if (shouldUseV4(tournamentId, expectedOwnerUserId)) {
        uploadCoordinator.upload(tournamentId).toDraftSyncResult()
    } else {
        draftDelegate.executeForRetry(tournamentId, expectedOwnerUserId)
    }

    suspend fun finalizedAction(
        tournamentId: String,
    ): QueueAwareActionResult<FinalizedMatchCloudSyncResult> {
        val owner = currentOwnerUserId() ?: return finalizedDelegate(tournamentId)
        if (!shouldUseV4(tournamentId, owner)) return finalizedDelegate(tournamentId)
        val result = uploadCoordinator.upload(tournamentId).toFinalizedSyncResult()
        return result.withQueue(owner, SyncQueueOperationType.FINALIZED_MATCH_SYNC, tournamentId)
    }

    suspend fun finalizedRetry(tournamentId: String): FinalizedMatchCloudSyncResult =
        currentOwnerUserId()?.let { finalizedRetryForOwner(tournamentId, it) }
            ?: FinalizedMatchCloudSyncResult.AuthenticationRequired

    suspend fun finalizedRetryForOwner(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): FinalizedMatchCloudSyncResult = if (shouldUseV4(tournamentId, expectedOwnerUserId)) {
        uploadCoordinator.upload(tournamentId).toFinalizedSyncResult()
    } else {
        finalizedDelegate.executeForRetry(tournamentId, expectedOwnerUserId)
    }

    private suspend fun shouldUseV4(
        tournamentId: String,
        ownerUserId: String,
    ): Boolean {
        if (!devV4Enabled) return false
        val tournament = tournamentRepository.observeByIdAndOwner(tournamentId, ownerUserId).first()
            ?: return false
        if (tournament.format != TournamentFormat.GROUP_ROTATION) return false
        return when (val result = teamIdentityRepository.read(tournament)) {
            GroupRotationTeamIdentityReadResult.Unsupported -> false
            is GroupRotationTeamIdentityReadResult.Invalid -> true
            is GroupRotationTeamIdentityReadResult.Loaded ->
                result.snapshot.teams.any { identity -> !identity.isLegacyIdentity(tournament) }
        }
    }

    private fun GroupRotationTeamIdentity.isLegacyIdentity(tournament: Tournament): Boolean {
        val tournamentUuid = runCatching { UUID.fromString(tournament.id) }.getOrNull() ?: return false
        val capacity = when (tournament.groupCount) {
            3 -> 18
            4 -> 24
            else -> return false
        }
        return (1..capacity).any { slotNumber ->
            TournamentCloudIdentity.teamSlotId(tournamentUuid, slotNumber) == teamUUID
        }
    }

    private suspend fun currentOwnerUserId(): String? = try {
        (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun <T> T.withQueue(
        ownerUserId: String,
        operation: SyncQueueOperationType,
        tournamentId: String,
    ): QueueAwareActionResult<T> {
        if (!authRepository.isSignedInAs(ownerUserId)) {
            return QueueAwareActionResult(this, QueueRecordingResult.NOT_REQUIRED)
        }
        val status = when (this) {
            is TournamentCloudUploadResult -> this.queueStatus()
            is TournamentCloudRestorationResult -> this.queueStatus()
            is DraftMatchCloudSyncResult -> this.queueStatus()
            is FinalizedMatchCloudSyncResult -> this.queueStatus()
            else -> SyncQueueStatus.FAILED_UNKNOWN
        }
        val category = when (this) {
            is TournamentCloudUploadResult.Conflict -> conflict.queueFailureCategory()
            is TournamentCloudRestorationResult.Conflict -> conflict.queueFailureCategory()
            is DraftMatchCloudSyncResult.Conflict -> conflict.queueFailureCategory()
            is FinalizedMatchCloudSyncResult.Conflict -> conflict.queueFailureCategory()
            else -> status.name
        }
        return QueueAwareActionResult(
            primaryResult = this,
            queueRecordingResult = queueRecorder.record(
                ownerUserId = ownerUserId,
                operation = operation,
                tournamentId = tournamentId,
                status = status,
                failureCategory = category,
            ),
        )
    }

    private fun GroupRotationV4CloudUploadResult.toTournamentUploadResult() = when (this) {
        is GroupRotationV4CloudUploadResult.Success -> TournamentCloudUploadResult.Success(confirmedCloudRevision)
        GroupRotationV4CloudUploadResult.AuthenticationRequired -> TournamentCloudUploadResult.AuthenticationRequired
        GroupRotationV4CloudUploadResult.AuthorizationFailure -> TournamentCloudUploadResult.AuthorizationFailure
        GroupRotationV4CloudUploadResult.ValidationFailure,
        GroupRotationV4CloudUploadResult.DeletionBlocked,
        -> TournamentCloudUploadResult.ValidationFailure
        GroupRotationV4CloudUploadResult.NetworkFailure -> TournamentCloudUploadResult.NetworkFailure
        is GroupRotationV4CloudUploadResult.Conflict -> TournamentCloudUploadResult.Conflict(conflict)
    }

    private fun GroupRotationV4CloudUploadResult.toDraftSyncResult() = when (this) {
        is GroupRotationV4CloudUploadResult.Success -> DraftMatchCloudSyncResult.Success
        GroupRotationV4CloudUploadResult.AuthenticationRequired -> DraftMatchCloudSyncResult.AuthenticationRequired
        GroupRotationV4CloudUploadResult.AuthorizationFailure -> DraftMatchCloudSyncResult.AuthorizationFailure
        GroupRotationV4CloudUploadResult.ValidationFailure,
        GroupRotationV4CloudUploadResult.DeletionBlocked,
        -> DraftMatchCloudSyncResult.ValidationFailure
        GroupRotationV4CloudUploadResult.NetworkFailure -> DraftMatchCloudSyncResult.NetworkFailure
        is GroupRotationV4CloudUploadResult.Conflict -> DraftMatchCloudSyncResult.Conflict(conflict)
    }

    private fun GroupRotationV4CloudUploadResult.toFinalizedSyncResult() = when (this) {
        is GroupRotationV4CloudUploadResult.Success -> FinalizedMatchCloudSyncResult.Success(confirmedCloudRevision)
        GroupRotationV4CloudUploadResult.AuthenticationRequired -> FinalizedMatchCloudSyncResult.AuthenticationRequired
        GroupRotationV4CloudUploadResult.AuthorizationFailure -> FinalizedMatchCloudSyncResult.AuthorizationFailure
        GroupRotationV4CloudUploadResult.ValidationFailure,
        GroupRotationV4CloudUploadResult.DeletionBlocked,
        -> FinalizedMatchCloudSyncResult.ValidationFailure
        GroupRotationV4CloudUploadResult.NetworkFailure -> FinalizedMatchCloudSyncResult.NetworkFailure
        is GroupRotationV4CloudUploadResult.Conflict -> FinalizedMatchCloudSyncResult.Conflict(conflict)
    }

    private fun GroupRotationUuidCloudRestorationResult.toTournamentRestorationResult() = when (this) {
        is GroupRotationUuidCloudRestorationResult.Restored ->
            TournamentCloudRestorationResult.Success(tournamentName)
        GroupRotationUuidCloudRestorationResult.AuthenticationRequired ->
            TournamentCloudRestorationResult.AuthenticationRequired
        GroupRotationUuidCloudRestorationResult.AuthorizationFailure ->
            TournamentCloudRestorationResult.AuthorizationFailure
        GroupRotationUuidCloudRestorationResult.NotFound ->
            TournamentCloudRestorationResult.AuthorizationFailure
        GroupRotationUuidCloudRestorationResult.ValidationFailure ->
            TournamentCloudRestorationResult.ValidationFailure
        GroupRotationUuidCloudRestorationResult.NetworkFailure ->
            TournamentCloudRestorationResult.NetworkFailure
        GroupRotationUuidCloudRestorationResult.Conflict ->
            TournamentCloudRestorationResult.Conflict(RevisionConflict.MissingRevision)
        GroupRotationUuidCloudRestorationResult.DeletionBlocked ->
            TournamentCloudRestorationResult.ValidationFailure
        GroupRotationUuidCloudRestorationResult.LocalTransactionFailure ->
            TournamentCloudRestorationResult.LocalTransactionFailure
        GroupRotationUuidCloudRestorationResult.NotUuidBacked ->
            error("NotUuidBacked is handled before mapping.")
    }

    private fun TournamentCloudRestorationFailureCategory.toRestorationResult() = when (this) {
        TournamentCloudRestorationFailureCategory.AUTHENTICATION ->
            TournamentCloudRestorationResult.AuthenticationRequired
        TournamentCloudRestorationFailureCategory.AUTHORIZATION,
        TournamentCloudRestorationFailureCategory.NOT_FOUND,
        -> TournamentCloudRestorationResult.AuthorizationFailure
        TournamentCloudRestorationFailureCategory.NETWORK ->
            TournamentCloudRestorationResult.NetworkFailure
        TournamentCloudRestorationFailureCategory.VALIDATION ->
            TournamentCloudRestorationResult.ValidationFailure
    }

    private fun debugRestore(code: String) {
        if (BuildConfig.DEBUG) Log.d(RESTORE_LOG_TAG, code)
    }

    private fun RestorationEngine.diagnosticCode() = when (this) {
        RestorationEngine.LEGACY -> "LEGACY"
        is RestorationEngine.UUID_BACKED -> "UUID_BACKED"
        is RestorationEngine.FAILURE -> "FAILURE_${result.diagnosticCode()}"
    }

    private fun TournamentCloudRestorationResult.diagnosticCode() = when (this) {
        is TournamentCloudRestorationResult.Available -> "AVAILABLE"
        is TournamentCloudRestorationResult.Success -> "SUCCESS"
        TournamentCloudRestorationResult.AuthenticationRequired -> "AUTHENTICATION_REQUIRED"
        TournamentCloudRestorationResult.AuthorizationFailure -> "AUTHORIZATION_FAILURE"
        TournamentCloudRestorationResult.ValidationFailure -> "VALIDATION_FAILURE"
        TournamentCloudRestorationResult.NetworkFailure -> "NETWORK_FAILURE"
        is TournamentCloudRestorationResult.Conflict -> "CONFLICT"
        TournamentCloudRestorationResult.LocalTransactionFailure -> "LOCAL_TRANSACTION_FAILURE"
    }

    private fun TournamentCloudUploadResult.queueStatus() = when (this) {
        is TournamentCloudUploadResult.Success -> SyncQueueStatus.COMPLETED
        TournamentCloudUploadResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION
        TournamentCloudUploadResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK
        TournamentCloudUploadResult.TournamentLimitReached,
        TournamentCloudUploadResult.ValidationFailure,
        -> SyncQueueStatus.FAILED_VALIDATION
        TournamentCloudUploadResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION
        is TournamentCloudUploadResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT
        is TournamentCloudUploadResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN
    }

    private fun TournamentCloudRestorationResult.queueStatus() = when (this) {
        is TournamentCloudRestorationResult.Success,
        is TournamentCloudRestorationResult.Available,
        -> SyncQueueStatus.COMPLETED
        TournamentCloudRestorationResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION
        TournamentCloudRestorationResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK
        TournamentCloudRestorationResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION
        TournamentCloudRestorationResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION
        TournamentCloudRestorationResult.LocalTransactionFailure -> SyncQueueStatus.FAILED_LOCAL
        is TournamentCloudRestorationResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT
    }

    private fun DraftMatchCloudSyncResult.queueStatus() = when (this) {
        DraftMatchCloudSyncResult.Success -> SyncQueueStatus.COMPLETED
        DraftMatchCloudSyncResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION
        DraftMatchCloudSyncResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK
        DraftMatchCloudSyncResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION
        DraftMatchCloudSyncResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION
        is DraftMatchCloudSyncResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT
        is DraftMatchCloudSyncResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN
    }

    private fun FinalizedMatchCloudSyncResult.queueStatus() = when (this) {
        is FinalizedMatchCloudSyncResult.Success -> SyncQueueStatus.COMPLETED
        FinalizedMatchCloudSyncResult.AuthenticationRequired -> SyncQueueStatus.BLOCKED_AUTHENTICATION
        FinalizedMatchCloudSyncResult.NetworkFailure -> SyncQueueStatus.BLOCKED_NETWORK
        FinalizedMatchCloudSyncResult.ValidationFailure -> SyncQueueStatus.FAILED_VALIDATION
        FinalizedMatchCloudSyncResult.AuthorizationFailure -> SyncQueueStatus.FAILED_AUTHORIZATION
        is FinalizedMatchCloudSyncResult.Conflict -> SyncQueueStatus.FAILED_CONFLICT
        is FinalizedMatchCloudSyncResult.PartialFailure -> SyncQueueStatus.FAILED_UNKNOWN
    }

    companion object {
        private const val RESTORE_LOG_TAG = "POINTIQ_V4_RESTORE"
        const val DEV_V4_ENABLED = "pointiq_dev_v4_enabled"
    }
}

@Singleton
class GroupRotationDevTournamentCloudUploadAction @Inject constructor(
    private val route: GroupRotationDevCloudRoute,
) : TournamentCloudUploadAction, TournamentCloudUploadRetryAction {
    override suspend fun invoke(tournamentId: String): QueueAwareActionResult<TournamentCloudUploadResult> =
        route.uploadAction(tournamentId)

    override suspend fun executeForRetry(tournamentId: String): TournamentCloudUploadResult =
        route.uploadRetry(tournamentId)

    override suspend fun executeForRetry(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): TournamentCloudUploadResult = route.uploadRetryForOwner(tournamentId, expectedOwnerUserId)
}

@Singleton
class GroupRotationDevTournamentCloudRestorationAction @Inject constructor(
    private val route: GroupRotationDevCloudRoute,
) : TournamentCloudRestorationAction, TournamentCloudRestorationRetryAction {
    override suspend fun loadAvailable(): TournamentCloudRestorationResult =
        route.loadAvailableAction()

    override suspend fun restore(
        tournamentId: String,
    ): QueueAwareActionResult<TournamentCloudRestorationResult> = route.restoreAction(tournamentId)

    override suspend fun executeForRetry(tournamentId: String): TournamentCloudRestorationResult =
        route.restorationRetry(tournamentId)

    override suspend fun executeForRetry(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): TournamentCloudRestorationResult = route.restorationRetryForOwner(tournamentId, expectedOwnerUserId)
}

@Singleton
class GroupRotationDevDraftMatchCloudSyncAction @Inject constructor(
    private val route: GroupRotationDevCloudRoute,
) : DraftMatchCloudSyncAction, DraftMatchCloudSyncRetryAction {
    override suspend fun invoke(tournamentId: String): QueueAwareActionResult<DraftMatchCloudSyncResult> =
        route.draftAction(tournamentId)

    override suspend fun invoke(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): QueueAwareActionResult<DraftMatchCloudSyncResult> =
        route.draftActionForOwner(tournamentId, expectedOwnerUserId)

    override suspend fun executeForRetry(tournamentId: String): DraftMatchCloudSyncResult =
        route.draftRetry(tournamentId)

    override suspend fun executeForRetry(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): DraftMatchCloudSyncResult = route.draftRetryForOwner(tournamentId, expectedOwnerUserId)
}

@Singleton
class GroupRotationDevFinalizedMatchCloudSyncAction @Inject constructor(
    private val route: GroupRotationDevCloudRoute,
) : FinalizedMatchCloudSyncAction, FinalizedMatchCloudSyncRetryAction {
    override suspend fun invoke(tournamentId: String): QueueAwareActionResult<FinalizedMatchCloudSyncResult> =
        route.finalizedAction(tournamentId)

    override suspend fun executeForRetry(tournamentId: String): FinalizedMatchCloudSyncResult =
        route.finalizedRetry(tournamentId)

    override suspend fun executeForRetry(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): FinalizedMatchCloudSyncResult = route.finalizedRetryForOwner(tournamentId, expectedOwnerUserId)
}

@dagger.Module
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
object GroupRotationDevCloudRoutingModule {
    @dagger.Provides
    @javax.inject.Singleton
    @javax.inject.Named(GroupRotationV4CloudUploadCoordinator.REQUEST_ID_GENERATOR)
    fun provideGroupRotationV4RequestIdGenerator(): () -> String = {
        java.util.UUID.randomUUID().toString()
    }

    @dagger.Provides
    @javax.inject.Singleton
    @javax.inject.Named(GroupRotationDevCloudRoute.DEV_V4_ENABLED)
    fun provideDevV4Enabled(): Boolean = BuildConfig.DEBUG
}
