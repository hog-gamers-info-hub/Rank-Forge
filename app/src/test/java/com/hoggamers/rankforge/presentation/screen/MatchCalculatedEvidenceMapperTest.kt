package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultPositionCalculatedEvidence
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrScreenshotResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrSlot
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrUnavailableReason
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewOutcome
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewUnavailableReason
import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerRowCropPreview
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropBounds
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCalculatedEvidenceMapperTest {
    @Test
    fun mapsExistingLobbyAndResultDisplayValuesAndGeometry() {
        val evidence = mapperInput().let { (reviewState, ocrState) ->
            MatchCalculatedEvidenceMapper.map(reviewState, ocrState)
        }

        requireNotNull(evidence)
        assertEquals(12, evidence.lobby.teams.single().slotNumber)
        assertEquals("Team 12", evidence.lobby.teams.single().teamName)
        assertEquals(3, evidence.lobby.teams.single().sourceScreenshotIndex)
        assertEquals(1.25, evidence.lobby.teams.single().cropLeft, 0.0)
        assertEquals(listOf("Lobby P1", null, "Lobby P3", null), evidence.lobby.teams.single().playerNames)

        assertEquals(12, evidence.result.positions.size)
        val result = evidence.result.positions.single { it.position == 12 }
        assertEquals(12, result.position)
        assertEquals(MatchResultScreenshotRole.MATCH_RESULT_LOWER, result.sourceScreenshotRole)
        assertEquals(11, result.cropLeft)
        assertEquals(12, result.slotNumber)
        assertEquals("Team 12", result.teamName)
        assertEquals(
            listOf("Result P1", MATCH_RESULT_NOT_DETECTED_PLAYER, "Result P3", MATCH_RESULT_NOT_DETECTED_PLAYER),
            result.playerNames,
        )
        assertEquals(listOf(true, false, true, false), result.playerKillApplicable)
        assertEquals(listOf(2, null, 5, null), result.playerKills)
        assertEquals(7, result.totalKills)
    }

    @Test
    fun mapsAnEmptyPlaceholderCalculationAsTwelveBlankResultRows() {
        val (reviewState, ocrState) = mapperInput()
        val blankRows = MatchResultOcrPreviewUiStateMapper.manualFallbackRows()
        val emptyOcrState = ocrState.copy(
            rows = blankRows,
            correctionDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(blankRows),
            matchResultOcrPreview = MatchResultOcrPreviewUiState.Empty,
            phase1LobbySlotNumberOcr = null,
        )

        val evidence = requireNotNull(
            MatchCalculatedEvidenceMapper.map(
                reviewState.copy(resultPositionCropPreviews = emptyMap()),
                emptyOcrState,
            ),
        )
        assertTrue(evidence.lobby.teams.isEmpty())
        assertEquals((1..12).toList(), evidence.result.positions.map { it.position })
        evidence.result.positions.forEach { position ->
            assertEquals(null, position.sourceScreenshotRole)
            assertEquals(null, position.cropLeft)
            assertEquals(List(4) { MATCH_RESULT_NOT_DETECTED_PLAYER }, position.playerNames)
            assertEquals(List(4) { false }, position.playerKillApplicable)
            assertEquals(List(4) { null }, position.playerKills)
            assertEquals(null, position.totalKills)
        }
    }

    @Test
    fun initialWorkingSetPersistsTwelveRowsWithoutLobbyOrResultEvidence() {
        val (reviewState, _) = mapperInput()

        val evidence = requireNotNull(MatchCalculatedEvidenceMapper.initialResultWorkingSet(reviewState))

        assertTrue(evidence.lobby.teams.isEmpty())
        assertEquals((1..12).toList(), evidence.result.positions.map { it.position })
        assertTrue(evidence.result.positions.all { position ->
                position.playerNames == List(4) { MATCH_RESULT_NOT_DETECTED_PLAYER } &&
                position.playerKillApplicable == List(4) { false } &&
                position.playerKills == List(4) { null } &&
                position.placement == null
        })
    }

    @Test
    fun restoredWorkingSetKeepsGeometrylessRowsAsDataOnlyRows() {
        val evidence = MatchCalculatedEvidenceMapper.initialResultWorkingSet(mapperInput().first)
        val restored = requireNotNull(evidence).toRestoredOcrReviewUiState(
            tournamentId = "tournament-1",
            matchId = "match-1",
            teamNamesBySlot = emptyMap(),
        ) as MatchOcrReviewUiState.Ready

        assertEquals(12, restored.rows.size)
        assertTrue((restored.matchResultOcrPreview as MatchResultOcrPreviewUiState.Ready).rows.isEmpty())
        assertTrue(restored.rows.all { row ->
            row.detectedPlacementDisplayValue.isBlank() &&
                row.detectedKillDisplayValue.isBlank() &&
                row.suggestedTeamSlotDisplayValue.isBlank() &&
                row.detectedPlayerNameEvidenceLabel.contains(MATCH_RESULT_NOT_DETECTED_PLAYER) &&
                row.playerKillEvidence.isEmpty()
        })
        assertTrue(restored.correctionDraft!!.rows.all { it.playerKillDrafts.isEmpty() })
    }

    @Test
    fun restoredCalculatedEvidenceCreatesIndividualKillEvidenceOnlyForDetectedPlayers() {
        val evidence = MatchCalculatedEvidence(
            result = ResultCalculatedEvidence(
                positions = listOf(
                    ResultPositionCalculatedEvidence(
                        position = 1,
                        playerNames = listOf("P1", "Not detected", "P3", null),
                        playerKillApplicable = listOf(true, false, true, false),
                        playerKills = listOf(2, null, 4, null),
                        totalKills = 6,
                        placement = 1,
                        slotNumber = 12,
                    ),
                ),
            ),
        )

        val restored = evidence.toRestoredOcrReviewUiState(
            tournamentId = "tournament-1",
            matchId = "match-1",
            teamNamesBySlot = emptyMap(),
        ) as MatchOcrReviewUiState.Ready

        assertEquals(listOf(1, 3), restored.rows.single().playerKillEvidence.map { it.playerSlot })
        assertEquals(
            listOf(1, 3),
            restored.correctionDraft!!.rows.single().playerKillDrafts.map { it.playerSlot },
        )
    }

    @Test
    fun manualCorrectionUpdatesGeometrylessStableRowIdentity() {
        val (reviewState, sourceOcrState) = mapperInput()
        val blankRows = MatchResultOcrPreviewUiStateMapper.manualFallbackRows()
        val initialEvidence = requireNotNull(MatchCalculatedEvidenceMapper.initialResultWorkingSet(reviewState))
        var draft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(blankRows)
        draft = MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(draft, 9, "3")
        draft = MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 9, "4")
        draft = MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(draft, 9, "7")

        val corrected = MatchCalculatedEvidenceMapper.applyAcceptedResultCorrections(
            evidence = initialEvidence,
            reviewState = reviewState.copy(resultPositionCropPreviews = emptyMap()),
            ocrState = sourceOcrState.copy(
                rows = blankRows,
                correctionDraft = draft,
                matchResultOcrPreview = MatchResultOcrPreviewUiState.Empty,
                phase1LobbySlotNumberOcr = null,
            ),
        )
        val row = corrected.result.positions.single { it.position == 10 }

        assertEquals(10, row.position)
        assertEquals(3, row.placement)
        assertEquals(7, row.slotNumber)
        assertEquals(4, row.totalKills)
    }

    @Test
    fun appliesPositionCorrectionByRoleAndExactBounds() {
        val (reviewState, ocrState) = mapperInput()
        val evidence = requireNotNull(MatchCalculatedEvidenceMapper.map(reviewState, ocrState))
        val corrected = MatchCalculatedEvidenceMapper.applyAcceptedResultCorrections(
            evidence = evidence,
            reviewState = reviewState,
            ocrState = ocrState.withCorrection { draft ->
                MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(draft, 11, "7")
            },
        )

        val result = corrected.result.positions.single { it.position == 12 }
        assertEquals(12, result.position)
        assertEquals(7, result.placement)
        assertEquals(evidence.result.positions.single { it.position == 12 }.sourceScreenshotRole, result.sourceScreenshotRole)
        assertEquals(
            listOf(11, 12, 31, 42),
            listOf(result.cropLeft, result.cropTop, result.cropRight, result.cropBottom),
        )
        assertEquals(evidence.result.positions.single { it.position == 12 }.playerNames, result.playerNames)
        assertEquals(evidence.lobby, corrected.lobby)
    }

    @Test
    fun appliesIndependentKillCorrectionWithoutDerivingFromPlayerKills() {
        val (reviewState, ocrState) = mapperInput()
        val evidence = requireNotNull(MatchCalculatedEvidenceMapper.map(reviewState, ocrState))
        val corrected = MatchCalculatedEvidenceMapper.applyAcceptedResultCorrections(
            evidence = evidence,
            reviewState = reviewState,
            ocrState = ocrState.withCorrection { draft ->
                MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, 11, "9")
            },
        )

        assertEquals(9, corrected.result.positions.single { it.position == 12 }.totalKills)
        assertEquals(
            evidence.result.positions.single { it.position == 12 }.playerKills,
            corrected.result.positions.single { it.position == 12 }.playerKills,
        )
    }

    @Test
    fun appliesPlayerKillCorrectionAndProductionDerivedTotal() {
        val (reviewState, sourceOcrState) = mapperInput()
        val ocrState = sourceOcrState.copy(
            rows = listOf(
                reviewRow(12).copy(
                    playerKillEvidence = listOf(
                        MatchOcrReviewPlayerKillEvidenceUiState(1, "2"),
                        MatchOcrReviewPlayerKillEvidenceUiState(3, "5"),
                    ),
                ),
            ),
        )
        val evidence = requireNotNull(MatchCalculatedEvidenceMapper.map(reviewState, ocrState))
        val corrected = MatchCalculatedEvidenceMapper.applyAcceptedResultCorrections(
            evidence = evidence,
            reviewState = reviewState,
            ocrState = ocrState.withCorrection { draft ->
                MatchOcrReviewCorrectionDraftReducer.onPlayerKillsChanged(draft, 11, 3, "6")
            },
        )

        assertEquals(
            listOf(2, null, 6, null),
            corrected.result.positions.single { it.position == 12 }.playerKills,
        )
        assertEquals(8, corrected.result.positions.single { it.position == 12 }.totalKills)
    }

    @Test
    fun appliesSlotCorrectionAndUsesExistingResultTeamName() {
        val (reviewState, ocrState) = mapperInput()
        val evidence = requireNotNull(MatchCalculatedEvidenceMapper.map(reviewState, ocrState))
        val corrected = MatchCalculatedEvidenceMapper.applyAcceptedResultCorrections(
            evidence = evidence,
            reviewState = reviewState,
            ocrState = ocrState.withCorrection { draft ->
                MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(draft, 11, "8")
            },
        )

        assertEquals(8, corrected.result.positions.single { it.position == 12 }.slotNumber)
        assertEquals("Team 8", corrected.result.positions.single { it.position == 12 }.teamName)
    }

    private fun MatchOcrReviewUiState.Ready.withCorrection(
        transform: (MatchOcrReviewCorrectionDraft) -> MatchOcrReviewCorrectionDraft,
    ): MatchOcrReviewUiState.Ready {
        val initialDraft = MatchOcrReviewCorrectionDraftReducer.createInitialDraft(rows)
        return copy(correctionDraft = transform(initialDraft))
    }

    private fun mapperInput(): Pair<MatchReviewUiState, MatchOcrReviewUiState.Ready> {
        val reviewState = MatchReviewUiState(
            isLoading = false,
            isAvailable = true,
            tournamentId = "tournament-1",
            matchId = "match-1",
            rows = (1..12).map { slot ->
                MatchReviewRowUiState(
                    teamSlotNumber = slot,
                    teamName = "Team $slot",
                )
            },
            resultPositionCropPreviews = mapOf(
                MatchResultScreenshotRole.MATCH_RESULT_LOWER to
                    MatchResultPositionCropPreviewState.Available(
                        listOf(
                            MatchResultPositionCropPreview(
                                position = 12,
                                image = FakeResultImage,
                                geometry = MatchResultPositionCrop(
                                    position = 12,
                                    column = MatchResultPositionColumn.RIGHT,
                                    bounds = OcrPixelCropRect(11, 12, 31, 42),
                                ),
                                sourceScreenshotRole = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                            ),
                        ),
                    ),
            ),
        )
        val resultPreview = MatchResultOcrPreviewUiState.Ready(
            roles = listOf(MatchResultScreenshotRole.MATCH_RESULT_LOWER),
            rows = listOf(
                MatchResultOcrPreviewRowUiState(
                    position = 12,
                    role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
                    sourceLabel = "LOWER",
                    placementText = "12",
                    slots = listOf(
                        resultSlot(1, "Result P1", "2"),
                        resultSlot(3, "Result P3", "5"),
                    ),
                ),
            ),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        val ocrState = MatchOcrReviewUiState.Ready(
            tournamentId = "tournament-1",
            matchId = "match-1",
            rowCount = 12,
            rows = listOf(reviewRow(12)),
            blockerCount = 0,
            warningCount = 0,
            safeRowCount = 1,
            manualRequiredRowCount = 0,
            reviewRequiredRowCount = 0,
            manualReviewRequired = false,
            hasUnavailableEvidence = false,
            matchResultOcrPreview = resultPreview,
            teamNamesBySlot = (1..12).associateWith { slot -> "Team $slot" },
            phase1LobbySlotNumberOcr = lobbyResult(),
        )
        return reviewState to ocrState
    }

    private fun lobbyResult(): MatchLobbySlotNumberOcrResult = MatchLobbySlotNumberOcrResult(
        screenshots = listOf(
            MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                RosterScreenshotPosition.ONE,
                MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE,
            ),
            MatchLobbySlotNumberOcrScreenshotResult.Unavailable(
                RosterScreenshotPosition.TWO,
                MatchLobbySlotNumberOcrUnavailableReason.ASSET_UNAVAILABLE,
            ),
            MatchLobbySlotNumberOcrScreenshotResult.Processed(
                screenshotPosition = RosterScreenshotPosition.THREE,
                slots = RosterVisibleSlotPosition.entries.map { visiblePosition ->
                    MatchLobbySlotNumberOcrSlot(visiblePosition, RosterSlotNumberCandidate.unavailable())
                },
                teamCropPreviews = MatchLobbyTeamCropPreviewResult.Available(
                    previews = listOf(
                        MatchLobbyTeamCropPreview(
                            visibleSlotPosition = RosterVisibleSlotPosition.BOTTOM_RIGHT,
                            detectedSlotNumber = 12,
                            image = FakeLobbyImage,
                            playerRowPreviews = listOf(
                                lobbyPlayer(1, "Lobby P1", null),
                                lobbyPlayer(3, null, "Lobby P3"),
                            ),
                            bounds = LobbyTeamCropBounds(1.25, 2.5, 30.75, 40.0),
                        ),
                    ),
                    unavailable = RosterVisibleSlotPosition.entries
                        .filterNot { it == RosterVisibleSlotPosition.BOTTOM_RIGHT }
                        .map { visiblePosition ->
                            MatchLobbyTeamCropPreviewOutcome.Unavailable(
                                visibleSlotPosition = visiblePosition,
                                reason = MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE,
                            )
                        },
                ),
            ),
        ),
    )

    private fun lobbyPlayer(row: Int, playerName: String?, structuralEvidence: String?): LobbyPlayerRowCropPreview =
        LobbyPlayerRowCropPreview(
            row = LobbyPlayerRow.entries[row - 1],
            boundsInTeamCrop = LobbyPlayerRowCropBounds(1, row, 10, row + 1),
            slotAnchorSource = LobbySlotAnchorSource.PP_OCR_SLOT,
            slotAnchorY = row.toDouble(),
            structuralEvidence = structuralEvidence,
            playerName = playerName,
        )

    private fun resultSlot(slot: Int, playerText: String, killText: String): MatchResultOcrPreviewSlotUiState =
        MatchResultOcrPreviewSlotUiState(
            slot = slot,
            playerText = playerText,
            playerOcrText = playerText,
            playerStatusLabel = "processed",
            killText = killText,
            killOcrText = killText,
            killStatusLabel = "processed",
        )

    private fun reviewRow(position: Int): MatchOcrReviewRowUiState = MatchOcrReviewRowUiState(
        rowIndex = position - 1,
        expectedPlacementLabel = position.toString(),
        detectedPlacementDisplayValue = position.toString(),
        placementStatusLabel = "processed",
        detectedKillDisplayValue = "7",
        killStatusLabel = "processed",
        detectedPlayerNameEvidenceLabel = "P1 Result P1, P3 Result P3",
        playerNameStatusLabel = "processed",
        suggestedTeamSlotDisplayValue = "12",
        confidenceScoreDisplayValue = "100",
        confidenceTierLabel = "HIGH",
        assignmentSafetyStatusLabel = "SAFE",
        topThreeSuggestionsSummary = emptyList(),
        warningLabels = emptyList(),
        blockerLabels = emptyList(),
        severity = MatchOcrReviewSeverity.INFORMATIONAL,
    )

    private data object FakeLobbyImage : com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewImage
    private data object FakeResultImage : MatchResultPositionCropPreviewImage
}
