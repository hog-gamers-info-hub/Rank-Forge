package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrLocalRelativePath
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrScreenshotSource
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class MatchLobbyPlayersOcrPlayer(
    val playerNumber: Int,
    val playerName: String?,
)

data class MatchLobbyPlayersOcrSlot(
    val slotNumber: Int,
    val players: List<MatchLobbyPlayersOcrPlayer>,
)

data class MatchLobbyPlayersOcrResult(
    val slots: List<MatchLobbyPlayersOcrSlot>,
) {
    companion object {
        fun unavailable(): MatchLobbyPlayersOcrResult = MatchLobbyPlayersOcrResult(
            slots = (1..12).map { slotNumber ->
                MatchLobbyPlayersOcrSlot(
                    slotNumber = slotNumber,
                    players = (1..4).map { playerNumber ->
                        MatchLobbyPlayersOcrPlayer(playerNumber, null)
                    },
                )
            },
        )
    }
}

fun interface MatchLobbyPlayersOcrRunner {
    suspend fun process(tournamentId: String, matchId: String): MatchLobbyPlayersOcrResult
}

@Singleton
class AndroidMatchLobbyPlayersOcrRunner @Inject constructor(
    private val assetRepository: MatchLobbyScreenshotAssetRepository,
    private val panelPreparer: RosterOcrPanelPreparer,
    private val extractor: RosterRawOcrExtractor,
    private val parser: RosterCandidateParser,
    private val associator: RosterSlotAssociator,
) : MatchLobbyPlayersOcrRunner {
    override suspend fun process(
        tournamentId: String,
        matchId: String,
    ): MatchLobbyPlayersOcrResult {
        if (tournamentId.isBlank() || matchId.isBlank()) {
            return MatchLobbyPlayersOcrResult.unavailable()
        }

        val slots = MatchLobbyPlayersOcrResult.unavailable().slots.toMutableList()
        RosterScreenshotPosition.entries.forEach { position ->
            processScreenshot(tournamentId, matchId, position).forEach { slot ->
                slots[slot.slotNumber - 1] = slot
            }
        }
        return MatchLobbyPlayersOcrResult(slots)
    }

    private suspend fun processScreenshot(
        tournamentId: String,
        matchId: String,
        position: RosterScreenshotPosition,
    ): List<MatchLobbyPlayersOcrSlot> {
        val unavailable = position.tournamentSlotRange.map { slotNumber ->
            MatchLobbyPlayersOcrSlot(
                slotNumber = slotNumber,
                players = (1..4).map { playerNumber -> MatchLobbyPlayersOcrPlayer(playerNumber, null) },
            )
        }
        val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, position.index)
        val asset = try {
            assetRepository.getByIdentity(identity)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return unavailable
        } ?: return unavailable

        val source = asset.toRosterSource(position, identity) ?: return unavailable
        val prepared = try {
            panelPreparer.prepare(source)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return unavailable
        }
        val panel = when (prepared) {
            is RosterOcrPanelPreparationResult.Failed -> return unavailable
            is RosterOcrPanelPreparationResult.Prepared -> prepared.panel
        }

        var extraction: List<RosterRawOcrExtractionResult>? = null
        var extractionFailure: Throwable? = null
        try {
            extraction = extractor.extract(
                RosterRawOcrExtractionInput(
                    croppedPanelImage = panel.croppedPanelImage,
                    croppedPanelInput = panel.croppedPanelInput,
                ),
            )
        } catch (cancellation: CancellationException) {
            extractionFailure = cancellation
        } catch (_: Throwable) {
            extractionFailure = ExtractionFailure
        }

        var releaseFailure: Throwable? = null
        try {
            panel.release()
        } catch (cancellation: CancellationException) {
            releaseFailure = cancellation
        } catch (_: Throwable) {
            releaseFailure = ReleaseFailure
        }
        extractionFailure?.let { failure ->
            if (failure is CancellationException) throw failure
            return unavailable
        }
        releaseFailure?.let { failure ->
            if (failure is CancellationException) throw failure
            return unavailable
        }

        val parsed = try {
            parser.parse(RosterCandidateParseInput(requireNotNull(extraction)))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return unavailable
        }
        val associated = try {
            associator.associate(RosterSlotAssociationInput(parsed))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return unavailable
        }
        val bySlot = associated.tournamentSlotCandidates.associateBy { it.tournamentSlotNumber }
        return position.tournamentSlotRange.map { slotNumber ->
            val candidate = bySlot[slotNumber]
            MatchLobbyPlayersOcrSlot(
                slotNumber = slotNumber,
                players = (1..4).map { playerNumber ->
                    val player = candidate?.playerNameCandidates
                        ?.firstOrNull { it.playerRowIndex == playerNumber }
                    MatchLobbyPlayersOcrPlayer(
                        playerNumber = playerNumber,
                        playerName = player?.candidateText
                            ?.trim()
                            ?.takeIf { player.status == RosterCandidateParseStatus.PARSED && it.isNotBlank() },
                    )
                },
            )
        }
    }

    private fun MatchLobbyScreenshotAssetEntity.toRosterSource(
        position: RosterScreenshotPosition,
        identity: MatchLobbyScreenshotIdentity,
    ): RosterOcrScreenshotSource? {
        if (identity.tournamentId != tournamentId || identity.matchId != matchId ||
            identity.lobbyScreenshotIndex != lobbyScreenshotIndex ||
            cropProfileId != OcrCropValidationProfiles.Lobby.id ||
            cropLeft == null || cropTop == null || cropRight == null || cropBottom == null
        ) return null
        return RosterOcrScreenshotSource(
            tournamentId = tournamentId,
            rosterScreenshotIndex = lobbyScreenshotIndex,
            screenshotPosition = position,
            localRelativePath = RosterOcrLocalRelativePath(localRelativePath),
            sourceWidth = originalWidth,
            sourceHeight = originalHeight,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom,
        )
    }

    private object ExtractionFailure : Throwable()
    private object ReleaseFailure : Throwable()
}
