package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class SyncDraftMatchesUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cloudSyncRepository: DraftMatchCloudSyncRepository,
) : DraftMatchCloudSyncAction {
    override suspend operator fun invoke(tournamentId: String): DraftMatchCloudSyncResult {
        val authenticated = try {
            authRepository.observeAuthState().first() is AuthState.SignedIn
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            false
        }
        if (!authenticated) return DraftMatchCloudSyncResult.AuthenticationRequired

        val snapshot = try {
            val tournament = tournamentRepository.observeById(tournamentId).first()
                ?: return DraftMatchCloudSyncResult.ValidationFailure
            DraftMatchCloudSyncSnapshot(
                tournament = tournament,
                matches = tournamentRepository.observeMatchesByTournamentId(tournamentId).first(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return DraftMatchCloudSyncResult.ValidationFailure
        }

        return cloudSyncRepository.sync(snapshot)
    }
}
