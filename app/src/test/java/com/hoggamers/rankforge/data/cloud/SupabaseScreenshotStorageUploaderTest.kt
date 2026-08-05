package com.hoggamers.rankforge.data.cloud

import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseScreenshotStorageUploaderTest {
    @Test
    fun objectPathUsesDeterministicCloudMatchIdentityForLocalMatch() {
        val tournamentId = "87204119-1b59-447a-8edc-bfecdaaeccfa"
        val localMatchId = "2cfb5241-7c23-49eb-a6f5-224a779fb220"
        val expectedCloudMatchId = UUID.nameUUIDFromBytes(
            "rank-forge:match:$tournamentId:$localMatchId"
                .toByteArray(StandardCharsets.UTF_8),
        ).toString()

        assertEquals(
            "users/user-id/tournaments/$tournamentId/matches/$expectedCloudMatchId/original.jpg",
            SupabaseScreenshotStorageUploader.objectPath(
                userId = "user-id",
                tournamentId = tournamentId,
                matchId = localMatchId,
                extension = "jpg",
            ),
        )
    }

    @Test
    fun objectPathRejectsInvalidTournamentIdentity() {
        assertNull(
            SupabaseScreenshotStorageUploader.objectPath(
                userId = "user-id",
                tournamentId = "not-a-uuid",
                matchId = "local-match",
                extension = "jpg",
            ),
        )
    }
}
