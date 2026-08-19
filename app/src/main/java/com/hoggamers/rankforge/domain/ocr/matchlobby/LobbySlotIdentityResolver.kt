package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotCandidate
import javax.inject.Inject

enum class LobbySlotIdentitySource {
    OCR_DIRECT,
    GROUP_INFERRED,
}

data class LobbyResolvedSlotIdentity(
    val visibleSlotPosition: RosterVisibleSlotPosition,
    val tournamentSlotNumber: Int,
    val source: LobbySlotIdentitySource,
)

data class LobbyResolvedSlotGroup(
    val tournamentSlotRange: IntRange,
    val slots: List<LobbyResolvedSlotIdentity>,
    val directlyDetectedCount: Int,
)

enum class LobbySlotIdentityResolutionFailure {
    NO_USABLE_SLOT_NUMBER,
    POSITION_INCOMPATIBLE_SLOT_NUMBER,
    CONFLICTING_GROUP_EVIDENCE,
}

sealed interface LobbySlotIdentityResolutionResult {
    data class Resolved(
        val group: LobbyResolvedSlotGroup,
    ) : LobbySlotIdentityResolutionResult

    data class Unresolved(
        val failure: LobbySlotIdentityResolutionFailure,
    ) : LobbySlotIdentityResolutionResult
}

class LobbySlotIdentityResolver @Inject constructor() {
    fun resolve(candidates: List<RosterSlotCandidate>): LobbySlotIdentityResolutionResult {
        val directObservations = candidates.mapNotNull { candidate ->
            val slotNumberCandidate = candidate.slotNumberCandidate
            if (slotNumberCandidate.status != RosterCandidateParseStatus.PARSED) {
                null
            } else {
                slotNumberCandidate.detectedSlotNumber?.let { slotNumber ->
                    DirectSlotObservation(candidate.visibleSlotPosition, slotNumber)
                }
            }
        }

        if (directObservations.isEmpty()) {
            return LobbySlotIdentityResolutionResult.Unresolved(
                LobbySlotIdentityResolutionFailure.NO_USABLE_SLOT_NUMBER,
            )
        }

        if (directObservations.any { observation ->
                observation.tournamentSlotNumber !in SUPPORTED_TOURNAMENT_SLOTS ||
                    observation.tournamentSlotNumber !in ALLOWED_SLOT_NUMBERS_BY_POSITION.getValue(
                        observation.visibleSlotPosition,
                    )
            }
        ) {
            return LobbySlotIdentityResolutionResult.Unresolved(
                LobbySlotIdentityResolutionFailure.POSITION_INCOMPATIBLE_SLOT_NUMBER,
            )
        }

        val groups = directObservations
            .map { observation -> groupRangeFor(observation.tournamentSlotNumber) }
            .distinct()

        if (groups.size != 1) {
            return LobbySlotIdentityResolutionResult.Unresolved(
                LobbySlotIdentityResolutionFailure.CONFLICTING_GROUP_EVIDENCE,
            )
        }

        val group = groups.single()
        val directlyObservedPositions = directObservations
            .map { observation -> observation.visibleSlotPosition }
            .toSet()
        val resolvedSlots = RosterVisibleSlotPosition.entries.map { visibleSlotPosition ->
            LobbyResolvedSlotIdentity(
                visibleSlotPosition = visibleSlotPosition,
                tournamentSlotNumber = group.first + visibleSlotPosition.offset - 1,
                source = if (visibleSlotPosition in directlyObservedPositions) {
                    LobbySlotIdentitySource.OCR_DIRECT
                } else {
                    LobbySlotIdentitySource.GROUP_INFERRED
                },
            )
        }

        return LobbySlotIdentityResolutionResult.Resolved(
            LobbyResolvedSlotGroup(
                tournamentSlotRange = group,
                slots = resolvedSlots,
                directlyDetectedCount = directObservations.size,
            ),
        )
    }

    private data class DirectSlotObservation(
        val visibleSlotPosition: RosterVisibleSlotPosition,
        val tournamentSlotNumber: Int,
    )

    private companion object {
        val SUPPORTED_TOURNAMENT_SLOTS = 1..12
        val GROUP_RANGES = listOf(1..4, 5..8, 9..12)
        val ALLOWED_SLOT_NUMBERS_BY_POSITION = mapOf(
            RosterVisibleSlotPosition.TOP_LEFT to setOf(1, 5, 9),
            RosterVisibleSlotPosition.TOP_RIGHT to setOf(2, 6, 10),
            RosterVisibleSlotPosition.BOTTOM_LEFT to setOf(3, 7, 11),
            RosterVisibleSlotPosition.BOTTOM_RIGHT to setOf(4, 8, 12),
        )

        fun groupRangeFor(tournamentSlotNumber: Int): IntRange =
            GROUP_RANGES.first { tournamentSlotNumber in it }
    }
}
