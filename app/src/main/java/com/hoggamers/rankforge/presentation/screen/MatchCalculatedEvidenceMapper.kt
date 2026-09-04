package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.LobbyCalculatedEvidence
import com.hoggamers.rankforge.data.local.LobbyTeamCalculatedEvidence
import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultCalculatedEvidence
import com.hoggamers.rankforge.data.local.ResultPositionCalculatedEvidence
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbySlotNumberOcrScreenshotResult
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.tournament.TeamSlot

internal const val MATCH_RESULT_NOT_DETECTED_PLAYER = "Not detected"

internal object MatchCalculatedEvidenceMapper {
    private val LOGICAL_RESULT_POSITIONS = 1..12

    fun map(
        reviewState: MatchReviewUiState,
        ocrState: MatchOcrReviewUiState.Ready,
    ): MatchCalculatedEvidence? {
        if (!reviewState.isAvailable ||
            reviewState.tournamentId != ocrState.tournamentId ||
            reviewState.matchId != ocrState.matchId
        ) {
            return null
        }

        val lobbyTeams = ocrState.phase1LobbySlotNumberOcr
            ?.toLobbyCalculatedEvidence(reviewState)
            .orEmpty()
        val resultPositions = ocrState.toResultCalculatedEvidence(reviewState)

        return MatchCalculatedEvidence(
            lobby = LobbyCalculatedEvidence(teams = lobbyTeams),
            result = ResultCalculatedEvidence(
                positions = resultPositions,
                excludedSourcePositions = ocrState.correctionDraft?.rows
                    ?.filter { it.isExcluded }
                    ?.map { it.rowIndex + 1 }
                    .orEmpty(),
            ),
        )
    }

    fun initialResultWorkingSet(reviewState: MatchReviewUiState): MatchCalculatedEvidence? {
        if (!reviewState.isAvailable ||
            reviewState.tournamentId.isNullOrBlank() ||
            reviewState.matchId.isNullOrBlank()
        ) {
            return null
        }
        return MatchCalculatedEvidence(
            result = ResultCalculatedEvidence(
                positions = LOGICAL_RESULT_POSITIONS.map { position ->
                    ResultPositionCalculatedEvidence(
                        position = position,
                        playerNames = List(4) { MATCH_RESULT_NOT_DETECTED_PLAYER },
                        placement = null,
                        playerKillApplicable = List(4) { false },
                    )
                },
            ),
        )
    }

    /** Applies accepted Result correction-draft values without changing evidence identity. */
    fun applyAcceptedResultCorrections(
        evidence: MatchCalculatedEvidence,
        reviewState: MatchReviewUiState,
        ocrState: MatchOcrReviewUiState.Ready,
    ): MatchCalculatedEvidence {
        if (!reviewState.isAvailable ||
            reviewState.tournamentId != ocrState.tournamentId ||
            reviewState.matchId != ocrState.matchId
        ) {
            return evidence
        }

        val correctionDraft = ocrState.correctionDraft ?: return evidence
        val updatedPositions = evidence.result.positions.map { savedPosition ->
            val rowDraft = correctionDraft.rows.singleOrNull { row ->
                row.rowIndex == savedPosition.position - 1
            } ?: return@map savedPosition
            if (rowDraft.isExcluded) return@map savedPosition

            val acceptedPlacement = rowDraft.placementDraftValue
                .trim()
                .toIntOrNull()
                ?.takeIf { it in TeamSlot.SLOT_NUMBERS }
            val acceptedSlot = rowDraft.assignedTeamSlotDraftValue
                .trim()
                .toIntOrNull()
                ?.takeIf { it in TeamSlot.SLOT_NUMBERS }
            val acceptedSlotValue = if (rowDraft.assignedTeamSlotDraftValue.isBlank()) {
                null
            } else {
                acceptedSlot ?: savedPosition.slotNumber
            }
            val acceptedPlayerKills = savedPosition.playerKills.mapIndexed { index, previous ->
                val playerDraft = rowDraft.playerKillDrafts
                    .singleOrNull { player -> player.playerSlot == index + 1 }
                when {
                    playerDraft == null -> previous
                    playerDraft.killsDraftValue.isBlank() -> null
                    else -> playerDraft.killsDraftValue
                        .trim()
                        .toIntOrNull()
                        ?.takeIf { it >= 0 }
                        ?: previous
                }
            }
            val acceptedTotalKills = if (rowDraft.killsDraftValue.isBlank()) {
                null
            } else {
                rowDraft.killsDraftValue
                    .trim()
                    .toIntOrNull()
                    ?.takeIf { it >= 0 }
                    ?: savedPosition.totalKills
            }
            val acceptedTeamName = if (acceptedSlotValue != savedPosition.slotNumber) {
                ocrState.teamNamesBySlot[acceptedSlotValue].cleanDisplayedText()
            } else savedPosition.teamName

            savedPosition.copy(
                placement = acceptedPlacement,
                slotNumber = acceptedSlotValue,
                teamName = acceptedTeamName,
                playerKills = acceptedPlayerKills,
                totalKills = acceptedTotalKills,
            )
        }
        return evidence.copy(
            result = evidence.result.copy(
                positions = updatedPositions,
                excludedSourcePositions = correctionDraft.rows
                    .filter { it.isExcluded }
                    .map { it.rowIndex + 1 },
            ),
        )
    }

    private fun MatchLobbySlotNumberOcrResult.toLobbyCalculatedEvidence(
        reviewState: MatchReviewUiState,
    ): List<LobbyTeamCalculatedEvidence> = screenshots.flatMap { screenshot ->
        val teamNamesBySlot = reviewState.rows.associate { row ->
            row.teamSlotNumber to row.teamName
        }
        when (screenshot) {
            is MatchLobbySlotNumberOcrScreenshotResult.Processed -> {
                val previews = (screenshot.teamCropPreviews as? MatchLobbyTeamCropPreviewResult.Available)
                    ?.previews
                    .orEmpty()
                previews.mapNotNull { preview ->
                    val bounds = preview.bounds ?: return@mapNotNull null
                    val slotNumber = preview.detectedSlotNumber
                        .takeIf { it in TeamSlot.SLOT_NUMBERS }
                        ?: return@mapNotNull null
                    LobbyTeamCalculatedEvidence(
                        slotNumber = slotNumber,
                        teamName = teamNamesBySlot[slotNumber]
                            .cleanDisplayedText(),
                        sourceScreenshotIndex = screenshot.screenshotPosition.index,
                        cropLeft = bounds.left,
                        cropTop = bounds.top,
                        cropRight = bounds.right,
                        cropBottom = bounds.bottom,
                        playerNames = (1..4).map { playerNumber ->
                            preview.playerRowPreviews
                                .firstOrNull { it.row.ordinal + 1 == playerNumber }
                                ?.let { rowPreview ->
                                    (rowPreview.playerName ?: rowPreview.structuralEvidence)
                                        .cleanDisplayedText()
                                }
                        },
                    )
                }
            }
            is MatchLobbySlotNumberOcrScreenshotResult.Unavailable -> emptyList()
        }
    }

    private fun MatchOcrReviewUiState.Ready.toResultCalculatedEvidence(
        reviewState: MatchReviewUiState,
    ): List<ResultPositionCalculatedEvidence> {
        val preview = matchResultOcrPreview as? MatchResultOcrPreviewUiState.Ready
        val reviewRowsByPosition = rows.associateBy { it.rowIndex + 1 }
        val resultTeamNamesBySlot = this.teamNamesBySlot
        val correctionDraft = this.correctionDraft
        val cropsByPosition = reviewState.resultPositionCropPreviews
            .flatMap { (storedRole, state) ->
                state.sortedCrops().map { storedRole to it }
            }
            .groupBy { (_, crop) -> crop.position }
        val rowsByPosition = preview?.rows.orEmpty().groupBy { it.position }
        return LOGICAL_RESULT_POSITIONS.map { position ->
            val cropAndRole = cropsByPosition[position]
                ?.firstOrNull { (storedRole, crop) ->
                    crop.geometry?.bounds != null &&
                        (crop.sourceScreenshotRole ?: storedRole) in MatchResultScreenshotRole.entries
                }
            val sourceRole = cropAndRole?.let { (storedRole, crop) ->
                crop.sourceScreenshotRole ?: storedRole
            }
            val bounds = cropAndRole?.second?.geometry?.bounds
            val previewRow = rowsByPosition[position]
                ?.firstOrNull { row -> sourceRole == null || row.role == sourceRole }
                ?: rowsByPosition[position]?.firstOrNull()
            val reviewRow = reviewRowsByPosition[position]
            val draft = correctionDraft?.rows?.singleOrNull { row ->
                row.rowIndex == position - 1
            }
            val assignedSlot = draft?.assignedTeamSlotDraftValue
                ?.parseTeamSlotOrNull()
                ?: reviewRow?.suggestedTeamSlotDisplayValue.parseTeamSlotOrNull()
            val placement = draft?.placementDraftValue
                ?.parseTeamSlotOrNull()
                ?: reviewRow?.detectedPlacementDisplayValue.parseTeamSlotOrNull()
            val playerKills = (1..4).map { playerSlot ->
                val draftPlayer = draft?.playerKillDrafts
                    ?.singleOrNull { player -> player.playerSlot == playerSlot }
                when {
                    draftPlayer != null -> draftPlayer.killsDraftValue.parseNonNegativeIntOrNull()
                    else -> previewRow?.slots
                        ?.firstOrNull { it.slot == playerSlot }
                        ?.killText
                        ?.parseNonNegativeIntOrNull()
                }
            }
            ResultPositionCalculatedEvidence(
                position = position,
                sourceScreenshotRole = sourceRole,
                cropLeft = bounds?.left,
                cropTop = bounds?.top,
                cropRight = bounds?.right,
                cropBottom = bounds?.bottom,
                slotNumber = assignedSlot,
                teamName = assignedSlot?.let { slot -> resultTeamNamesBySlot[slot].cleanDisplayedText() },
                playerNames = (1..4).map { playerSlot ->
                    previewRow?.slots
                        ?.firstOrNull { it.slot == playerSlot }
                        ?.playerText
                        .cleanDisplayedText()
                        ?: MATCH_RESULT_NOT_DETECTED_PLAYER
                },
                playerKillApplicable = (1..4).map { playerSlot ->
                    previewRow?.slots
                        ?.firstOrNull { it.slot == playerSlot }
                        ?.isPlayerKillApplicable() == true
                },
                playerKills = playerKills,
                totalKills = draft?.killsDraftValue
                    ?.parseNonNegativeIntOrNull()
                    ?: reviewRow?.detectedKillDisplayValue.parseNonNegativeIntOrNull(),
                placement = placement,
            )
        }
    }

    private fun String?.cleanDisplayedText(): String? = this
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun String?.parseTeamSlotOrNull(): Int? = this
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it in TeamSlot.SLOT_NUMBERS }

    private fun String?.parseNonNegativeIntOrNull(): Int? = this
        ?.trim()
        ?.toIntOrNull()
        ?.takeIf { it >= 0 }
}
