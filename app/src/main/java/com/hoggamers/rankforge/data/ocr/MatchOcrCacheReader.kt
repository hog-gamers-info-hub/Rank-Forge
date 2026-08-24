package com.hoggamers.rankforge.data.ocr

import com.hoggamers.rankforge.data.local.MatchLobbyOcrCacheRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultOcrCacheRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewProcessingResult
import com.hoggamers.rankforge.data.ocr.matchresult.MatchResultOcrPreviewRoleResult
import com.hoggamers.rankforge.data.ocr.matchresult.toMatchResultOcrCacheFingerprint
import com.hoggamers.rankforge.data.ocr.matchlobby.toMatchLobbyOcrCacheFingerprint
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthState
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class MatchOcrCacheAvailability {
    UNKNOWN,
    NOT_AVAILABLE,
    READY,
    STALE_OR_INCOMPLETE,
}

data class MatchOcrCacheReadResult(
    val availability: MatchOcrCacheAvailability,
    val resultRoleResults: List<MatchResultOcrPreviewRoleResult> = emptyList(),
    val lobbyResult: MatchLobbyPlayersOcrResult = MatchLobbyPlayersOcrResult.unavailable(),
)

fun interface MatchOcrCacheReader {
    suspend fun read(tournamentId: String, matchId: String): MatchOcrCacheReadResult
}

@Singleton
class RoomMatchOcrCacheReader @Inject constructor(
    private val resultScreenshotAssetRepository: MatchResultScreenshotAssetRepository,
    private val resultCacheRepository: MatchResultOcrCacheRepository,
    private val lobbyScreenshotAssetRepository: MatchLobbyScreenshotAssetRepository,
    private val lobbyCacheRepository: MatchLobbyOcrCacheRepository,
    private val authRepository: AuthRepository,
) : MatchOcrCacheReader {
    override suspend fun read(
        tournamentId: String,
        matchId: String,
    ): MatchOcrCacheReadResult {
        if (tournamentId.isBlank() || matchId.isBlank()) {
            return MatchOcrCacheReadResult(MatchOcrCacheAvailability.NOT_AVAILABLE)
        }
        val ownerUserId = (authRepository.observeAuthState().first() as? AuthState.SignedIn)
            ?.user?.id?.takeIf { it.isNotBlank() }
            ?: return MatchOcrCacheReadResult(MatchOcrCacheAvailability.NOT_AVAILABLE)

        val resultRoleResults = MatchResultScreenshotRole.entries.map { role ->
            val identity = MatchResultScreenshotIdentity(
                tournamentId = tournamentId,
                matchId = matchId,
                role = role,
            )
            val processed = readResult(identity, ownerUserId)
            MatchResultOcrPreviewRoleResult(
                role = role,
                result = processed ?: MatchResultOcrPreviewProcessingResult.MissingAsset,
            )
        }
        val lobbySlots = MatchLobbyPlayersOcrResult.unavailable().slots.toMutableList()
        var validLobbyCacheCount = 0
        RosterScreenshotPosition.entries.forEach { position ->
            val identity = MatchLobbyScreenshotIdentity(tournamentId, matchId, position.index)
            val cached = readLobby(identity, position, ownerUserId)
            if (cached != null) {
                validLobbyCacheCount++
                cached.forEach { slot ->
                    if (slot.slotNumber in 1..lobbySlots.size) {
                        lobbySlots[slot.slotNumber - 1] = slot
                    }
                }
            }
        }

        val allResultCachesValid = resultRoleResults.all {
            it.result is MatchResultOcrPreviewProcessingResult.Processed
        }
        val allLobbyCachesValid = validLobbyCacheCount == RosterScreenshotPosition.entries.size
        val validCacheCount = resultRoleResults.count {
            it.result is MatchResultOcrPreviewProcessingResult.Processed
        } + validLobbyCacheCount
        val availability = when {
            allResultCachesValid && allLobbyCachesValid -> MatchOcrCacheAvailability.READY
            validCacheCount > 0 -> MatchOcrCacheAvailability.STALE_OR_INCOMPLETE
            else -> MatchOcrCacheAvailability.NOT_AVAILABLE
        }
        return MatchOcrCacheReadResult(
            availability = availability,
            resultRoleResults = resultRoleResults,
            lobbyResult = MatchLobbyPlayersOcrResult(lobbySlots),
        )
    }

    private suspend fun readResult(
        identity: MatchResultScreenshotIdentity,
        ownerUserId: String,
    ): MatchResultOcrPreviewProcessingResult.Processed? = try {
        resultScreenshotAssetRepository.getByIdentityAndOwner(identity, ownerUserId)
            ?.toMatchResultOcrCacheFingerprint(identity)
            ?.let { resultCacheRepository.readByOwner(it, ownerUserId) }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun readLobby(
        identity: MatchLobbyScreenshotIdentity,
        position: RosterScreenshotPosition,
        ownerUserId: String,
    ) = try {
        lobbyScreenshotAssetRepository.getByIdentityAndOwner(identity, ownerUserId)
            ?.toMatchLobbyOcrCacheFingerprint(identity, position)
            ?.let { lobbyCacheRepository.readByOwner(it, ownerUserId) }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }
}

object NoOpMatchOcrCacheReader : MatchOcrCacheReader {
    override suspend fun read(tournamentId: String, matchId: String): MatchOcrCacheReadResult =
        MatchOcrCacheReadResult(MatchOcrCacheAvailability.NOT_AVAILABLE)
}
