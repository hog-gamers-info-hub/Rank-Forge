package com.hoggamers.rankforge.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseScreenshotStorageUploaderTest {
    @Test
    fun objectPathIsScopedToUserTournamentAndMatch() {
        assertEquals(
            "users/user-id/tournaments/tournament-id/matches/match-id/original.jpg",
            SupabaseScreenshotStorageUploader.objectPath(
                userId = "user-id",
                tournamentId = "tournament-id",
                matchId = "match-id",
                extension = "jpg",
            ),
        )
    }
}
