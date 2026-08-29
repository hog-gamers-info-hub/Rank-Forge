package com.hoggamers.rankforge.data.ocr.matchlobby

import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyOcrCacheFingerprint
import com.hoggamers.rankforge.data.local.MatchLobbyOcrCacheRepository
import com.hoggamers.rankforge.data.local.identityOrNull
import com.hoggamers.rankforge.presentation.screen.NoOpScreenshotOwnerProvider
import com.hoggamers.rankforge.presentation.screen.ScreenshotOwnerProvider
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyResolvedSlotGroup
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
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

const val MATCH_LOBBY_OCR_CACHE_PIPELINE_VERSION = 7

fun interface MatchLobbyPlayersOcrRunner {
    suspend fun process(tournamentId: String, matchId: String): MatchLobbyPlayersOcrResult
}

@Singleton
class AndroidMatchLobbyPlayersOcrRunner @Inject constructor(
    private val assetRepository: MatchLobbyScreenshotAssetRepository,
    private val cacheRepository: MatchLobbyOcrCacheRepository,
    private val slotNumberOcrRunner: MatchLobbySlotNumberOcrRunner,
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

        val cachedContributions = mutableMapOf<RosterScreenshotPosition, MatchLobbyScreenshotContribution>()
        val fingerprints = mutableMapOf<RosterScreenshotPosition, MatchLobbyOcrCacheFingerprint?>()
        val uncachedPositions = mutableSetOf<RosterScreenshotPosition>()
        RosterScreenshotPosition.entries.forEach { position ->
            val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, position.index)
            val asset = try {
                assetRepository.getByIdentityAndOwner(identity, ownerUserId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            val fingerprint = asset?.toMatchLobbyOcrCacheFingerprint(identity, position)
            fingerprints[position] = fingerprint
            val cached = fingerprint?.let { value ->
                try {
                    cacheRepository.readByOwner(value, ownerUserId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    null
                }
            }
            val contribution = cached?.toContribution()
            if (contribution != null) cachedContributions[position] = contribution
            else uncachedPositions += position
        }

        val freshScreenshots = if (uncachedPositions.isEmpty()) {
            emptyMap()
        } else {
            val fresh = try {
                slotNumberOcrRunner.process(tournamentId, matchId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                null
            }
            fresh?.screenshots
                ?.filter { it.screenshotPosition in uncachedPositions && fingerprints[it.screenshotPosition] != null }
                ?.associateBy { it.screenshotPosition }
                .orEmpty()
        }
        val contributions = RosterScreenshotPosition.entries.mapNotNull { position ->
            cachedContributions[position] ?: freshScreenshots[position]
                ?.toContribution()
                ?.also { contribution ->
                    fingerprints[position]?.let { fingerprint ->
                        try {
                            cacheRepository.saveByOwner(fingerprint, contribution.slots, ownerUserId)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Throwable) {
                            // Cache persistence is an optimization and must not block OCR Review.
                        }
                    }
                }
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

    private fun MatchLobbySlotNumberOcrScreenshotResult.toContribution(): MatchLobbyScreenshotContribution? {
        val processed = this as? MatchLobbySlotNumberOcrScreenshotResult.Processed ?: return null
        val previews = (processed.teamCropPreviews as? MatchLobbyTeamCropPreviewResult.Available)
            ?.previews
            ?: return null
        val slots = previews.map { preview ->
            val slotNumber = preview.authoritativeTeamSlotNumber
                .takeIf { it in 1..12 }
                ?: processed.screenshotPosition.tournamentSlotFor(preview.visibleSlotPosition)
            MatchLobbyPlayersOcrSlot(
                slotNumber = slotNumber,
                players = LobbyPlayerRow.entries.map { row ->
                    val rowPreview = preview.playerRowPreviews.firstOrNull { it.row == row }
                    MatchLobbyPlayersOcrPlayer(
                        playerNumber = row.ordinal + 1,
                        playerName = rowPreview?.playerName
                            ?: rowPreview?.structuralEvidence
                            ?.trim()
                            ?.takeIf { it.isNotBlank() },
                    )
                },
            )
        }
        if (slots.size != RosterVisibleSlotPosition.entries.size) return null
        return slots.toContribution()
    }

    private fun Set<Int>.toIntRange(): IntRange = minOrNull()!!..maxOrNull()!!

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
