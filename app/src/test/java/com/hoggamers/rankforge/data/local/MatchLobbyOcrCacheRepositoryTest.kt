package com.hoggamers.rankforge.data.local

import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyOcrCacheCodec
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrPlayer
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyPlayersOcrSlot
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatchLobbyOcrCacheRepositoryTest {
    private val dao = FakeDao()
    private val repository = RoomMatchLobbyOcrCacheRepository(
        dao = dao,
        codec = MatchLobbyOcrCacheCodec(),
        clock = Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC),
    )

    @Test
    fun savesAndReadsIndependentScreenshotEntries() = runTest {
        val one = fingerprint(RosterScreenshotPosition.ONE)
        val two = fingerprint(RosterScreenshotPosition.TWO)

        repository.save(one, slots(RosterScreenshotPosition.ONE))
        repository.save(two, slots(RosterScreenshotPosition.TWO))

        assertEquals(slots(RosterScreenshotPosition.ONE), repository.read(one))
        assertEquals(slots(RosterScreenshotPosition.TWO), repository.read(two))
        assertEquals(2, dao.entries.size)
    }

    @Test
    fun physicalFingerprintCanReadSwappedSemanticGroup() = runTest {
        val fingerprint = fingerprint(RosterScreenshotPosition.ONE)
        val expected = slots(5..8)

        repository.save(fingerprint, expected)

        assertEquals(expected, repository.read(fingerprint))
    }

    @Test
    fun fingerprintMismatchIsCacheMiss() = runTest {
        val fingerprint = fingerprint(RosterScreenshotPosition.ONE)
        repository.save(fingerprint, slots(RosterScreenshotPosition.ONE))

        assertNull(repository.read(fingerprint.copy(screenshotSha256 = "changed")))
        assertNull(repository.read(fingerprint.copy(cropRight = 0.5)))
        assertNull(repository.read(fingerprint.copy(originalWidth = 200)))
        assertNull(repository.read(fingerprint.copy(ocrPipelineVersion = 1)))
    }

    @Test
    fun deleteRemovesOnlyRequestedScreenshotEntry() = runTest {
        repository.save(fingerprint(RosterScreenshotPosition.ONE), slots(RosterScreenshotPosition.ONE))
        repository.save(fingerprint(RosterScreenshotPosition.TWO), slots(RosterScreenshotPosition.TWO))

        repository.deleteByMatchAndIndex("match-1", 1)

        assertNull(repository.read(fingerprint(RosterScreenshotPosition.ONE)))
        assertEquals(slots(RosterScreenshotPosition.TWO), repository.read(fingerprint(RosterScreenshotPosition.TWO)))
    }

    private fun fingerprint(position: RosterScreenshotPosition) = MatchLobbyOcrCacheFingerprint(
        tournamentId = "tournament-1",
        matchId = "match-1",
        screenshotPosition = position,
        screenshotSha256 = "sha-${position.index}",
        originalWidth = 100,
        originalHeight = 100,
        cropProfileId = "roster",
        cropLeft = 0.0,
        cropTop = 0.0,
        cropRight = 1.0,
        cropBottom = 1.0,
        ocrPipelineVersion = 3,
    )

    private fun slots(position: RosterScreenshotPosition) = slots(position.tournamentSlotRange)

    private fun slots(slotNumberRange: IntRange) = slotNumberRange.map { slotNumber ->
        MatchLobbyPlayersOcrSlot(
            slotNumber = slotNumber,
            players = (1..4).map { playerNumber -> MatchLobbyPlayersOcrPlayer(playerNumber, "p$playerNumber") },
        )
    }

    private class FakeDao : MatchLobbyOcrCacheDao {
        val entries = mutableMapOf<Pair<String, Int>, MatchLobbyOcrCacheEntity>()

        override suspend fun readByMatchAndIndex(
            matchId: String,
            lobbyScreenshotIndex: Int,
        ): MatchLobbyOcrCacheEntity? = entries[matchId to lobbyScreenshotIndex]

        override suspend fun upsert(cache: MatchLobbyOcrCacheEntity) {
            entries[cache.matchId to cache.lobbyScreenshotIndex] = cache
        }

        override suspend fun deleteByMatchAndIndex(matchId: String, lobbyScreenshotIndex: Int) {
            entries.remove(matchId to lobbyScreenshotIndex)
        }
    }
}
