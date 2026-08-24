package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyOcrCacheCodec
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrSlot
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

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

    suspend fun readByOwner(
        fingerprint: MatchLobbyOcrCacheFingerprint,
        ownerUserId: String,
    ): List<MatchLobbyPlayersOcrSlot>? = null

    suspend fun saveByOwner(
        fingerprint: MatchLobbyOcrCacheFingerprint,
        slots: List<MatchLobbyPlayersOcrSlot>,
        ownerUserId: String,
    ): Boolean = false

    suspend fun deleteByMatchAndIndexByOwner(
        matchId: String,
        lobbyScreenshotIndex: Int,
        ownerUserId: String,
    ): Boolean = false
}

@Singleton
class RoomMatchLobbyOcrCacheRepository(
    private val dao: MatchLobbyOcrCacheDao,
    private val codec: MatchLobbyOcrCacheCodec,
    private val clock: Clock,
    private val database: RankForgeDatabase?,
) : MatchLobbyOcrCacheRepository {
    constructor(
        dao: MatchLobbyOcrCacheDao,
        codec: MatchLobbyOcrCacheCodec,
        clock: Clock,
    ) : this(dao, codec, clock, null)
    override suspend fun read(
        fingerprint: MatchLobbyOcrCacheFingerprint,
    ): List<MatchLobbyPlayersOcrSlot>? {
        val cached = dao.readByMatchAndIndex(
            matchId = fingerprint.matchId,
            lobbyScreenshotIndex = fingerprint.screenshotPosition.index,
        ) ?: return null
        if (cached.toFingerprint() != fingerprint) return null
        return codec.decode(cached.processedPayloadJson)
    }

    override suspend fun save(
        fingerprint: MatchLobbyOcrCacheFingerprint,
        slots: List<MatchLobbyPlayersOcrSlot>,
    ) {
        val payload = codec.encode(slots)
        if (codec.decode(payload) == null) return
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

    override suspend fun readByOwner(
        fingerprint: MatchLobbyOcrCacheFingerprint,
        ownerUserId: String,
    ): List<MatchLobbyPlayersOcrSlot>? {
        if (ownerUserId.isBlank()) return null
        val cached = dao.readByMatchAndIndexAndOwner(fingerprint.matchId, fingerprint.screenshotPosition.index, ownerUserId)
            ?: return null
        if (cached.toFingerprint() != fingerprint) return null
        return codec.decode(cached.processedPayloadJson)
    }

    override suspend fun saveByOwner(
        fingerprint: MatchLobbyOcrCacheFingerprint,
        slots: List<MatchLobbyPlayersOcrSlot>,
        ownerUserId: String,
    ): Boolean {
        if (ownerUserId.isBlank()) return false
        val payload = codec.encode(slots)
        if (codec.decode(payload) == null) return false
        return (database ?: return false).withTransaction {
            if (!database.matchDao().existsByIdAndTournamentAndOwner(fingerprint.matchId, fingerprint.tournamentId, ownerUserId)) {
                return@withTransaction false
            }
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
            true
        }
    }

    override suspend fun deleteByMatchAndIndexByOwner(
        matchId: String,
        lobbyScreenshotIndex: Int,
        ownerUserId: String,
    ): Boolean = if (ownerUserId.isBlank()) false else (database ?: return false).withTransaction {
        val cache = database.matchDao().observeByIdAndOwner(matchId, ownerUserId).first()
            ?: return@withTransaction false
        dao.deleteByMatchAndIndex(matchId, lobbyScreenshotIndex)
        true
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
