package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseFailure
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
import com.hoggamers.rankforge.domain.ocr.parsing.RosterTeamNameCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbySlotIdentityResolverTest {
    private val resolver = LobbySlotIdentityResolver()

    @Test
    fun topLeftOneResolvesGroupOneThroughFour() {
        val group = resolved(candidate(RosterVisibleSlotPosition.TOP_LEFT, direct(1)))

        assertEquals(1..4, group.tournamentSlotRange)
        assertEquals((1..4).toList(), group.slots.map { it.tournamentSlotNumber })
    }

    @Test
    fun topRightSixResolvesGroupFiveThroughEight() {
        val group = resolved(candidate(RosterVisibleSlotPosition.TOP_RIGHT, direct(6)))

        assertEquals(5..8, group.tournamentSlotRange)
        assertEquals((5..8).toList(), group.slots.map { it.tournamentSlotNumber })
    }

    @Test
    fun bottomLeftElevenResolvesGroupNineThroughTwelve() {
        val group = resolved(candidate(RosterVisibleSlotPosition.BOTTOM_LEFT, direct(11)))

        assertEquals(9..12, group.tournamentSlotRange)
        assertEquals((9..12).toList(), group.slots.map { it.tournamentSlotNumber })
    }

    @Test
    fun bottomRightTwelveResolvesGroupNineThroughTwelve() {
        val group = resolved(candidate(RosterVisibleSlotPosition.BOTTOM_RIGHT, direct(12)))

        assertEquals(9..12, group.tournamentSlotRange)
        assertEquals((9..12).toList(), group.slots.map { it.tournamentSlotNumber })
    }

    @Test
    fun oneAnchorInfersTheRemainingThreeSlots() {
        val group = resolved(candidate(RosterVisibleSlotPosition.TOP_RIGHT, direct(6)))

        assertEquals(
            listOf(
                LobbySlotIdentitySource.GROUP_INFERRED,
                LobbySlotIdentitySource.OCR_DIRECT,
                LobbySlotIdentitySource.GROUP_INFERRED,
                LobbySlotIdentitySource.GROUP_INFERRED,
            ),
            group.slots.map { it.source },
        )
        assertEquals(1, group.directlyDetectedCount)
    }

    @Test
    fun directSlotIsMarkedAsOcrDirect() {
        val group = resolved(candidate(RosterVisibleSlotPosition.BOTTOM_LEFT, direct(11)))

        assertEquals(
            LobbySlotIdentitySource.OCR_DIRECT,
            group.slots.single { it.visibleSlotPosition == RosterVisibleSlotPosition.BOTTOM_LEFT }.source,
        )
    }

    @Test
    fun inferredSlotsAreMarkedAsGroupInferred() {
        val group = resolved(candidate(RosterVisibleSlotPosition.BOTTOM_LEFT, direct(11)))

        assertTrue(
            group.slots
                .filterNot { it.visibleSlotPosition == RosterVisibleSlotPosition.BOTTOM_LEFT }
                .all { it.source == LobbySlotIdentitySource.GROUP_INFERRED },
        )
    }

    @Test
    fun multipleAgreeingDirectAnchorsResolveTheSameGroup() {
        val result = resolver.resolve(
            listOf(
                candidate(RosterVisibleSlotPosition.TOP_LEFT, direct(5)),
                candidate(RosterVisibleSlotPosition.BOTTOM_RIGHT, direct(8)),
            ),
        )

        val group = resolved(result)
        assertEquals(5..8, group.tournamentSlotRange)
        assertEquals(2, group.directlyDetectedCount)
        assertEquals(
            setOf(RosterVisibleSlotPosition.TOP_LEFT, RosterVisibleSlotPosition.BOTTOM_RIGHT),
            group.slots.filter { it.source == LobbySlotIdentitySource.OCR_DIRECT }
                .map { it.visibleSlotPosition }
                .toSet(),
        )
    }

    @Test
    fun conflictingGroupsReturnTypedFailure() {
        val result = resolver.resolve(
            listOf(
                candidate(RosterVisibleSlotPosition.TOP_LEFT, direct(5)),
                candidate(RosterVisibleSlotPosition.TOP_RIGHT, direct(2)),
            ),
        )

        assertEquals(
            LobbySlotIdentityResolutionResult.Unresolved(
                LobbySlotIdentityResolutionFailure.CONFLICTING_GROUP_EVIDENCE,
            ),
            result,
        )
    }

    @Test
    fun bottomLeftOneReturnsPositionIncompatibleFailure() {
        val result = resolver.resolve(
            listOf(candidate(RosterVisibleSlotPosition.BOTTOM_LEFT, direct(1))),
        )

        assertEquals(
            LobbySlotIdentityResolutionResult.Unresolved(
                LobbySlotIdentityResolutionFailure.POSITION_INCOMPATIBLE_SLOT_NUMBER,
            ),
            result,
        )
    }

    @Test
    fun topRightElevenReturnsPositionIncompatibleFailure() {
        val result = resolver.resolve(
            listOf(candidate(RosterVisibleSlotPosition.TOP_RIGHT, direct(11))),
        )

        assertEquals(
            LobbySlotIdentityResolutionResult.Unresolved(
                LobbySlotIdentityResolutionFailure.POSITION_INCOMPATIBLE_SLOT_NUMBER,
            ),
            result,
        )
    }

    @Test
    fun zeroUsableAnchorsReturnNoUsableSlotNumber() {
        val result = resolver.resolve(
            listOf(
                candidate(RosterVisibleSlotPosition.TOP_LEFT, unusable(RosterCandidateParseStatus.MISSING)),
                candidate(RosterVisibleSlotPosition.TOP_RIGHT, unusable(RosterCandidateParseStatus.DUPLICATE)),
                candidate(RosterVisibleSlotPosition.BOTTOM_LEFT, unusable(RosterCandidateParseStatus.INPUT_FAILURE)),
                candidate(RosterVisibleSlotPosition.BOTTOM_RIGHT, unusable(RosterCandidateParseStatus.AMBIGUOUS)),
            ),
        )

        assertEquals(
            LobbySlotIdentityResolutionResult.Unresolved(
                LobbySlotIdentityResolutionFailure.NO_USABLE_SLOT_NUMBER,
            ),
            result,
        )
    }

    @Test
    fun oneParsedAnchorAndThreeMissingAnchorsStillResolve() {
        val group = resolved(
            resolver.resolve(
                listOf(
                    candidate(RosterVisibleSlotPosition.TOP_LEFT, unusable(RosterCandidateParseStatus.MISSING)),
                    candidate(RosterVisibleSlotPosition.TOP_RIGHT, direct(6)),
                    candidate(RosterVisibleSlotPosition.BOTTOM_LEFT, unusable(RosterCandidateParseStatus.MISSING)),
                    candidate(RosterVisibleSlotPosition.BOTTOM_RIGHT, unusable(RosterCandidateParseStatus.MISSING)),
                ),
            ),
        )

        assertEquals(5..8, group.tournamentSlotRange)
        assertEquals(1, group.directlyDetectedCount)
    }

    @Test
    fun parsedAnchorWithMalformedEmptyAndAmbiguousOthersStillResolves() {
        val group = resolved(
            resolver.resolve(
                listOf(
                    candidate(RosterVisibleSlotPosition.TOP_LEFT, unusable(RosterCandidateParseStatus.MALFORMED)),
                    candidate(RosterVisibleSlotPosition.TOP_RIGHT, unusable(RosterCandidateParseStatus.EMPTY)),
                    candidate(RosterVisibleSlotPosition.BOTTOM_LEFT, direct(11)),
                    candidate(RosterVisibleSlotPosition.BOTTOM_RIGHT, unusable(RosterCandidateParseStatus.AMBIGUOUS)),
                ),
            ),
        )

        assertEquals(9..12, group.tournamentSlotRange)
        assertEquals(1, group.directlyDetectedCount)
    }

    @Test
    fun screenshotPositionOneDoesNotOverrideTopLeftNine() {
        val group = resolved(
            candidate(
                visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
                slotNumberCandidate = direct(9),
                screenshotPosition = RosterScreenshotPosition.ONE,
            ),
        )

        assertEquals(9..12, group.tournamentSlotRange)
    }

    @Test
    fun screenshotPositionThreeDoesNotOverrideTopLeftOne() {
        val group = resolved(
            candidate(
                visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
                slotNumberCandidate = direct(1),
                screenshotPosition = RosterScreenshotPosition.THREE,
            ),
        )

        assertEquals(1..4, group.tournamentSlotRange)
    }

    @Test
    fun multiDigitTenIsResolvedCorrectly() {
        val group = resolved(candidate(RosterVisibleSlotPosition.TOP_RIGHT, direct(10)))

        assertEquals(9..12, group.tournamentSlotRange)
        assertEquals(10, group.slots[1].tournamentSlotNumber)
    }

    @Test
    fun multiDigitElevenIsResolvedCorrectly() {
        val group = resolved(candidate(RosterVisibleSlotPosition.BOTTOM_LEFT, direct(11)))

        assertEquals(11, group.slots[2].tournamentSlotNumber)
    }

    @Test
    fun multiDigitTwelveIsResolvedCorrectly() {
        val group = resolved(candidate(RosterVisibleSlotPosition.BOTTOM_RIGHT, direct(12)))

        assertEquals(12, group.slots[3].tournamentSlotNumber)
    }

    @Test
    fun resultSlotOrderIsDeterministicAndInputOrderDoesNotMatter() {
        val candidates = listOf(
            candidate(RosterVisibleSlotPosition.BOTTOM_RIGHT, direct(8)),
            candidate(RosterVisibleSlotPosition.TOP_LEFT, direct(5)),
            candidate(RosterVisibleSlotPosition.BOTTOM_LEFT, unusable(RosterCandidateParseStatus.MISSING)),
            candidate(RosterVisibleSlotPosition.TOP_RIGHT, direct(6)),
        )

        val first = resolver.resolve(candidates)
        val second = resolver.resolve(candidates.reversed())

        assertEquals(first, second)
        assertEquals(
            RosterVisibleSlotPosition.entries,
            (first as LobbySlotIdentityResolutionResult.Resolved).group.slots
                .map { it.visibleSlotPosition },
        )
    }

    private fun resolved(
        candidate: RosterSlotCandidate,
    ): LobbyResolvedSlotGroup = resolved(resolver.resolve(listOf(candidate)))

    private fun resolved(
        result: LobbySlotIdentityResolutionResult,
    ): LobbyResolvedSlotGroup = (result as LobbySlotIdentityResolutionResult.Resolved).group

    private fun candidate(
        visibleSlotPosition: RosterVisibleSlotPosition,
        slotNumberCandidate: RosterSlotNumberCandidate,
        screenshotPosition: RosterScreenshotPosition = RosterScreenshotPosition.ONE,
    ): RosterSlotCandidate = RosterSlotCandidate(
        screenshotPosition = screenshotPosition,
        visibleSlotPosition = visibleSlotPosition,
        intendedTournamentSlotRange = screenshotPosition.tournamentSlotRange,
        intendedTournamentSlot = screenshotPosition.tournamentSlotFor(visibleSlotPosition),
        teamNameCandidate = RosterTeamNameCandidate(
            status = RosterCandidateParseStatus.UNSUPPORTED,
            failure = RosterCandidateParseFailure.UNSUPPORTED_TEAM_NAME_REGION,
            rawSourceResults = emptyList(),
            confidence = RawOcrConfidence.Unavailable,
        ),
        playerNameCandidates = emptyList(),
        slotNumberCandidate = slotNumberCandidate,
    )

    private fun direct(slotNumber: Int): RosterSlotNumberCandidate = RosterSlotNumberCandidate(
        status = RosterCandidateParseStatus.PARSED,
        detectedSlotNumber = slotNumber,
        failure = null,
        rawSourceResults = emptyList(),
        confidence = RawOcrConfidence.Unavailable,
    )

    private fun unusable(status: RosterCandidateParseStatus): RosterSlotNumberCandidate =
        RosterSlotNumberCandidate(
            status = status,
            detectedSlotNumber = null,
            failure = null,
            rawSourceResults = emptyList(),
            confidence = RawOcrConfidence.Unavailable,
        )
}
