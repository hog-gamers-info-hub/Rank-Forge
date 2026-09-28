package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import java.util.UUID
import kotlinx.coroutines.flow.first

data class CreateTournamentInput(
    val name: String,
    val stageName: String,
    val organizerContactNumber: String,
    val status: TournamentStatus = TournamentStatus.DRAFT,
    val organizationName: String = "",
)

enum class TournamentField {
    NAME,
    STAGE_NAME,
    ORGANIZER_CONTACT_NUMBER,
    STATUS,
}

enum class TournamentValidationError {
    REQUIRED,
    UNSUPPORTED_STATUS,
}

fun validateCreateTournamentInput(
    input: CreateTournamentInput,
): Map<TournamentField, TournamentValidationError> = buildMap {
    if (input.name.isBlank()) {
        put(TournamentField.NAME, TournamentValidationError.REQUIRED)
    }
    if (input.status != TournamentStatus.DRAFT) {
        put(TournamentField.STATUS, TournamentValidationError.UNSUPPORTED_STATUS)
    }
}

sealed interface CreateTournamentResult {
    data class Created(val tournament: Tournament) : CreateTournamentResult

    data object AuthenticationRequired : CreateTournamentResult

    data class Invalid(
        val errors: Map<TournamentField, TournamentValidationError>,
    ) : CreateTournamentResult
}

class CreateTournamentUseCase(
    private val repository: TournamentRepository,
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(input: CreateTournamentInput): CreateTournamentResult {
        val errors = validateCreateTournamentInput(input)
        if (errors.isNotEmpty()) {
            return CreateTournamentResult.Invalid(errors)
        }

        val ownerUserId = try {
            (authRepository.observeAuthState().first() as? AuthState.SignedIn)
                ?.user
                ?.id
                ?.takeIf { it.isNotBlank() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } ?: return CreateTournamentResult.AuthenticationRequired

        val tournament = Tournament(
            id = UUID.randomUUID().toString(),
            name = input.name.trim(),
            stageName = input.stageName.trim(),
            organizerContactNumber = input.organizerContactNumber.trim(),
            status = TournamentStatus.DRAFT,
            ownerUserId = ownerUserId,
            organizationName = input.organizationName.trim().takeIf { it.isNotEmpty() },
        )
        repository.create(tournament)
        return CreateTournamentResult.Created(tournament)
    }
}
