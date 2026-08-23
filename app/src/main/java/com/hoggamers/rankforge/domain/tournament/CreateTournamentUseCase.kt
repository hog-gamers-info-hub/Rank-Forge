package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CancellationException
import java.util.UUID
import kotlinx.coroutines.flow.first

data class CreateTournamentInput(
    val name: String,
    val date: LocalDate?,
    val organizerName: String,
    val organizerContactNumber: String,
    val status: TournamentStatus = TournamentStatus.DRAFT,
)

enum class TournamentField {
    NAME,
    DATE,
    ORGANIZER_NAME,
    ORGANIZER_CONTACT_NUMBER,
    STATUS,
}

enum class TournamentValidationError {
    REQUIRED,
    PAST_DATE,
    UNSUPPORTED_STATUS,
}

fun validateCreateTournamentInput(
    input: CreateTournamentInput,
    clock: Clock,
): Map<TournamentField, TournamentValidationError> = buildMap {
    if (input.name.isBlank()) {
        put(TournamentField.NAME, TournamentValidationError.REQUIRED)
    }
    if (input.date == null) {
        put(TournamentField.DATE, TournamentValidationError.REQUIRED)
    } else if (input.date.isBefore(LocalDate.now(clock))) {
        put(TournamentField.DATE, TournamentValidationError.PAST_DATE)
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
    private val clock: Clock,
) {
    suspend operator fun invoke(input: CreateTournamentInput): CreateTournamentResult {
        val errors = validateCreateTournamentInput(input, clock)
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
            date = input.date!!,
            organizerName = input.organizerName.trim(),
            organizerContactNumber = input.organizerContactNumber.trim(),
            status = TournamentStatus.DRAFT,
            ownerUserId = ownerUserId,
        )
        repository.create(tournament)
        return CreateTournamentResult.Created(tournament)
    }
}
