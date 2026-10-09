package com.hoggamers.rankforge.domain.tournament

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCorrectionUseCaseTest {
    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
    }

    @Test
    fun finalizedMatchCanStartCorrection() = runTest {
        val repository = createFinalizedRepository()

        val result = StartMatchCorrectionUseCase(repository, SignedInTournamentTestAuthRepository())("match-id")

        assertTrue(result is StartMatchCorrectionResult.Started)
        assertEquals(MatchStatus.FINALIZED, (result as StartMatchCorrectionResult.Started).match.status)
    }

    @Test
    fun draftMatchCannotStartCorrection() = runTest {
        val repository = createDraftRepository()

        val result = StartMatchCorrectionUseCase(repository, SignedInTournamentTestAuthRepository())("match-id")

        assertEquals(
            MatchCorrectionGlobalError.MATCH_NOT_FINALIZED,
            (result as StartMatchCorrectionResult.Rejected).error,
        )
    }

    @Test
    fun discardCorrectionLeavesFinalizedResultUnchanged() = runTest {
        val repository = createFinalizedRepository()
        repository.saveDraftMatchValue("tournament-id", "match-id", 1, "12", "99")
        val before = repository.observeMatchById("match-id").first()!!

        ClearMatchCorrectionDraftUseCase(repository, SignedInTournamentTestAuthRepository())(
            ClearMatchCorrectionDraftInput("tournament-id", "match-id"),
        )

        assertEquals(before, repository.observeMatchById("match-id").first())
        assertTrue(repository.observeDraftMatchValues("tournament-id", "match-id").first().isEmpty())
    }

    @Test
    fun validCorrectionSubmitsAndPreservesPreviousResultInformation() = runTest {
        val repository = createFinalizedRepository()
        val result = SubmitMatchCorrectionUseCase(
            repository,
            ValidateMatchResultUseCase(),
            SignedInTournamentTestAuthRepository(),
            ProtectedMatchCorrectionAction { ProtectedMatchCorrectionResult.Success(2) },
        )(
            SubmitMatchCorrectionInput("match-id", correctedRows()),
        )

        val submitted = result as SubmitMatchCorrectionResult.Submitted
        assertEquals(MatchStatus.FINALIZED, submitted.match.status)
        assertEquals(2, submitted.match.placements.first { it.teamSlotNumber == 1 }.position)
        assertEquals(1, submitted.match.kills.first { it.teamSlotNumber == 1 }.kills)
        assertEquals(1, submitted.match.correctionHistory.size)
        assertEquals(1, submitted.match.correctionHistory.single().previousPlacements.first().position)
        assertEquals(2, submitted.match.correctionHistory.single().correctedPlacements.first().position)
        assertTrue(repository.observeDraftMatchValues("tournament-id", "match-id").first().isEmpty())
    }

    @Test
    fun validCorrectionPreservesExistingPointAdjustment() = runTest {
        val repository = createDraftRepository()
        val originalResults = (1..12).map { slotNumber ->
            MatchParticipantResult(
                teamSlotNumber = slotNumber,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = slotNumber,
                kills = slotNumber - 1,
                pointAdjustment = if (slotNumber == 1) 3 else 0,
            )
        }
        repository.finalizeDraftMatch(
            matchId = "match-id",
            placements = originalResults.map { MatchPlacement(it.teamSlotNumber, it.placement!!) },
            kills = originalResults.map { MatchKill(it.teamSlotNumber, it.kills) },
            participantResults = originalResults,
        )

        val result = SubmitMatchCorrectionUseCase(
            repository,
            ValidateMatchResultUseCase(),
            SignedInTournamentTestAuthRepository(),
            ProtectedMatchCorrectionAction { ProtectedMatchCorrectionResult.Success(2) },
        )(SubmitMatchCorrectionInput("match-id", correctedRows()))

        val corrected = result as SubmitMatchCorrectionResult.Submitted
        assertEquals(
            3,
            corrected.match.finalizedParticipantResultsOrNull()!!.first { it.teamSlotNumber == 1 }
                .pointAdjustment,
        )
    }

    @Test
    fun invalidCorrectionIsBlockedAndFinalizedResultRemains() = runTest {
        val repository = createFinalizedRepository()
        val result = SubmitMatchCorrectionUseCase(
            repository,
            ValidateMatchResultUseCase(),
            SignedInTournamentTestAuthRepository(),
        )(
            SubmitMatchCorrectionInput(
                "match-id",
                correctedRows().map { row -> if (row.teamSlotNumber == 1) row.copy(placement = "") else row },
            ),
        )

        assertTrue(result is SubmitMatchCorrectionResult.Invalid)
        assertEquals(
            1,
            repository.observeMatchById("match-id").first()!!.placements.first { it.teamSlotNumber == 1 }.position,
        )
        assertTrue(repository.observeMatchById("match-id").first()!!.correctionHistory.isEmpty())
    }

    @Test
    fun protectedCorrectionFailureLeavesFinalizedResultUnchanged() = runTest {
        val repository = createFinalizedRepository()

        val result = SubmitMatchCorrectionUseCase(
            repository,
            ValidateMatchResultUseCase(),
            SignedInTournamentTestAuthRepository(),
            ProtectedMatchCorrectionAction { ProtectedMatchCorrectionResult.NetworkFailure },
        )(SubmitMatchCorrectionInput("match-id", correctedRows()))

        assertEquals(
            MatchCorrectionGlobalError.NETWORK_FAILURE,
            (result as SubmitMatchCorrectionResult.Invalid).globalError,
        )
        assertEquals(1, repository.observeMatchById("match-id").first()!!.placements.first().position)
        assertTrue(repository.observeMatchById("match-id").first()!!.correctionHistory.isEmpty())
    }

    @Test
    fun tenTeamCorrectionSwapsPlacementsAndKeepsTenStoredResults() = runTest {
        val repository = createTenTeamFinalizedRepository()

        val result = SubmitMatchCorrectionUseCase(
            repository,
            ValidateMatchResultUseCase(),
            SignedInTournamentTestAuthRepository(),
            ProtectedMatchCorrectionAction { ProtectedMatchCorrectionResult.Success(2) },
        )(
            SubmitMatchCorrectionInput("match-id", correctedRows(10)),
        )

        val submitted = result as SubmitMatchCorrectionResult.Submitted
        assertEquals(10, submitted.match.placements.size)
        assertEquals(10, submitted.match.kills.size)
        assertEquals(setOf(1, 2), submitted.match.placements
            .filter { it.teamSlotNumber <= 2 }
            .map { it.position }
            .toSet())
        assertTrue(submitted.match.placements.none { it.teamSlotNumber > 10 })
    }

    @Test
    fun standardCorrectionNeverReadsIdentityContextAndKeepsExistingTenTeamBehavior() = runTest {
        val repository = createTenTeamFinalizedRepository()
        val identityRepository = RecordingIdentityContextRepository(
            MatchTeamIdentityContextReadResult.SetupRequired,
        )

        val result = SubmitMatchCorrectionUseCase(
            repository = repository,
            validateMatchResult = ValidateMatchResultUseCase(),
            authRepository = SignedInTournamentTestAuthRepository(),
            protectedCorrection = ProtectedMatchCorrectionAction {
                ProtectedMatchCorrectionResult.Success(2)
            },
            matchIdentityContextRepository = identityRepository,
        )(
            SubmitMatchCorrectionInput("match-id", correctedRows(10)),
        )

        assertTrue(result is SubmitMatchCorrectionResult.Submitted)
        assertTrue(identityRepository.matchIds.isEmpty())
        assertEquals(10, (result as SubmitMatchCorrectionResult.Submitted).match.placements.size)
    }

    @Test
    fun groupRotationCorrectionUsesLoadedExplicitContextIncludingCanonicalSlotEighteen() = runTest {
        val repository = createGroupRotationFinalizedRepository()
        val identityRepository = RecordingIdentityContextRepository(
            MatchTeamIdentityContextReadResult.Loaded(groupRotationIdentityContext()),
        )
        var protectedCalls = 0

        val result = SubmitMatchCorrectionUseCase(
            repository = repository,
            validateMatchResult = ValidateMatchResultUseCase(),
            authRepository = SignedInTournamentTestAuthRepository(),
            protectedCorrection = ProtectedMatchCorrectionAction {
                protectedCalls++
                ProtectedMatchCorrectionResult.Success(2)
            },
            matchIdentityContextRepository = identityRepository,
        )(
            SubmitMatchCorrectionInput("group-match-id", groupRotationCorrectionRows()),
        )

        assertTrue(result is SubmitMatchCorrectionResult.Submitted)
        assertEquals(1, protectedCalls)
        assertEquals(listOf("group-match-id"), identityRepository.matchIds)
        assertTrue(
            groupRotationIdentityContext().eligibleTeamSlotNumbers.contains(18),
        )
    }

    @Test
    fun groupRotationSetupRequiredPreventsProtectedCorrection() = runTest {
        assertGroupRotationIdentityFailurePreventsCloud(
            MatchTeamIdentityContextReadResult.SetupRequired,
        )
    }

    @Test
    fun groupRotationInvalidMappingPreventsProtectedCorrection() = runTest {
        assertGroupRotationIdentityFailurePreventsCloud(
            MatchTeamIdentityContextReadResult.InvalidMapping,
        )
    }

    @Test
    fun groupRotationContextPairingMismatchPreventsProtectedCorrection() = runTest {
        val repository = createGroupRotationFinalizedRepository()
        val wrongPairing = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        var protectedCalls = 0

        val result = SubmitMatchCorrectionUseCase(
            repository = repository,
            validateMatchResult = ValidateMatchResultUseCase(),
            authRepository = SignedInTournamentTestAuthRepository(),
            protectedCorrection = ProtectedMatchCorrectionAction {
                protectedCalls++
                ProtectedMatchCorrectionResult.Success(2)
            },
            matchIdentityContextRepository = RecordingIdentityContextRepository(
                MatchTeamIdentityContextReadResult.Loaded(
                    MatchTeamIdentityContext(
                        tournamentId = TOURNAMENT_ID,
                        pairing = wrongPairing,
                        teams = groupRotationEligibleSlots().mapIndexed { index, slot ->
                            MatchLobbyTeamIdentity(index + 1, slot)
                        },
                    ),
                ),
            ),
        )(
            SubmitMatchCorrectionInput("group-match-id", groupRotationCorrectionRows()),
        )

        assertEquals(MatchCorrectionGlobalError.INVALID_DATA, (result as SubmitMatchCorrectionResult.Invalid).globalError)
        assertEquals(0, protectedCalls)
    }

    @Test
    fun groupRotationParticipantSetDifferentFromContextPreventsProtectedCorrection() = runTest {
        val repository = createGroupRotationFinalizedRepository()
        var protectedCalls = 0
        val result = SubmitMatchCorrectionUseCase(
            repository = repository,
            validateMatchResult = ValidateMatchResultUseCase(),
            authRepository = SignedInTournamentTestAuthRepository(),
            protectedCorrection = ProtectedMatchCorrectionAction {
                protectedCalls++
                ProtectedMatchCorrectionResult.Success(2)
            },
            matchIdentityContextRepository = RecordingIdentityContextRepository(
                MatchTeamIdentityContextReadResult.Loaded(
                    MatchTeamIdentityContext(
                        tournamentId = TOURNAMENT_ID,
                        pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C),
                        teams = (1..12).map { lobbySlot ->
                            MatchLobbyTeamIdentity(lobbySlot, lobbySlot)
                        },
                    ),
                ),
            ),
        )(
            SubmitMatchCorrectionInput("group-match-id", groupRotationCorrectionRows()),
        )

        assertEquals(MatchCorrectionGlobalError.INVALID_DATA, (result as SubmitMatchCorrectionResult.Invalid).globalError)
        assertEquals(0, protectedCalls)
    }

    @Test
    fun zeroMappingHistoricalGroupRotationCorrectionFailsBeforeCloudAccess() = runTest {
        val repository = createGroupRotationFinalizedRepository()
        var protectedCalls = 0
        val result = SubmitMatchCorrectionUseCase(
            repository = repository,
            validateMatchResult = ValidateMatchResultUseCase(),
            authRepository = SignedInTournamentTestAuthRepository(),
            protectedCorrection = ProtectedMatchCorrectionAction {
                protectedCalls++
                ProtectedMatchCorrectionResult.Success(2)
            },
            matchIdentityContextRepository = RecordingIdentityContextRepository(
                MatchTeamIdentityContextReadResult.SetupRequired,
            ),
        )(
            SubmitMatchCorrectionInput("group-match-id", groupRotationCorrectionRows()),
        )

        assertEquals(MatchCorrectionGlobalError.INVALID_DATA, (result as SubmitMatchCorrectionResult.Invalid).globalError)
        assertEquals(0, protectedCalls)
    }

    @Test
    fun tenTeamCorrectionRejectsMissingOrExtraIncomingRows() = runTest {
        val repository = createTenTeamFinalizedRepository()

        listOf(
            correctedRows(10).dropLast(1),
            correctedRows(10) + MatchResultRowInput(11, "11", "10"),
        ).forEach { rows ->
            val result = SubmitMatchCorrectionUseCase(
                repository,
                ValidateMatchResultUseCase(),
                SignedInTournamentTestAuthRepository(),
                ProtectedMatchCorrectionAction { error("cloud correction must not be called") },
            )(
                SubmitMatchCorrectionInput("match-id", rows),
            )

            assertTrue(result is SubmitMatchCorrectionResult.Invalid)
            assertEquals(10, repository.observeMatchById("match-id").first()!!.placements.size)
        }
    }

    @Test
    fun localCorrectionPersistsParticipantStatusTransitionAndStatusAwareAudit() = runTest {
        val repository = createThreeTeamFinalizedRepository()
        val corrected = listOf(
            MatchParticipantResult(1, MatchParticipationStatus.PARTICIPATED, 1, 4),
            MatchParticipantResult(2, MatchParticipationStatus.PARTICIPATED, 2, 2),
            MatchParticipantResult(3, MatchParticipationStatus.PARTICIPATED, 3, 1),
        )

        val result = repository.submitMatchCorrection(
            matchId = "match-id",
            placements = corrected.map { MatchPlacement(it.teamSlotNumber, it.placement!!) },
            kills = corrected.map { MatchKill(it.teamSlotNumber, it.kills) },
            participantResults = corrected,
        )

        val submitted = result as SubmitMatchCorrectionRepositoryResult.Submitted
        assertEquals(corrected, submitted.match.participantResults)
        assertEquals(MatchParticipationStatus.PARTICIPATED, submitted.match.participantResults.last().participationStatus)
        assertEquals(MatchParticipationStatus.NO_SHOW, submitted.match.correctionHistory.single().previousParticipantResults.last().participationStatus)
        assertEquals(MatchParticipationStatus.PARTICIPATED, submitted.match.correctionHistory.single().correctedParticipantResults.last().participationStatus)
    }

    private suspend fun createDraftRepository(
        activeCount: Int? = null,
    ): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        repository.create(
            Tournament(
                id = "tournament-id",
                name = "Summer Cup",
                stageName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
                ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
            ),
        )
        val participatingTeamCount = activeCount ?: TeamSlot.MAX_SLOT_NUMBER
        repository.saveTeamNames(
            "tournament-id",
            (1..participatingTeamCount).associateWith { slotNumber -> "Team $slotNumber" },
        )
        repository.createDraftMatch(
            Match(
                id = "match-id",
                tournamentId = "tournament-id",
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        return repository
    }

    private suspend fun createFinalizedRepository(): InMemoryTournamentRepository {
        val repository = createDraftRepository()
        repository.finalizeDraftMatch(
            "match-id",
            (1..12).map { MatchPlacement(it, it) },
            (1..12).map { MatchKill(it, it - 1) },
        )
        return repository
    }

    private suspend fun createTenTeamFinalizedRepository(): InMemoryTournamentRepository {
        val repository = createDraftRepository(activeCount = 10)
        assertTrue(
            repository.finalizeDraftMatch(
                "match-id",
                (1..10).map { MatchPlacement(it, it) },
                (1..10).map { MatchKill(it, it - 1) },
            ) is FinalizeMatchRepositoryResult.Finalized,
        )
        return repository
    }

    private suspend fun createThreeTeamFinalizedRepository(): InMemoryTournamentRepository {
        val repository = createDraftRepository(activeCount = 3)
        val participantResults = listOf(
            MatchParticipantResult(1, MatchParticipationStatus.PARTICIPATED, 1, 3),
            MatchParticipantResult(2, MatchParticipationStatus.PARTICIPATED, 2, 2),
            MatchParticipantResult(3, MatchParticipationStatus.NO_SHOW, null, 0),
        )
        assertTrue(
            repository.finalizeDraftMatch(
                "match-id",
                participantResults.mapNotNull { it.placement?.let { position -> MatchPlacement(it.teamSlotNumber, position) } },
                participantResults.filter { it.placement != null }.map { MatchKill(it.teamSlotNumber, it.kills) },
                participantResults,
            ) is FinalizeMatchRepositoryResult.Finalized,
        )
        return repository
    }

    private suspend fun createGroupRotationFinalizedRepository(
        withMapping: Boolean = true,
    ): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        val pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C)
        repository.create(
            Tournament(
                id = TOURNAMENT_ID,
                name = "Group Rotation Cup",
                stageName = "Organizer",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
                ownerUserId = SignedInTournamentTestAuthRepository.OWNER_USER_ID,
                format = TournamentFormat.GROUP_ROTATION,
                groupCount = 4,
                selectedGroupPairings = listOf(pairing),
            ),
        )
        repository.saveTeamNames(
            TOURNAMENT_ID,
            (1..24).associateWith { slotNumber -> "Team $slotNumber" },
        )
        if (withMapping) {
            repository.replaceGroupRotationPairingLobbySlots(
                groupRotationEligibleSlots().mapIndexed { index, slotNumber ->
                    GroupRotationPairingLobbySlot(
                        tournamentId = TOURNAMENT_ID,
                        pairing = pairing,
                        lobbySlotNumber = index + 1,
                        teamSlotNumber = slotNumber,
                    )
                },
            )
        }
        repository.createDraftMatch(
            Match(
                id = "group-match-id",
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 7, 24),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
                groupPairing = pairing,
            ),
        )
        val participantResults = groupRotationEligibleSlots().mapIndexed { index, slotNumber ->
            MatchParticipantResult(
                teamSlotNumber = slotNumber,
                participationStatus = MatchParticipationStatus.PARTICIPATED,
                placement = index + 1,
                kills = index,
            )
        }
        assertTrue(
            repository.finalizeDraftMatch(
                matchId = "group-match-id",
                placements = participantResults.map { MatchPlacement(it.teamSlotNumber, it.placement!!) },
                kills = participantResults.map { MatchKill(it.teamSlotNumber, it.kills) },
                participantResults = participantResults,
            ) is FinalizeMatchRepositoryResult.Finalized,
        )
        return repository
    }

    private fun groupRotationEligibleSlots() = (1..6).toList() + (13..18).toList()

    private fun groupRotationIdentityContext() = MatchTeamIdentityContext(
        tournamentId = TOURNAMENT_ID,
        pairing = GroupPairing(TournamentGroup.A, TournamentGroup.C),
        teams = groupRotationEligibleSlots().mapIndexed { index, slotNumber ->
            MatchLobbyTeamIdentity(index + 1, slotNumber)
        },
    )

    private fun groupRotationCorrectionRows() = groupRotationEligibleSlots().mapIndexed { index, slotNumber ->
        MatchResultRowInput(
            teamSlotNumber = slotNumber,
            placement = when (index) {
                0 -> "2"
                1 -> "1"
                else -> (index + 1).toString()
            },
            kills = index.toString(),
        )
    }

    private suspend fun assertGroupRotationIdentityFailurePreventsCloud(
        contextResult: MatchTeamIdentityContextReadResult,
    ) {
        val repository = createGroupRotationFinalizedRepository()
        val identityRepository = RecordingIdentityContextRepository(contextResult)
        var protectedCalls = 0

        val result = SubmitMatchCorrectionUseCase(
            repository = repository,
            validateMatchResult = ValidateMatchResultUseCase(),
            authRepository = SignedInTournamentTestAuthRepository(),
            protectedCorrection = ProtectedMatchCorrectionAction {
                protectedCalls++
                ProtectedMatchCorrectionResult.Success(2)
            },
            matchIdentityContextRepository = identityRepository,
        )(
            SubmitMatchCorrectionInput("group-match-id", groupRotationCorrectionRows()),
        )

        assertEquals(MatchCorrectionGlobalError.INVALID_DATA, (result as SubmitMatchCorrectionResult.Invalid).globalError)
        assertEquals(0, protectedCalls)
        assertEquals(listOf("group-match-id"), identityRepository.matchIds)
    }

    private class RecordingIdentityContextRepository(
        private val result: MatchTeamIdentityContextReadResult,
    ) : MatchTeamIdentityContextRepository {
        val matchIds = mutableListOf<String>()

        override suspend fun readForMatch(
            matchId: String,
            ownerUserId: String,
        ): MatchTeamIdentityContextReadResult {
            matchIds += matchId
            return result
        }

        override suspend fun readForPairing(
            tournamentId: String,
            pairing: GroupPairing,
            ownerUserId: String,
        ): MatchTeamIdentityContextReadResult = result
    }

    private fun correctedRows(count: Int = 12) = (1..count).map { slotNumber ->
        MatchResultRowInput(
            teamSlotNumber = slotNumber,
            placement = when (slotNumber) {
                1 -> "2"
                2 -> "1"
                else -> slotNumber.toString()
            },
            kills = when (slotNumber) {
                1 -> "1"
                else -> (slotNumber - 1).toString()
            },
        )
    }
}
