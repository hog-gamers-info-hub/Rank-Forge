package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** The existing draft table is used with lobby positions only for UUID-backed matches. */
data class GroupRotationMatchDraftState(
    val snapshot: GroupRotationMatchIdentitySnapshot,
    val participantResults: List<GroupRotationMatchParticipantResult>,
    val valuesByLobbySlot: Map<Int, MatchDraftFieldValues>,
)

sealed interface GroupRotationMatchDraftReadResult {
    data class Loaded(val state: GroupRotationMatchDraftState) : GroupRotationMatchDraftReadResult
    data object AuthenticationRequired : GroupRotationMatchDraftReadResult
    data object MatchNotFound : GroupRotationMatchDraftReadResult
    data object NotUuidBacked : GroupRotationMatchDraftReadResult
    data object InvalidData : GroupRotationMatchDraftReadResult
}

enum class GroupRotationMatchDraftMutationResult {
    SAVED,
    AUTHENTICATION_REQUIRED,
    MATCH_NOT_FOUND,
    INVALID_DATA,
}

interface GroupRotationMatchDraftRepository {
    fun observeByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
    ): Flow<GroupRotationMatchDraftReadResult>

    suspend fun saveValueByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        lobbySlotNumber: Int,
        placementInput: String? = null,
        killsInput: String? = null,
        pointAdjustment: Int? = null,
    ): GroupRotationMatchDraftMutationResult

    suspend fun savePlacementsByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        placementsByLobbySlot: Map<Int, String>,
    ): GroupRotationMatchDraftMutationResult

    suspend fun saveKillsByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        killsByLobbySlot: Map<Int, String>,
    ): GroupRotationMatchDraftMutationResult

    suspend fun saveParticipantResultsByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        participantResults: List<GroupRotationMatchParticipantResult>,
    ): GroupRotationMatchDraftMutationResult

    suspend fun savePointAdjustmentByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        lobbySlotNumber: Int,
        pointAdjustment: Int,
    ): GroupRotationMatchDraftMutationResult

    suspend fun clearByOwner(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
    ): GroupRotationMatchDraftMutationResult
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ObserveGroupRotationMatchDraftUseCase @Inject constructor(
    private val repository: GroupRotationMatchDraftRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationMatchDraftRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    operator fun invoke(
        tournamentId: String,
        matchId: String,
    ): Flow<GroupRotationMatchDraftReadResult> = authRepository.observeAuthState().flatMapLatest { state ->
        val ownerUserId = (state as? AuthState.SignedIn)?.user?.id
            ?.takeIf { it.isNotBlank() }
        if (ownerUserId == null) {
            flowOf(GroupRotationMatchDraftReadResult.AuthenticationRequired)
        } else {
            repository.observeByOwner(tournamentId, matchId, ownerUserId)
        }
    }
}

class SaveGroupRotationMatchDraftValueUseCase @Inject constructor(
    private val repository: GroupRotationMatchDraftRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationMatchDraftRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    suspend operator fun invoke(input: SaveMatchDraftValueInput): GroupRotationMatchDraftMutationResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED
        return repository.saveValueByOwner(
            tournamentId = input.tournamentId,
            matchId = input.matchId,
            ownerUserId = ownerUserId,
            lobbySlotNumber = input.teamSlotNumber,
            placementInput = input.placementInput,
            killsInput = input.killsInput,
            pointAdjustment = input.pointAdjustment,
        )
    }
}

class SaveGroupRotationMatchPlacementsUseCase @Inject constructor(
    private val repository: GroupRotationMatchDraftRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationMatchDraftRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    suspend operator fun invoke(
        tournamentId: String,
        matchId: String,
        placementsByLobbySlot: Map<Int, String>,
    ): GroupRotationMatchDraftMutationResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED
        return repository.savePlacementsByOwner(
            tournamentId,
            matchId,
            ownerUserId,
            placementsByLobbySlot,
        )
    }
}

class SaveGroupRotationMatchKillsUseCase @Inject constructor(
    private val repository: GroupRotationMatchDraftRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationMatchDraftRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    suspend operator fun invoke(
        tournamentId: String,
        matchId: String,
        killsByLobbySlot: Map<Int, String>,
    ): GroupRotationMatchDraftMutationResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED
        return repository.saveKillsByOwner(
            tournamentId,
            matchId,
            ownerUserId,
            killsByLobbySlot,
        )
    }
}

class SaveGroupRotationMatchParticipantResultsUseCase @Inject constructor(
    private val repository: GroupRotationMatchDraftRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationMatchDraftRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    suspend operator fun invoke(
        tournamentId: String,
        matchId: String,
        participantResults: List<GroupRotationMatchParticipantResult>,
    ): GroupRotationMatchDraftMutationResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED
        return repository.saveParticipantResultsByOwner(
            tournamentId = tournamentId,
            matchId = matchId,
            ownerUserId = ownerUserId,
            participantResults = participantResults,
        )
    }
}

class SaveGroupRotationMatchPointAdjustmentUseCase @Inject constructor(
    private val repository: GroupRotationMatchDraftRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationMatchDraftRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    suspend operator fun invoke(
        tournamentId: String,
        matchId: String,
        lobbySlotNumber: Int,
        pointAdjustment: Int,
    ): GroupRotationMatchDraftMutationResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED
        return repository.savePointAdjustmentByOwner(
            tournamentId,
            matchId,
            ownerUserId,
            lobbySlotNumber,
            pointAdjustment,
        )
    }
}

class ClearGroupRotationMatchDraftUseCase @Inject constructor(
    private val repository: GroupRotationMatchDraftRepository,
    private val authRepository: AuthRepository,
) {
    constructor(repository: GroupRotationMatchDraftRepository) : this(
        repository,
        SetupMutationUnauthenticatedAuthRepository,
    )

    suspend operator fun invoke(
        tournamentId: String,
        matchId: String,
    ): GroupRotationMatchDraftMutationResult {
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return GroupRotationMatchDraftMutationResult.AUTHENTICATION_REQUIRED
        return repository.clearByOwner(tournamentId, matchId, ownerUserId)
    }
}
