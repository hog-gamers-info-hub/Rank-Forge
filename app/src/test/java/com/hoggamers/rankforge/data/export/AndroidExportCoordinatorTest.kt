package com.hoggamers.rankforge.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidExportCoordinatorTest {
    private val coordinator = AndroidExportCoordinator()

    @Test
    fun matchCsvReadyResultPreservesExactIdentityAndUtf8Metadata() {
        val result = coordinator.prepareMatchCsv(
            tournamentId = "tournament-id",
            matchId = "match-id",
            csv = "header\r\nvalue",
        )

        val ready = result as AndroidExportResult.CsvReady
        assertEquals(AndroidExportType.MATCH_CSV, ready.request.type)
        assertEquals("tournament-id", ready.request.tournamentId)
        assertEquals("match-id", ready.request.matchId)
        assertEquals("text/csv", ready.mimeType)
        assertEquals("header\r\nvalue", ready.content)
        assertTrue(ready.byteCount > 0)
        assertTrue(ready.sha256.isNotBlank())
    }

    @Test
    fun standingsCsvReadyResultPreservesExactTournamentIdentity() {
        val result = coordinator.prepareStandingsCsv(
            tournamentId = "tournament-id",
            csv = "header\r\nvalue",
        )

        val ready = result as AndroidExportResult.CsvReady
        assertEquals(AndroidExportType.STANDINGS_CSV, ready.request.type)
        assertEquals("tournament-id", ready.request.tournamentId)
        assertEquals(null, ready.request.matchId)
        assertEquals("rank-forge-standings-tournament-id.csv", ready.filename)
    }

    @Test
    fun emptyCsvIsBlockedWithoutCreatingAFilePayload() {
        val result = coordinator.prepareStandingsCsv(
            tournamentId = "tournament-id",
            csv = "",
        )

        assertEquals(AndroidExportBlockedReason.INVALID_CSV_PAYLOAD, (result as AndroidExportResult.Blocked).reason)
        assertEquals("tournament-id", result.request.tournamentId)
    }

    @Test
    fun googleSheetsMatchExportIsUnavailableWithoutAnAndroidClient() {
        val result = coordinator.googleSheetsMatchUnavailable(
            tournamentId = "tournament-id",
            matchId = "match-id",
        )

        assertEquals(AndroidExportType.MATCH_GOOGLE_SHEETS, result.request.type)
        assertEquals("tournament-id", result.request.tournamentId)
        assertEquals("match-id", result.request.matchId)
        assertEquals(
            AndroidExportUnavailableReason.GOOGLE_SHEETS_CLIENT_NOT_CONFIGURED,
            result.reason,
        )
    }

    @Test
    fun blockedMatchExportPreservesIdentityWithoutFinalizingData() {
        val result = coordinator.blockMatchCsv(
            tournamentId = "tournament-id",
            matchId = "match-id",
            reason = AndroidExportBlockedReason.MATCH_NOT_FINALIZED,
        )

        assertEquals(AndroidExportType.MATCH_CSV, result.request.type)
        assertEquals("tournament-id", result.request.tournamentId)
        assertEquals("match-id", result.request.matchId)
        assertEquals(AndroidExportBlockedReason.MATCH_NOT_FINALIZED, result.reason)
    }
}
