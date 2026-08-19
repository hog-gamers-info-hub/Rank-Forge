package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrPlayer
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrRunner
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrSlot
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRunner
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrField
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldStatus
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrFieldType
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrPlayerSlot
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRect
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRow
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultOcrRowSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MatchOcrResultLobbyEndToEndTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun liveFullPermutationPreservesResultSemanticsAndFinalizesToLobbySlots() = runTest(dispatcher) {
        val expectedLobbySlotByResultPosition = listOf(7, 1, 12, 4, 9, 2, 11, 5, 3, 10, 6, 8)
        val resultRows = expectedLobbySlotByResultPosition.mapIndexed { index, lobbySlot ->
            val resolvedPlayers = teamPlayers(lobbySlot)
            val resolvedKills = (index + 1..index + 4).toList()
            resultRow(
                position = index + 1,
                playerNames = resolvedPlayers,
                killValues = resolvedKills,
                rawPlayerNames = resolvedPlayers.mapIndexed { playerIndex, playerName ->
                    if (index == 0 && playerIndex == 0) "S7A1pha" else playerName
                },
                rawKillValues = resolvedKills.mapIndexed { killIndex, killValue ->
                    if (index == 0 && killIndex == 0) "04" else killValue.toString()
                },
            )
        }
        val lobbyResult = fullLobbyResult()
        val repository = createRepository()
        val viewModel = viewModel(repository, resultRows, lobbyResult)

        val state = loadState(viewModel)
        assertEquals(expectedLobbySlotByResultPosition.map(Int::toString), state.rows.map { it.suggestedTeamSlotDisplayValue })
        assertEquals(expectedLobbySlotByResultPosition, state.rows.map { it.originalSuggestedTeamSlot })
        assertEquals(
            expectedLobbySlotByResultPosition.map(Int::toString),
            state.correctionDraft!!.rows.map { it.assignedTeamSlotDraftValue },
        )
        assertEquals((1..12).toList(), state.rows.map { it.expectedPlacementLabel.toInt() })
        assertEquals((1..12).toList(), state.rows.map { it.originalParsedPlacementValue })
        assertEquals((1..12).toSet(), state.rows.mapNotNull { it.originalSuggestedTeamSlot }.toSet())
        assertTrue(state.rows.all { "OCR preview requires manual confirmation" in it.warningLabels })

        val preview = state.matchResultOcrPreview as MatchResultOcrPreviewUiState.Ready
        resultRows.forEach { resultRow ->
            val previewRow = preview.rows.single { it.position == resultRow.position }
            assertEquals(
                resultRow.playerSlots.map { it.player.resolvedText },
                previewRow.slots.map { it.playerText },
            )
            assertEquals(
                resultRow.playerSlots.map { it.player.ocrText },
                previewRow.slots.map { it.playerOcrText },
            )
            assertEquals(
                resultRow.playerSlots.map { it.kill.resolvedText },
                previewRow.slots.map { it.killText },
            )
            assertEquals(
                resultRow.playerSlots.map { it.kill.ocrText },
                previewRow.slots.map { it.killOcrText },
            )
            assertEquals(
                resultRow.playerSlots.sumOf { it.kill.resolvedText.toInt() },
                state.rows[resultRow.position - 1].detectedKillDisplayValue.toInt(),
            )
        }
        viewModel.onFinalizeOcrCorrection()
        advanceUntilIdle()
        val warningState = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(warningState.finalization.showWarningConfirmation)
        assertFalse(warningState.finalization.isFinalized)
        assertEquals(MatchStatus.DRAFT, repository.observeMatchById(MATCH_ID).first()!!.status)

        viewModel.onConfirmFinalizeWarnings()
        advanceUntilIdle()
        val finalizedState = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(finalizedState.finalization.isFinalized)

        val finalizedMatch = repository.observeMatchById(MATCH_ID).first()!!
        assertEquals(MatchStatus.FINALIZED, finalizedMatch.status)
        assertEquals(12, finalizedMatch.placements.size)
        assertEquals(12, finalizedMatch.kills.size)
        assertEquals((1..12).toSet(), finalizedMatch.placements.map { it.teamSlotNumber }.toSet())
        assertEquals((1..12).toSet(), finalizedMatch.kills.map { it.teamSlotNumber }.toSet())
        assertEquals(
            expectedLobbySlotByResultPosition.withIndex().associate { (index, slot) -> slot to index + 1 },
            finalizedMatch.placements.associate { it.teamSlotNumber to it.position },
        )
        assertEquals(
            resultRows.associate { row ->
                expectedLobbySlotByResultPosition[row.position - 1] to
                    row.playerSlots.sumOf { it.kill.resolvedText.toInt() }
            },
            finalizedMatch.kills.associate { it.teamSlotNumber to it.kills },
        )
        assertNotNull(repository.readPreservedOcrEvidence(MATCH_ID))
    }

    @Test
    fun liveMatcherRealismCoversOrderTypoThreeOfFourAndMissingPlayer() = runTest(dispatcher) {
        val cases = listOf(
            RealismCase(
                name = "order changed",
                resultPlayers = listOf("S1Delta", "S1Bravo", "S1Alpha", "S1Charlie"),
                lobbyPlayers = teamPlayers(1),
                expectedLobbySlot = 1,
            ),
            RealismCase(
                name = "ocr normalization",
                resultPlayers = listOf("PLAYER0NE", "NOVA", "RIN", "KAI"),
                lobbyPlayers = listOf("PLAYERONE", "NOVA", "RIN", "KAI"),
                expectedLobbySlot = 1,
            ),
            RealismCase(
                name = "three of four",
                resultPlayers = listOf("Unit7", "Nova", "Rin", "NewWolf"),
                lobbyPlayers = listOf("Unit7", "Nova", "Rin", "OldWolf"),
                expectedLobbySlot = 6,
            ),
            RealismCase(
                name = "missing player",
                resultPlayers = listOf("S1Alpha", "S1Bravo", null, "S1Delta"),
                lobbyPlayers = teamPlayers(1),
                expectedLobbySlot = 1,
                expectAutomaticAssignment = false,
            ),
        )

        cases.forEach { case ->
            val repository = createRepository()
            val resultRows = scenarioRows(
                resultPlayers = case.resultPlayers,
                otherRowsAreUnrelated = case.expectedLobbySlot != 1,
            )
            val lobbyResult = fullLobbyResult(mapOf(case.expectedLobbySlot to case.lobbyPlayers))
            val viewModel = viewModel(repository, resultRows, lobbyResult)
            val state = loadState(viewModel)
            val row = state.rows.first()

            assertEquals(case.name, case.expectedLobbySlot.toString(), row.suggestedTeamSlotDisplayValue)
            if (case.expectAutomaticAssignment) {
                assertEquals(case.name, case.expectedLobbySlot, row.originalSuggestedTeamSlot)
                assertEquals(
                    case.name,
                    case.expectedLobbySlot.toString(),
                    state.correctionDraft!!.rows.first().assignedTeamSlotDraftValue,
                )
                assertEquals(case.name, "Safe automatic assignment", row.assignmentSafetyStatusLabel)
            } else {
                assertNull(row.originalSuggestedTeamSlot)
                assertEquals("", state.correctionDraft!!.rows.first().assignedTeamSlotDraftValue)
                assertFalse(row.assignmentSafetyStatusLabel == "Safe automatic assignment")
            }
            if (case.name == "missing player") {
                val preview = state.matchResultOcrPreview as MatchResultOcrPreviewUiState.Ready
                assertEquals("", preview.rows.first().slots[2].playerText)
                assertFalse(row.detectedPlayerNameEvidenceLabel.contains("P3"))
            }
        }
    }

    @Test
    fun liveTwoPlayerEvidenceRemainsManual() = runTest(dispatcher) {
        val repository = createRepository()
        val resultRows = scenarioRows(listOf("S1Alpha", "S1Bravo", null, "UNMATCHED-1"))
        val viewModel = viewModel(repository, resultRows, fullLobbyResult())

        val state = loadState(viewModel)
        val row = state.rows.first()
        assertEquals("Review required", row.assignmentSafetyStatusLabel)
        assertTrue(row.topThreeSuggestionsSummary.first().contains("matches 2"))
        assertNull(row.originalSuggestedTeamSlot)
        assertEquals("", state.correctionDraft!!.rows.first().assignedTeamSlotDraftValue)
    }

    @Test
    fun liveCompetingCandidatesRemainReviewOnly() = runTest(dispatcher) {
        val repository = createRepository()
        val resultRows = scenarioRows(
            resultPlayers = teamPlayers(1),
            otherRowsAreUnrelated = true,
        )
        val lobbyResult = fullLobbyResult(
            overrides = mapOf(
                2 to listOf("S1Alpha", "S1Bravo", "S1Charlie", "S2Old"),
            ),
        )
        val viewModel = viewModel(repository, resultRows, lobbyResult)

        val state = loadState(viewModel)
        val row = state.rows.first()
        assertEquals("Review required", row.assignmentSafetyStatusLabel)
        assertTrue(row.blockerLabels.any { "INSUFFICIENT_CANDIDATE_LEAD" in it })
        assertNull(row.originalSuggestedTeamSlot)
        assertEquals("", state.correctionDraft!!.rows.first().assignedTeamSlotDraftValue)
    }

    @Test
    fun liveDuplicateSlotClaimsRemainUnassignedGlobally() = runTest(dispatcher) {
        val repository = createRepository()
        val resultRows = (1..12).map { position ->
            resultRow(
                position = position,
                playerNames = if (position <= 2) teamPlayers(7) else unrelatedPlayers(100 + position),
                killValues = listOf(1, 2, 3, 4),
            )
        }
        val viewModel = viewModel(repository, resultRows, fullLobbyResult())

        val state = loadState(viewModel)
        state.rows.take(2).forEach { row ->
            assertEquals("Review required", row.assignmentSafetyStatusLabel)
            assertTrue(row.blockerLabels.any { "DUPLICATE_TEAM_CANDIDATE" in it })
            assertNull(row.originalSuggestedTeamSlot)
        }
        assertTrue(state.correctionDraft!!.rows.take(2).all { it.assignedTeamSlotDraftValue.isBlank() })
    }

    @Test
    fun manualCorrectionResolvesOnlyTheUnassignedSlot() = runTest(dispatcher) {
        val repository = createRepository()
        val resultRows = scenarioRows(listOf("S1Alpha", "S1Bravo", null, "UNMATCHED-1"))
        val viewModel = viewModel(repository, resultRows, fullLobbyResult())

        val before = loadState(viewModel)
        assertTrue(before.correctionDraft!!.rows.first().validation.blockers.isNotEmpty())
        assertEquals("", before.correctionDraft.rows.first().assignedTeamSlotDraftValue)

        viewModel.onAssignedTeamSlotChanged(0, "2")
        val duplicateState = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        assertTrue(
            duplicateState.correctionDraft!!.rows.first().validation.blockers
                .contains(MatchOcrReviewCorrectionReason.DUPLICATE_TEAM_SLOT),
        )

        viewModel.onAssignedTeamSlotChanged(0, "1")
        val correctedState = viewModel.uiState.value as MatchOcrReviewUiState.Ready
        val correctedDraft = correctedState.correctionDraft!!
        assertEquals("1", correctedDraft.rows.first().assignedTeamSlotDraftValue)
        assertTrue(correctedDraft.rows.first().validation.blockers.isEmpty())
        assertTrue(correctedDraft.rows.drop(1).all { it.assignedTeamSlotDraftValue == it.originalAssignedTeamSlotValue })
        assertEquals(0, correctedDraft.blockerCount)
    }

    @Test
    fun missingLobbyKeepsResultEvidenceWithoutPersistedRosterFallback() = runTest(dispatcher) {
        val repository = createRepository(withSlotOneRoster = true)
        val resultRows = fullResultRows()
        val viewModel = viewModel(repository, resultRows, MatchLobbyPlayersOcrResult.unavailable())

        val state = loadState(viewModel)
        assertEquals(12, state.rows.size)
        assertEquals((1..12).toList(), state.rows.map { it.originalParsedPlacementValue })
        assertTrue(state.rows.all { it.originalSuggestedTeamSlot == null })
        assertTrue(state.rows.all { it.assignmentSafetyStatusLabel == "Manual required" })
        assertTrue(state.correctionDraft!!.rows.all { it.assignedTeamSlotDraftValue.isBlank() })
        val preview = state.matchResultOcrPreview as MatchResultOcrPreviewUiState.Ready
        assertEquals(resultRows.map { it.playerSlots.first().player.resolvedText }, preview.rows.map { it.slots.first().playerText })
        assertEquals(12, state.rows.count { it.detectedKillDisplayValue != "Unavailable" })
    }

    @Test
    fun partialResultKeepsPlaceholdersWithoutFabricatingMatchingRows() = runTest(dispatcher) {
        val repository = createRepository()
        val resultRows = fullResultRows()
        val viewModel = viewModel(
            repository = repository,
            resultRows = resultRows,
            lobbyResult = fullLobbyResult(),
            lowerResultUnavailable = true,
        )

        val state = loadState(viewModel, allowIncompleteEvidence = true)
        val preview = state.matchResultOcrPreview as MatchResultOcrPreviewUiState.Ready
        assertEquals((1..10).toList(), preview.rows.map { it.position })
        assertTrue(state.rows.take(10).all { it.originalParsedPlacementValue != null })
        assertTrue(state.rows.drop(10).all { it.originalParsedPlacementValue == null })
        assertTrue(state.rows.drop(10).all { it.originalParsedKillValue == null })
        assertTrue(state.rows.drop(10).all { it.originalSuggestedTeamSlot == null })
        assertTrue(state.correctionDraft!!.rows.drop(10).all {
            it.placementDraftValue.isBlank() &&
                it.killsDraftValue.isBlank() &&
                it.assignedTeamSlotDraftValue.isBlank()
        })
    }

    @Test
    fun lobbyOcrRemainsAuthoritativeOverConflictingPersistedRoster() = runTest(dispatcher) {
        val repository = createRepository(withSlotOneRoster = true)
        val resultRows = scenarioRows(teamPlayers(1), otherRowsAreUnrelated = true)
        val lobbyResult = fullLobbyResult(
            overrides = mapOf(
                1 to unrelatedPlayers(1),
                5 to teamPlayers(1),
            ),
        )
        val viewModel = viewModel(repository, resultRows, lobbyResult)

        val state = loadState(viewModel)
        val row = state.rows.first()
        assertEquals("5", row.suggestedTeamSlotDisplayValue)
        assertEquals(5, row.originalSuggestedTeamSlot)
        assertEquals("5", state.correctionDraft!!.rows.first().assignedTeamSlotDraftValue)
        assertFalse(row.suggestedTeamSlotDisplayValue == "1")
        assertFalse(row.originalSuggestedTeamSlot == 1)
        assertFalse(state.correctionDraft.rows.first().assignedTeamSlotDraftValue == "1")
    }

    private suspend fun createRepository(withSlotOneRoster: Boolean = false): InMemoryTournamentRepository {
        val repository = InMemoryTournamentRepository()
        repository.create(
            Tournament(
                id = TOURNAMENT_ID,
                name = "Slice 5 Cup",
                date = LocalDate.of(2026, 8, 20),
                organizerName = "Verification",
                organizerContactNumber = "123",
                status = TournamentStatus.CONFIRMED,
            ),
        )
        repository.saveTeamNames(
            tournamentId = TOURNAMENT_ID,
            teamNamesBySlotNumber = TeamSlot.SLOT_NUMBERS.associateWith { slot -> "Team $slot" },
        )
        if (withSlotOneRoster) {
            repository.saveRoster(
                tournamentId = TOURNAMENT_ID,
                slotNumber = 1,
                players = listOf("S1Alpha", "S1Bravo", "S1Charlie", "S1Delta").map { name ->
                    RosterPlayer.create(TOURNAMENT_ID, 1, name)
                },
            )
        }
        repository.createDraftMatch(
            Match(
                id = MATCH_ID,
                tournamentId = TOURNAMENT_ID,
                matchNumber = 1,
                date = LocalDate.of(2026, 8, 20),
                mapName = "Bermuda",
                status = MatchStatus.DRAFT,
            ),
        )
        return repository
    }

    private fun viewModel(
        repository: InMemoryTournamentRepository,
        resultRows: List<MatchResultOcrRow>,
        lobbyResult: MatchLobbyPlayersOcrResult,
        lowerResultUnavailable: Boolean = false,
    ): MatchOcrReviewViewModel = MatchOcrReviewViewModel(
        finalizeOcrCorrectionMatch = FinalizeOcrCorrectionMatchUseCase(
            repository = repository,
            finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
        ),
        matchResultOcrPreviewRunner = MatchResultOcrPreviewRunner { identity ->
            if (lowerResultUnavailable && identity.role == MatchResultScreenshotRole.MATCH_RESULT_LOWER) {
                MatchResultOcrPreviewProcessingResult.MissingAsset
            } else {
                MatchResultOcrPreviewProcessingResult.Processed(
                    extraction = MatchResultOcrExtractionResult(
                        role = identity.role,
                        fields = emptyList(),
                        rows = resultRows.filter { row ->
                            if (identity.role == MatchResultScreenshotRole.MATCH_RESULT_UPPER) {
                                row.position <= 10
                            } else {
                                row.position > 10
                            }
                        },
                    ),
                    pixelCrop = OcrPixelCropRect(0, 0, 100, 100),
                    cropWidth = 100,
                    cropHeight = 100,
                )
            }
        },
        matchLobbyPlayersOcrRunner = MatchLobbyPlayersOcrRunner { _, _ -> lobbyResult },
        observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
        observeRoster = ObserveRosterByTournamentUseCase(repository),
        initialUiState = MatchOcrReviewUiState.Loading,
    )

    private suspend fun TestScope.loadState(
        viewModel: MatchOcrReviewViewModel,
        allowIncompleteEvidence: Boolean = false,
    ): MatchOcrReviewUiState.Ready {
        viewModel.reprocess(TOURNAMENT_ID, MATCH_ID, allowIncompleteEvidence)
        advanceUntilIdle()
        return viewModel.uiState.value as MatchOcrReviewUiState.Ready
    }

    private fun fullResultRows(): List<MatchResultOcrRow> =
        FULL_LOBBY_SLOT_BY_RESULT_POSITION.mapIndexed { index, lobbySlot ->
            resultRow(
                position = index + 1,
                playerNames = teamPlayers(lobbySlot),
                killValues = (index + 1..index + 4).toList(),
            )
        }

    private fun scenarioRows(
        resultPlayers: List<String?>,
        otherRowsAreUnrelated: Boolean = false,
    ): List<MatchResultOcrRow> = (1..12).map { position ->
        resultRow(
            position = position,
            playerNames = if (position == 1) {
                resultPlayers
            } else if (otherRowsAreUnrelated) {
                unrelatedPlayers(100 + position)
            } else {
                teamPlayers(position)
            },
            killValues = listOf(1, 2, 3, 4),
        )
    }

    private fun fullLobbyResult(
        overrides: Map<Int, List<String?>> = emptyMap(),
    ): MatchLobbyPlayersOcrResult = MatchLobbyPlayersOcrResult(
        slots = (1..12).map { slotNumber ->
            val players = overrides[slotNumber] ?: teamPlayers(slotNumber)
            MatchLobbyPlayersOcrSlot(
                slotNumber = slotNumber,
                players = players.mapIndexed { index, playerName ->
                    MatchLobbyPlayersOcrPlayer(index + 1, playerName)
                },
            )
        },
    )

    private fun resultRow(
        position: Int,
        playerNames: List<String?>,
        killValues: List<Int>,
        rawPlayerNames: List<String?> = playerNames,
        rawKillValues: List<String> = killValues.map(Int::toString),
    ): MatchResultOcrRow {
        val role = if (position <= 10) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER
        } else {
            MatchResultScreenshotRole.MATCH_RESULT_LOWER
        }
        return MatchResultOcrRow(
            position = position,
            source = when {
                role == MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MatchResultOcrRowSource.UPPER_TEMPLATE
                position == 11 -> MatchResultOcrRowSource.LOWER_ROW_A
                else -> MatchResultOcrRowSource.LOWER_ROW_B
            },
            placement = field(
                id = "placement-$position",
                type = MatchResultOcrFieldType.PLACEMENT,
                position = position,
                slot = null,
                text = position.toString(),
                status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
            ),
            playerSlots = playerNames.mapIndexed { index, playerName ->
                MatchResultOcrPlayerSlot(
                    slot = index + 1,
                    player = field(
                        id = "player-$position-${index + 1}",
                        type = MatchResultOcrFieldType.PLAYER,
                        position = position,
                        slot = index + 1,
                        text = rawPlayerNames[index].orEmpty(),
                        resolvedText = playerName.orEmpty(),
                        status = if (playerName.isNullOrBlank()) {
                            MatchResultOcrFieldStatus.EMPTY
                        } else {
                            MatchResultOcrFieldStatus.DIRECT_TEXT
                        },
                    ),
                    kill = field(
                        id = "kill-$position-${index + 1}",
                        type = MatchResultOcrFieldType.KILL,
                        position = position,
                        slot = index + 1,
                        text = rawKillValues[index],
                        resolvedText = killValues[index].toString(),
                        status = MatchResultOcrFieldStatus.DIRECT_NUMERIC,
                    ),
                )
            },
        )
    }

    private fun field(
        id: String,
        type: MatchResultOcrFieldType,
        position: Int,
        slot: Int?,
        text: String,
        resolvedText: String = text,
        status: MatchResultOcrFieldStatus,
    ): MatchResultOcrField = MatchResultOcrField(
        id = id,
        type = type,
        position = position,
        visualRow = null,
        slot = slot,
        canonicalRect = RECT,
        mappedRect = RECT,
        ocrText = text,
        resolvedText = resolvedText,
        status = status,
    )

    private fun teamPlayers(slot: Int): List<String> = listOf(
        "S${slot}Alpha",
        "S${slot}Bravo",
        "S${slot}Charlie",
        "S${slot}Delta",
    )

    private fun unrelatedPlayers(slot: Int): List<String> = listOf(
        "Unrelated${slot}Alpha",
        "Unrelated${slot}Bravo",
        "Unrelated${slot}Charlie",
        "Unrelated${slot}Delta",
    )

    private data class RealismCase(
        val name: String,
        val resultPlayers: List<String?>,
        val lobbyPlayers: List<String?>,
        val expectedLobbySlot: Int,
        val expectAutomaticAssignment: Boolean = true,
    )

    private companion object {
        val RECT = MatchResultOcrRect(0.0, 0.0, 1.0, 1.0)
        val FULL_LOBBY_SLOT_BY_RESULT_POSITION = listOf(7, 1, 12, 4, 9, 2, 11, 5, 3, 10, 6, 8)
        const val TOURNAMENT_ID = "slice5-tournament"
        const val MATCH_ID = "slice5-match"
    }
}
