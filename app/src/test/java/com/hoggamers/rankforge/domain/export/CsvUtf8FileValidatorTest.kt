package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CsvUtf8FileValidatorTest {
    private lateinit var validator: CsvUtf8FileValidator

    @Before
    fun setUp() {
        validator = CsvUtf8FileValidator()
    }

    @Test
    fun payloadUsesExactUtf8Bytes() {
        val csv = "name,team\r\nरैंक फोर्ज,HØG Élite 🔥"

        val payload = createPayloadSuccess(csv)

        assertArrayEquals(
            csv.toByteArray(StandardCharsets.UTF_8),
            payload.bytes(),
        )
        assertEquals(payload.bytes().size, payload.byteCount)
    }

    @Test
    fun repeatedEncodingIsDeterministic() {
        val csv = "a,b\r\n1,2"

        val first = createPayloadSuccess(csv)
        val second = createPayloadSuccess(csv)

        assertArrayEquals(first.bytes(), second.bytes())
        assertEquals(first.byteCount, second.byteCount)
        assertEquals(first.sha256, second.sha256)
    }

    @Test
    fun generatedPayloadDoesNotContainUtf8Bom() {
        val bytes = createPayloadSuccess("a,b\r\n1,2").bytes()

        assertFalse(bytes.startsWithUtf8Bom())
    }

    @Test
    fun emptyCsvContentIsRejected() {
        val result = validator.createPayload("")

        assertPayloadFailure(
            result,
            CsvUtf8FileFailure.EMPTY_CSV_CONTENT,
        )
    }

    @Test
    fun exactUtf8RoundTripIsAccepted() {
        val csv = "name,value\r\nरैंक फोर्ज,🔥"
        val payload = createPayloadSuccess(csv)

        val validation = validator.validate(
            expectedCsv = csv,
            actualBytes = payload.bytes(),
            expectedSha256 = payload.sha256,
        )

        val success = validation as CsvUtf8ValidationResult.Success
        assertEquals(payload.byteCount, success.byteCount)
        assertEquals(payload.sha256, success.sha256)
    }

    @Test
    fun emptyFileBytesAreRejected() {
        val result = validator.validate(
            expectedCsv = "a,b",
            actualBytes = byteArrayOf(),
        )

        assertValidationFailure(
            result,
            CsvUtf8FileFailure.EMPTY_FILE_BYTES,
        )
    }

    @Test
    fun utf8BomIsRejected() {
        val csv = "a,b\r\n1,2"
        val bytes = UTF8_BOM + csv.toByteArray(StandardCharsets.UTF_8)

        val result = validator.validate(
            expectedCsv = csv,
            actualBytes = bytes,
        )

        assertValidationFailure(
            result,
            CsvUtf8FileFailure.UTF8_BOM_PRESENT,
        )
    }

    @Test
    fun malformedUtf8BytesAreRejected() {
        val malformedBytes = byteArrayOf(
            0xc3.toByte(),
            0x28,
        )

        val result = validator.validate(
            expectedCsv = "expected",
            actualBytes = malformedBytes,
        )

        assertValidationFailure(
            result,
            CsvUtf8FileFailure.MALFORMED_UTF8_BYTES,
        )
    }

    @Test
    fun truncatedBytesAreRejected() {
        val csv = "a,b\r\n1,2"
        val bytes = csv.toByteArray(StandardCharsets.UTF_8)
            .copyOfRange(
                fromIndex = 0,
                toIndex = csv.toByteArray(StandardCharsets.UTF_8).size - 1,
            )

        val result = validator.validate(
            expectedCsv = csv,
            actualBytes = bytes,
        )

        assertValidationFailure(
            result,
            CsvUtf8FileFailure.DECODED_CONTENT_MISMATCH,
        )
        assertValidationFailure(
            result,
            CsvUtf8FileFailure.BYTE_CONTENT_MISMATCH,
        )
    }

    @Test
    fun appendedBytesAreRejected() {
        val csv = "a,b\r\n1,2"
        val bytes =
            csv.toByteArray(StandardCharsets.UTF_8) +
                "\r\nextra".toByteArray(StandardCharsets.UTF_8)

        val result = validator.validate(
            expectedCsv = csv,
            actualBytes = bytes,
        )

        assertValidationFailure(
            result,
            CsvUtf8FileFailure.DECODED_CONTENT_MISMATCH,
        )
        assertValidationFailure(
            result,
            CsvUtf8FileFailure.BYTE_CONTENT_MISMATCH,
        )
    }

    @Test
    fun alteredBytesAreRejected() {
        val csv = "a,b\r\n1,2"
        val altered = "a,b\r\n1,3"
            .toByteArray(StandardCharsets.UTF_8)

        val result = validator.validate(
            expectedCsv = csv,
            actualBytes = altered,
        )

        assertValidationFailure(
            result,
            CsvUtf8FileFailure.DECODED_CONTENT_MISMATCH,
        )
        assertValidationFailure(
            result,
            CsvUtf8FileFailure.BYTE_CONTENT_MISMATCH,
        )
    }

    @Test
    fun checksumUsesExactSha256LowercaseHex() {
        val payload = createPayloadSuccess("a,b\r\n1,2")

        assertEquals(
            "a64a34aacbdacd17c0c52c867be14c6b9dab76b5e7348392eb829431fbba3a33",
            payload.sha256,
        )
        assertEquals(64, payload.sha256.length)
        assertTrue(
            payload.sha256.all { character ->
                character in '0'..'9' || character in 'a'..'f'
            },
        )
    }

    @Test
    fun checksumChangesWhenContentChanges() {
        val first = createPayloadSuccess("a,b\r\n1,2")
        val second = createPayloadSuccess("a,b\r\n1,3")

        assertNotEquals(first.sha256, second.sha256)
    }

    @Test
    fun checksumMismatchIsRejected() {
        val csv = "a,b\r\n1,2"
        val payload = createPayloadSuccess(csv)

        val result = validator.validate(
            expectedCsv = csv,
            actualBytes = payload.bytes(),
            expectedSha256 =
                "0000000000000000000000000000000000000000000000000000000000000000",
        )

        assertValidationFailure(
            result,
            CsvUtf8FileFailure.CHECKSUM_MISMATCH,
        )
    }

    @Test
    fun payloadBytesCannotBeMutatedExternally() {
        val csv = "a,b\r\n1,2"
        val payload = createPayloadSuccess(csv)
        val exposedBytes = payload.bytes()

        exposedBytes[0] = 'x'.code.toByte()

        assertArrayEquals(
            csv.toByteArray(StandardCharsets.UTF_8),
            payload.bytes(),
        )
        assertEquals(
            sha256(csv.toByteArray(StandardCharsets.UTF_8)),
            payload.sha256,
        )
    }

    @Test
    fun specialCharactersAndExactWhitespaceArePreserved() {
        val csv =
            "tournament,team,player\r\n" +
                "\"Copa São Paulo\",\" Team \"\"One\"\" \",\"रैंक फोर्ज 🔥\"\r\n" +
                "\"Alpha, Bravo\",\"Line 1\nLine 2\",HØG"

        val payload = createPayloadSuccess(csv)
        val validation = validator.validate(
            expectedCsv = csv,
            actualBytes = payload.bytes(),
            expectedSha256 = payload.sha256,
        )

        assertTrue(validation is CsvUtf8ValidationResult.Success)
        assertEquals(
            csv,
            payload.bytes().toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun matchExporterOutputRoundTripsExactly() {
        val csv = matchCsv(
            tournamentName = "रैंक फोर्ज कप 🔥",
            teamOneName = "HØG, Élite",
            playerOneName = "Player \"One\"",
        )
        val payload = createPayloadSuccess(csv)

        val result = validator.validate(
            expectedCsv = csv,
            actualBytes = payload.bytes(),
            expectedSha256 = payload.sha256,
        )

        assertTrue(result is CsvUtf8ValidationResult.Success)
        assertEquals(
            csv,
            payload.bytes().toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun tournamentExporterOutputRoundTripsExactly() {
        val csv = tournamentCsv(
            tournamentName = "Copa São Paulo 🔥",
            teamOneName = " टीम एक ",
            playerOneName = "Æon, \"Prime\"",
        )
        val payload = createPayloadSuccess(csv)

        val result = validator.validate(
            expectedCsv = csv,
            actualBytes = payload.bytes(),
            expectedSha256 = payload.sha256,
        )

        assertTrue(result is CsvUtf8ValidationResult.Success)
        assertEquals(
            csv,
            payload.bytes().toString(StandardCharsets.UTF_8),
        )
    }

    @Test
    fun temporaryFileRoundTripPreservesExactBytes() {
        val csv = tournamentCsv(
            tournamentName = "रैंक फोर्ज",
            teamOneName = "HØG Élite",
            playerOneName = "Player 🔥",
        )
        val payload = createPayloadSuccess(csv)
        val temporaryFile = Files.createTempFile(
            "rank-forge-export-",
            ".csv",
        )

        try {
            Files.write(temporaryFile, payload.bytes())
            val readBackBytes = Files.readAllBytes(temporaryFile)

            assertArrayEquals(payload.bytes(), readBackBytes)
            assertTrue(
                validator.validate(
                    expectedCsv = csv,
                    actualBytes = readBackBytes,
                    expectedSha256 = payload.sha256,
                ) is CsvUtf8ValidationResult.Success,
            )
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }

    @Test
    fun integrityMetadataIsNotAddedToCsvContent() {
        val csv = matchCsv()
        val payload = createPayloadSuccess(csv)
        val decoded = payload.bytes()
            .toString(StandardCharsets.UTF_8)

        assertEquals(csv, decoded)
        assertFalse(decoded.contains("sha256"))
        assertFalse(decoded.contains("byte_count"))
        assertFalse(decoded.contains(payload.sha256))
    }

    private fun createPayloadSuccess(
        csv: String,
    ): CsvUtf8FilePayload {
        val result = validator.createPayload(csv)
        return (result as CsvUtf8PayloadResult.Success).payload
    }

    private fun assertPayloadFailure(
        result: CsvUtf8PayloadResult,
        failure: CsvUtf8FileFailure,
    ) {
        val failed = result as CsvUtf8PayloadResult.Failure
        assertTrue(failure in failed.failures)
    }

    private fun assertValidationFailure(
        result: CsvUtf8ValidationResult,
        failure: CsvUtf8FileFailure,
    ) {
        val failed = result as CsvUtf8ValidationResult.Failure
        assertTrue(failure in failed.failures)
    }

    private fun matchCsv(
        tournamentName: String = "Synthetic Cup",
        teamOneName: String = "Team 1",
        playerOneName: String = "Player 1.1",
    ): String {
        val result = MatchCsvExporter().export(
            MatchCsvExportInput(
                tournament = validTournament(tournamentName),
                match = validMatch(),
                teamSlots = validTeamSlots(teamOneName),
                rosterPlayers = validRosterPlayers(playerOneName),
            ),
        )

        return (result as MatchCsvExportResult.Success).csv
    }

    private fun tournamentCsv(
        tournamentName: String = "Synthetic Cup",
        teamOneName: String = "Team 1",
        playerOneName: String = "Player 1.1",
    ): String {
        val result = TournamentCsvExporter().export(
            TournamentCsvExportInput(
                tournament = validTournament(tournamentName),
                matches = listOf(validMatch()),
                teamSlots = validTeamSlots(teamOneName),
                rosterPlayers = validRosterPlayers(playerOneName),
            ),
        )

        return (result as TournamentCsvExportResult.Success).csv
    }

    private fun validTournament(
        name: String,
    ): Tournament =
        Tournament(
            id = TOURNAMENT_ID,
            name = name,
            date = LocalDate.of(2026, 7, 31),
            organizerName = "Organizer",
            organizerContactNumber = "1234567890",
            status = TournamentStatus.CONFIRMED,
        )

    private fun validMatch(): Match =
        Match(
            id = MATCH_ID,
            tournamentId = TOURNAMENT_ID,
            matchNumber = 1,
            date = LocalDate.of(2026, 7, 31),
            mapName = "Bermuda",
            status = MatchStatus.FINALIZED,
            placements = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                MatchPlacement(
                    teamSlotNumber = slotNumber,
                    position = slotNumber,
                )
            },
            kills = TeamSlot.SLOT_NUMBERS.map { slotNumber ->
                MatchKill(
                    teamSlotNumber = slotNumber,
                    kills = slotNumber - 1,
                )
            },
        )

    private fun validTeamSlots(
        teamOneName: String,
    ): List<TeamSlot> =
        TeamSlot.SLOT_NUMBERS.map { slotNumber ->
            TeamSlot(
                tournamentId = TOURNAMENT_ID,
                slotNumber = slotNumber,
                teamName = if (slotNumber == 1) {
                    teamOneName
                } else {
                    "Team $slotNumber"
                },
            )
        }

    private fun validRosterPlayers(
        playerOneName: String,
    ): List<RosterPlayer> =
        TeamSlot.SLOT_NUMBERS.flatMap { slotNumber ->
            (1..4).map { playerNumber ->
                RosterPlayer(
                    tournamentId = TOURNAMENT_ID,
                    slotNumber = slotNumber,
                    displayName =
                        if (slotNumber == 1 && playerNumber == 1) {
                            playerOneName
                        } else {
                            "Player $slotNumber.$playerNumber"
                        },
                )
            }
        }

    private fun sha256(
        bytes: ByteArray,
    ): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

    private fun ByteArray.startsWithUtf8Bom(): Boolean =
        size >= UTF8_BOM.size &&
            copyOfRange(0, UTF8_BOM.size).contentEquals(UTF8_BOM)

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
        const val MATCH_ID = "match-id"

        val UTF8_BOM = byteArrayOf(
            0xef.toByte(),
            0xbb.toByte(),
            0xbf.toByte(),
        )
    }
}
