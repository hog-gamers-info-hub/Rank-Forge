package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class RecoverPendingLocalDeletionCleanupUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val deletionIntentRepository: DeletionIntentRepository,
    private val localDeletionRepository: LocalDeletionRepository,
) {
    suspend operator fun invoke(ownerUserId: String) {
        if (ownerUserId.isBlank() || currentOwnerUserId() != ownerUserId) return
        val intents = deletionIntentRepository.readPendingLocalCleanupByOwner(ownerUserId)
        for (intent in intents) {
            if (currentOwnerUserId() != ownerUserId) return
            val result = when (intent.targetType) {
                DeletionTargetType.MATCH ->
                    localDeletionRepository.deleteMatchLocallyByOwner(intent.targetId, ownerUserId)
                DeletionTargetType.TOURNAMENT ->
                    localDeletionRepository.deleteTournamentLocallyByOwner(intent.targetId, ownerUserId)
            }
            if (result == LocalDeletionResult.Deleted || result == LocalDeletionResult.NotFound) {
                if (currentOwnerUserId() != ownerUserId) return
                deletionIntentRepository.clearByTargetAndOwner(
                    intent.targetType,
                    intent.targetId,
                    ownerUserId,
                )
            }
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
}
