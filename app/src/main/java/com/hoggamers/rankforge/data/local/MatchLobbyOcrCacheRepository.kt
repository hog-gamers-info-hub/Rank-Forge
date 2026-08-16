package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyOcrCacheCodec
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrSlot
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class MatchLobbyOcrCacheFingerprint(
    val tournamentId: String,
    val matchId: String,
    val screenshotPosition: RosterScreenshotPosition,
    val screenshotSha256: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val cropProfileId: String,
    val cropLeft: Double,
    val cropTop: Double,
    val cropRight: Double,
    val cropBottom: Double,
    val ocrPipelineVersion: Int,
)

interface MatchLobbyOcrCacheRepository {
    suspend fun read(
        fingerprint: MatchLobbyOcrCacheFingerprint,
    ): List<MatchLobbyPlayersOcrSlot>?

    suspend fun save(
        fingerprint: MatchLobbyOcrCacheFingerprint,
        slots: List<MatchLobbyPlayersOcrSlot>,
    )

    suspend fun deleteByMatchAndIndex(matchId: String, lobbyScreenshotIndex: Int)
}

@Singleton
class RoomMatchLobbyOcrCacheRepository @Inject constructor(
    private val dao: MatchLobbyOcrCacheDao,
    private val codec: MatchLobbyOcrCacheCodec,
    private val clock: Clock,
) : MatchLobbyOcrCacheRepository {
    override suspend fun read(
        fingerprint: MatchLobbyOcrCacheFingerprint,
    ): List<MatchLobbyPlayersOcrSlot>? {
        val cached = dao.readByMatchAndIndex(
            matchId = fingerprint.matchId,
            lobbyScreenshotIndex = fingerprint.screenshotPosition.index,
        ) ?: return null
        if (cached.toFingerprint() != fingerprint) return null
        return codec.decode(cached.processedPayloadJson, fingerprint.screenshotPosition)
    }

    override suspend fun save(
        fingerprint: MatchLobbyOcrCacheFingerprint,
        slots: List<MatchLobbyPlayersOcrSlot>,
    ) {
        val payload = codec.encode(slots)
        if (codec.decode(payload, fingerprint.screenshotPosition) == null) return
        dao.upsert(
            MatchLobbyOcrCacheEntity(
                tournamentId = fingerprint.tournamentId,
                matchId = fingerprint.matchId,
                lobbyScreenshotIndex = fingerprint.screenshotPosition.index,
                screenshotSha256 = fingerprint.screenshotSha256,
                originalWidth = fingerprint.originalWidth,
                originalHeight = fingerprint.originalHeight,
                cropProfileId = fingerprint.cropProfileId,
                cropLeft = fingerprint.cropLeft,
                cropTop = fingerprint.cropTop,
                cropRight = fingerprint.cropRight,
                cropBottom = fingerprint.cropBottom,
                ocrPipelineVersion = fingerprint.ocrPipelineVersion,
                processedPayloadJson = payload,
                cachedAt = clock.millis(),
            ),
        )
    }

    override suspend fun deleteByMatchAndIndex(matchId: String, lobbyScreenshotIndex: Int) {
        dao.deleteByMatchAndIndex(matchId, lobbyScreenshotIndex)
    }
}

private fun MatchLobbyOcrCacheEntity.toFingerprint(): MatchLobbyOcrCacheFingerprint? = runCatching {
    MatchLobbyOcrCacheFingerprint(
        tournamentId = tournamentId,
        matchId = matchId,
        screenshotPosition = RosterScreenshotPosition.fromIndex(lobbyScreenshotIndex) ?: return null,
        screenshotSha256 = screenshotSha256,
        originalWidth = originalWidth,
        originalHeight = originalHeight,
        cropProfileId = cropProfileId,
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropRight = cropRight,
        cropBottom = cropBottom,
        ocrPipelineVersion = ocrPipelineVersion,
    )
}.getOrNull()
