package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyOcrCacheFingerprint
import com.hoggamers.rankforge.data.local.MatchLobbyOcrCacheRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.presentation.screen.NoOpScreenshotOwnerProvider
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyResolvedSlotGroup
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotIdentityResolutionResult
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotIdentityResolver
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotContentSlotNumberExtractor
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotCandidate
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

const val MATCH_LOBBY_OCR_CACHE_PIPELINE_VERSION = 3

fun interface MatchLobbyPlayersOcrRunner {
    suspend fun process(tournamentId: String, matchId: String): MatchLobbyPlayersOcrResult
}

@Singleton
class AndroidMatchLobbyPlayersOcrRunner @Inject constructor(
    private val assetRepository: MatchLobbyScreenshotAssetRepository,
    private val cacheRepository: MatchLobbyOcrCacheRepository,
    private val panelPreparer: RosterOcrPanelPreparer,
    private val extractor: RosterRawOcrExtractor,
    private val parser: RosterCandidateParser,
    private val slotIdentityResolver: LobbySlotIdentityResolver,
    private val screenshotOwnerProvider: ScreenshotOwnerProvider = NoOpScreenshotOwnerProvider(),
) : MatchLobbyPlayersOcrRunner {
    override suspend fun process(
        tournamentId: String,
        matchId: String,
    ): MatchLobbyPlayersOcrResult {
        if (tournamentId.isBlank() || matchId.isBlank()) {
            return MatchLobbyPlayersOcrResult.unavailable()
        }
        val ownerUserId = screenshotOwnerProvider.currentOwnerUserId()?.takeIf { it.isNotBlank() }
            ?: return MatchLobbyPlayersOcrResult.unavailable()

        val contributions = RosterScreenshotPosition.entries.mapNotNull { position ->
            processScreenshot(tournamentId, matchId, position, ownerUserId)
        }
        val slots = MatchLobbyPlayersOcrResult.unavailable().slots.toMutableList()
        contributions
            .groupBy { contribution -> contribution.group.tournamentSlotRange }
            .values
            .filter { groupContributions -> groupContributions.size == 1 }
            .forEach { groupContributions ->
                groupContributions.single().slots.forEach { slot ->
                    slots[slot.slotNumber - 1] = slot
                }
            }
        return MatchLobbyPlayersOcrResult(slots)
    }

    private suspend fun processScreenshot(
        tournamentId: String,
        matchId: String,
        position: RosterScreenshotPosition,
        ownerUserId: String,
    ): MatchLobbyScreenshotContribution? {
        val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, position.index)
        val asset = try {
            assetRepository.getByIdentityAndOwner(identity, ownerUserId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return null
        } ?: run {
            return null
        }

        val source = asset.toRosterSource(position, identity) ?: run {
            return null
        }
        val fingerprint = asset.toMatchLobbyOcrCacheFingerprint(identity, position)
        if (fingerprint != null) {
            val cached = try {
                cacheRepository.readByOwner(fingerprint, ownerUserId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            if (cached != null && readFingerprint(identity, position, ownerUserId) == fingerprint) {
                val cachedContribution = cached.toContribution()
                if (cachedContribution != null) {
                    return cachedContribution
                }
            }
        }
        val prepared = try {
            panelPreparer.prepare(source)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return null
        }
        val panel = when (prepared) {
            is RosterOcrPanelPreparationResult.Failed -> {
                return null
            }
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
            return null
        }
        releaseFailure?.let { failure ->
            if (failure is CancellationException) throw failure
            return null
        }

        val extractionResults = requireNotNull(extraction)
        val parsed = try {
            parser.parse(RosterCandidateParseInput(extractionResults))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return null
        }
        val contentSlotNumberCandidates = LobbySlotContentSlotNumberExtractor.derive(extractionResults)
        val semanticSlots = parsed.slots.map { slot ->
            slot.copy(
                slotNumberCandidate = contentSlotNumberCandidates[slot.visibleSlotPosition]
                    ?: com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotNumberCandidate.unavailable(),
            )
        }
        val resolvedGroup = when (val resolution = slotIdentityResolver.resolve(semanticSlots)) {
            is LobbySlotIdentityResolutionResult.Resolved -> resolution.group
            is LobbySlotIdentityResolutionResult.Unresolved -> {
                return null
            }
        }
        val candidatesByVisiblePosition = semanticSlots.associateBy { it.visibleSlotPosition }
        val slots = resolvedGroup.slots.map { resolvedSlot ->
            val candidate = candidatesByVisiblePosition[resolvedSlot.visibleSlotPosition]
            MatchLobbyPlayersOcrSlot(
                slotNumber = resolvedSlot.tournamentSlotNumber,
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
        if (fingerprint != null && readFingerprint(identity, position, ownerUserId) == fingerprint) {
            try {
            cacheRepository.saveByOwner(fingerprint, slots, ownerUserId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Cache persistence is an optimization and must not block OCR Review.
            }
        }
        return MatchLobbyScreenshotContribution(resolvedGroup, slots)
    }

    private fun List<MatchLobbyPlayersOcrSlot>.toContribution(): MatchLobbyScreenshotContribution? {
        val slotNumbers = map { it.slotNumber }.toSet()
        val group = APPROVED_SEMANTIC_SLOT_GROUPS.singleOrNull { it == slotNumbers } ?: return null
        return MatchLobbyScreenshotContribution(
            group = LobbyResolvedSlotGroup(
                tournamentSlotRange = group.toIntRange(),
                slots = emptyList(),
                directlyDetectedCount = 0,
            ),
            slots = sortedBy { it.slotNumber },
        )
    }

    private fun Set<Int>.toIntRange(): IntRange = minOrNull()!!..maxOrNull()!!

    private suspend fun readFingerprint(
        identity: MatchLobbyScreenshotIdentity,
        position: RosterScreenshotPosition,
        ownerUserId: String,
    ): MatchLobbyOcrCacheFingerprint? = try {
        assetRepository.getByIdentityAndOwner(identity, ownerUserId)
            ?.toMatchLobbyOcrCacheFingerprint(identity, position)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
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

    private data class MatchLobbyScreenshotContribution(
        val group: LobbyResolvedSlotGroup,
        val slots: List<MatchLobbyPlayersOcrSlot>,
    )

    private companion object {
        val APPROVED_SEMANTIC_SLOT_GROUPS = listOf(
            (1..4).toSet(),
            (5..8).toSet(),
            (9..12).toSet(),
        )
    }
}

fun MatchLobbyScreenshotAssetEntity.toMatchLobbyOcrCacheFingerprint(
    identity: MatchLobbyScreenshotIdentity,
    position: RosterScreenshotPosition,
    pipelineVersion: Int = MATCH_LOBBY_OCR_CACHE_PIPELINE_VERSION,
): MatchLobbyOcrCacheFingerprint? {
    if (identityOrNull() != identity || position.index != identity.lobbyScreenshotIndex) return null
    if (sha256.isBlank() || originalWidth <= 0 || originalHeight <= 0) return null
    val profileId = cropProfileId?.takeIf { it.isNotBlank() } ?: return null
    val left = cropLeft ?: return null
    val top = cropTop ?: return null
    val right = cropRight ?: return null
    val bottom = cropBottom ?: return null
    if (!listOf(left, top, right, bottom).all(Double::isFinite)) return null
    if (right <= left || bottom <= top) return null

    return MatchLobbyOcrCacheFingerprint(
        tournamentId = identity.tournamentId,
        matchId = identity.matchId,
        screenshotPosition = position,
        screenshotSha256 = sha256,
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        cropProfileId = profileId,
        cropLeft = left,
        cropTop = top,
        cropRight = right,
        cropBottom = bottom,
        ocrPipelineVersion = pipelineVersion,
    )
}
