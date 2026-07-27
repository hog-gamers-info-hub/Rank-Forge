package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class UploadTournamentUseCase @Inject constructor(
    private val tournamentRepository: TournamentRepository,
    private val authRepository: AuthRepository,
    private val cloudUploadRepository: TournamentCloudUploadRepository,
) : TournamentCloudUploadAction {
    override suspend operator fun invoke(tournamentId: String): TournamentCloudUploadResult {
        val authState = try {
            authRepository.observeAuthState().first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return TournamentCloudUploadResult.AuthenticationRequired
        }

        val ownerId = (authState as? AuthState.SignedIn)?.user?.id
            ?.takeIf { it.isNotBlank() }
            ?: return TournamentCloudUploadResult.AuthenticationRequired

        val snapshot = try {
            val tournament = tournamentRepository.observeById(tournamentId).first()
                ?: return TournamentCloudUploadResult.ValidationFailure
            TournamentCloudUploadSnapshot(
                tournament = tournament,
                slots = tournamentRepository.observeSlotsByTournamentId(tournamentId).first(),
                rosters = tournamentRepository.observeRosterByTournamentId(tournamentId).first(),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return TournamentCloudUploadResult.ValidationFailure
        }

        return cloudUploadRepository.upload(snapshot, ownerId)
    }
}
