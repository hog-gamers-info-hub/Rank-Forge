package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Claims only legacy local rows whose exact stable ID is visible to the expected owner through RLS.
 */
class ReconcileLegacyTournamentOwnershipUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val tournamentRepository: TournamentRepository,
    private val cloudRepository: TournamentCloudRestorationRepository,
) {
    suspend operator fun invoke(expectedOwnerUserId: String) {
        if (!isStillExpectedOwner(expectedOwnerUserId)) return

        for (localTournament in tournamentRepository.readOwnerlessLegacyTournaments()) {
            if (!isStillExpectedOwner(expectedOwnerUserId)) return

            val proof = try {
                cloudRepository.readOwnedTournament(localTournament.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                continue
            }
            val cloudTournament = (proof as? TournamentCloudRestorationRemoteResult.Success)
                ?.value
                ?.tournament
                ?: continue
            if (
                cloudTournament.id != localTournament.id ||
                cloudTournament.ownerUserId.isNullOrBlank() ||
                cloudTournament.ownerUserId != expectedOwnerUserId ||
                !isStillExpectedOwner(expectedOwnerUserId)
            ) {
                continue
            }
            tournamentRepository.assignLegacyTournamentOwnerIfUnassigned(
                tournamentId = localTournament.id,
                provenOwnerUserId = expectedOwnerUserId,
            )
        }
    }

    private suspend fun isStillExpectedOwner(expectedOwnerUserId: String): Boolean =
        expectedOwnerUserId.isNotBlank() &&
            ((authRepository.observeAuthState().first() as? AuthState.SignedIn)
                ?.user?.id == expectedOwnerUserId)
}
