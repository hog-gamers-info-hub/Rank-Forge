package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.ResultPositionCalculatedEvidence
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewOutcome
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerRowCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.createAndroidMatchLobbyTeamCropPreviewImage
import com.hoggamers.rankforge.data.ocr.matchlobby.toRosterOcrScreenshotSource
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelCropRect
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCrop
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyTeamCropBounds
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionColumn
import com.hoggamers.rankforge.domain.ocr.matchresult.MatchResultPositionCrop
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class RestoredMatchCalculatedEvidencePreviews(
    val lobbyTeamCropPreviewsByScreenshotIndex: Map<Int, MatchLobbyTeamCropPreviewResult>,
    val resultPositionCropPreviews: Map<MatchResultScreenshotRole, MatchResultPositionCropPreviewState>,
    val lobbyTeamNamesBySlot: Map<Int, String>,
)

interface MatchCalculatedEvidencePreviewRestorer {
    suspend fun restore(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        evidence: MatchCalculatedEvidence,
    ): RestoredMatchCalculatedEvidencePreviews?
}

@Singleton
class AndroidMatchCalculatedEvidencePreviewRestorer @Inject constructor(
    private val lobbyScreenshotAssetRepository: MatchLobbyScreenshotAssetRepository,
    private val resultScreenshotAssetRepository: MatchResultScreenshotAssetRepository,
    private val localImagePreserver: LocalImagePreserver,
    private val rosterOcrPanelPreparer: RosterOcrPanelPreparer,
) : MatchCalculatedEvidencePreviewRestorer {
    override suspend fun restore(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        evidence: MatchCalculatedEvidence,
    ): RestoredMatchCalculatedEvidencePreviews? {
        val lobby = try {
            restoreLobby(tournamentId, matchId, ownerUserId, evidence).orEmpty()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            emptyMap()
        }
        val result = try {
            restoreResult(tournamentId, matchId, ownerUserId, evidence)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            emptyMap()
        }
        return RestoredMatchCalculatedEvidencePreviews(
            lobbyTeamCropPreviewsByScreenshotIndex = lobby,
            resultPositionCropPreviews = result,
            lobbyTeamNamesBySlot = evidence.lobby.teams.mapNotNull { team ->
                val slot = team.slotNumber ?: return@mapNotNull null
                val name = team.teamName?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                slot to name
            }.toMap(),
        )
    }

    private suspend fun restoreLobby(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        evidence: MatchCalculatedEvidence,
    ): Map<Int, MatchLobbyTeamCropPreviewResult>? {
        if (evidence.lobby.teams.isEmpty()) return null
        val teamsByScreenshot = evidence.lobby.teams.groupBy { it.sourceScreenshotIndex }
        val restored = mutableMapOf<Int, MatchLobbyTeamCropPreviewResult>()
        teamsByScreenshot.forEach { (screenshotIndex, teams) ->
            val position = RosterScreenshotPosition.fromIndex(screenshotIndex) ?: return null
            val asset = lobbyScreenshotAssetRepository.getByIdentityAndOwner(
                MatchLobbyScreenshotIdentity(tournamentId, matchId, screenshotIndex),
                ownerUserId,
            ) ?: return null
            val source = asset.toRosterOcrScreenshotSource(position, MatchLobbyScreenshotIdentity(tournamentId, matchId, screenshotIndex))
                ?: return null
            val prepared = rosterOcrPanelPreparer.prepare(source)
            val panel = (prepared as? RosterOcrPanelPreparationResult.Prepared)?.panel ?: return null
            try {
                val panelImage = panel.croppedPanelImage
                val byVisiblePosition = mutableMapOf<RosterVisibleSlotPosition, MatchLobbyTeamCropPreview>()
                teams.forEach { team ->
                    val slot = team.slotNumber ?: return null
                    if (slot !in 1..12 || team.playerNames.size != 4) return null
                    val visiblePosition = RosterVisibleSlotPosition.entries.singleOrNull {
                        position.tournamentSlotFor(it) == slot
                    } ?: return null
                    if (byVisiblePosition.containsKey(visiblePosition)) return null
                    val bounds = LobbyTeamCropBounds(
                        team.cropLeft,
                        team.cropTop,
                        team.cropRight,
                        team.cropBottom,
                    ).takeIf { bounds ->
                        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).all(Double::isFinite) &&
                            bounds.right > bounds.left && bounds.bottom > bounds.top
                    } ?: return null
                    val crop = LobbyTeamCrop(visiblePosition, slot, bounds)
                    val rowPreviews = LobbyPlayerRow.entries.mapIndexed { index, row ->
                        LobbyPlayerRowCropPreview(
                            row = row,
                            boundsInTeamCrop = LobbyPlayerRowCropBounds(0, 0, 1, 1),
                            slotAnchorSource = LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK,
                            slotAnchorY = 0.5,
                            structuralEvidence = null,
                            playerName = team.playerNames[index]?.trim()?.takeIf { it.isNotBlank() },
                        )
                    }
                    byVisiblePosition[visiblePosition] = MatchLobbyTeamCropPreview(
                        visibleSlotPosition = visiblePosition,
                        detectedSlotNumber = slot,
                        image = createAndroidMatchLobbyTeamCropPreviewImage(panelImage, crop),
                        playerRowPreviews = rowPreviews,
                        authoritativeTeamSlotNumber = slot,
                        bounds = bounds,
                    )
                }
                restored[screenshotIndex] = MatchLobbyTeamCropPreviewResult.Available(
                    previews = byVisiblePosition.values.toList(),
                    unavailable = RosterVisibleSlotPosition.entries
                        .filterNot(byVisiblePosition::containsKey)
                        .map { visiblePosition ->
                            MatchLobbyTeamCropPreviewOutcome.Unavailable(
                                visibleSlotPosition = visiblePosition,
                                reason = com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE,
                            )
                        },
                )
            } finally {
                panel.release()
            }
        }
        return restored
    }

    private suspend fun restoreResult(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        evidence: MatchCalculatedEvidence,
    ): Map<MatchResultScreenshotRole, MatchResultPositionCropPreviewState> {
        val result = mutableMapOf<MatchResultScreenshotRole, MatchResultPositionCropPreviewState>()
        for ((role, positions) in evidence.result.positions
            .filter { position ->
                position.sourceScreenshotRole != null &&
                    position.cropLeft != null &&
                    position.cropTop != null &&
                    position.cropRight != null &&
                    position.cropBottom != null
            }
            .groupBy { requireNotNull(it.sourceScreenshotRole) }) {
                val restored = try {
                    restoreResultRole(
                        tournamentId = tournamentId,
                        matchId = matchId,
                        ownerUserId = ownerUserId,
                        role = role,
                        positions = positions,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
                if (restored != null) result[role] = restored
        }
        return MatchResultScreenshotRole.entries.associateWith { role ->
            result[role] ?: MatchResultPositionCropPreviewState.Unavailable(
                MatchResultPositionCropPreviewUnavailableReason.NOT_READY,
            )
        }
    }

    private suspend fun restoreResultRole(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        role: MatchResultScreenshotRole,
        positions: List<com.hoggamers.rankforge.data.local.ResultPositionCalculatedEvidence>,
    ): MatchResultPositionCropPreviewState? {
        val asset = resultScreenshotAssetRepository.getByIdentityAndOwner(
                MatchResultScreenshotIdentity(tournamentId, matchId, role = role),
                ownerUserId,
            ) ?: return null
        if (asset.cropProfileId != OcrCropValidationProfiles.MatchResult.id) return null
        val confirmedCrop = listOf(asset.cropLeft, asset.cropTop, asset.cropRight, asset.cropBottom)
            .takeIf { values -> values.all { it != null && it.isFinite() } }
            ?.let { values ->
                com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect(
                    values[0]!!, values[1]!!, values[2]!!, values[3]!!,
                )
            } ?: return null
        val localFile = localImagePreserver.resolveRelativePath(asset.localRelativePath)
            ?.takeIf { it.isFile && it.canRead() && it.length() > 0L }
            ?: return null
        val source = decodeConfirmedCrop(localFile, confirmedCrop) ?: return null
        try {
            val crops = positions.mapNotNull(ResultPositionCalculatedEvidence::toAuthoritativePositionCropOrNull)
                .takeIf { crops ->
                    crops.isNotEmpty() && crops.map { it.position }.distinct().size == crops.size
                } ?: return null
            return rasterizeResultPositionCropPreviews(
                source = source,
                sourceScreenshotRole = role,
                authoritativeCrops = crops,
            )
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private fun decodeConfirmedCrop(
        localFile: java.io.File,
        confirmedCrop: com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect,
    ): android.graphics.Bitmap? = decodeConfirmedCropForRestoration(localFile, confirmedCrop)
}

internal fun ResultPositionCalculatedEvidence.toAuthoritativePositionCropOrNull(): MatchResultPositionCrop? {
    sourceScreenshotRole ?: return null
    val left = cropLeft ?: return null
    val top = cropTop ?: return null
    val right = cropRight ?: return null
    val bottom = cropBottom ?: return null
    if (right <= left || bottom <= top) return null
    return MatchResultPositionCrop(
        position = position,
        column = if (position <= 5) MatchResultPositionColumn.LEFT else MatchResultPositionColumn.RIGHT,
        bounds = OcrPixelCropRect(left, top, right, bottom),
    )
}

internal object NoOpMatchCalculatedEvidencePreviewRestorer : MatchCalculatedEvidencePreviewRestorer {
    override suspend fun restore(
        tournamentId: String,
        matchId: String,
        ownerUserId: String,
        evidence: MatchCalculatedEvidence,
    ): RestoredMatchCalculatedEvidencePreviews? = null
}
