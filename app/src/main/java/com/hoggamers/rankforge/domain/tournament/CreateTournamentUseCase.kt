package com.hoggamers.rankforge.domain.tournament

import java.time.Clock
import java.time.LocalDate
import java.util.UUID

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

sealed interface CreateTournamentResult {
    data class Created(val tournament: Tournament) : CreateTournamentResult

    data class Invalid(
        val errors: Map<TournamentField, TournamentValidationError>,
    ) : CreateTournamentResult
}

class CreateTournamentUseCase(
    private val repository: TournamentRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(input: CreateTournamentInput): CreateTournamentResult {
        val errors = buildMap {
            if (input.name.isBlank()) {
                put(TournamentField.NAME, TournamentValidationError.REQUIRED)
            }
            if (input.date == null) {
                put(TournamentField.DATE, TournamentValidationError.REQUIRED)
            } else if (input.date.isBefore(LocalDate.now(clock))) {
                put(TournamentField.DATE, TournamentValidationError.PAST_DATE)
            }
            if (input.organizerName.isBlank()) {
                put(TournamentField.ORGANIZER_NAME, TournamentValidationError.REQUIRED)
            }
            if (input.organizerContactNumber.isBlank()) {
                put(TournamentField.ORGANIZER_CONTACT_NUMBER, TournamentValidationError.REQUIRED)
            }
            if (input.status != TournamentStatus.DRAFT) {
                put(TournamentField.STATUS, TournamentValidationError.UNSUPPORTED_STATUS)
            }
        }

        if (errors.isNotEmpty()) {
            return CreateTournamentResult.Invalid(errors)
        }

        val tournament = Tournament(
            id = UUID.randomUUID().toString(),
            name = input.name.trim(),
            date = input.date!!,
            organizerName = input.organizerName.trim(),
            organizerContactNumber = input.organizerContactNumber.trim(),
            status = TournamentStatus.DRAFT,
        )
        repository.create(tournament)
        return CreateTournamentResult.Created(tournament)
    }
}
