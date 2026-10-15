package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.TournamentFormat
import com.hoggamers.rankforge.domain.tournament.TournamentGroup
import com.hoggamers.rankforge.domain.tournament.toCloudValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationUuidCloudRestorationMapperTest {
    @Test
    fun preservesExactUuidsNamesSparseLobbySlotsAndPairingReuse() {
        val payloads = payloads(
            identities = listOf(identity(U1, "Phoenix Updated"), identity(U2, "Bravo")),
            assignments = listOf(
                assignment(PAIRING_AB, 3, U1),
                assignment(PAIRING_AB, 12, U2),
                assignment(PAIRING_AC, 7, U1),
            ),
        )

        val result = GroupRotationUuidCloudRestorationMapper.map(payloads, OWNER)

        assertTrue(result is GroupRotationUuidCloudRestorationMappingResult.Success)
        val snapshot = (result as GroupRotationUuidCloudRestorationMappingResult.Success).snapshot
        assertEquals(listOf(3, 12), snapshot.assignments
            .filter { it.pairingKey == PAIRING_AB }
            .map { it.lobbySlotNumber })
        assertEquals(U1, snapshot.assignments.first { it.lobbySlotNumber == 3 }.teamUUID)
        assertEquals("Phoenix Updated", snapshot.identities.first { it.teamUUID == U1 }.displayName)
        assertEquals(U1, snapshot.assignments.first { it.pairingKey == PAIRING_AC }.teamUUID)
        assertEquals(7, snapshot.cloudRevision.value)
    }

    @Test
    fun restoresPairingSpecificClearedStateWithoutChangingRegisteredUuid() {
        val result = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                identities = listOf(identity(U1, "Phoenix")),
                assignments = listOf(assignment(PAIRING_AB, 3, U1, nameCleared = true)),
            ),
            OWNER,
        )

        assertTrue(result is GroupRotationUuidCloudRestorationMappingResult.Success)
        val snapshot = (result as GroupRotationUuidCloudRestorationMappingResult.Success).snapshot
        val assignment = snapshot.assignments.single()
        assertEquals(U1, assignment.teamUUID)
        assertTrue(assignment.nameCleared)
        assertEquals("Phoenix", snapshot.identities.single().displayName)
    }

    @Test
    fun emptyPairingAndZeroUuidMatchesAreValidWithoutPlaceholders() {
        val result = GroupRotationUuidCloudRestorationMapper.map(
            payloads(identities = listOf(identity(U1, "Phoenix"))),
            OWNER,
        )

        assertTrue(result is GroupRotationUuidCloudRestorationMappingResult.Success)
        val snapshot = (result as GroupRotationUuidCloudRestorationMappingResult.Success).snapshot
        assertTrue(snapshot.assignments.isEmpty())
        assertTrue(snapshot.uuidMatches.isEmpty())
        assertTrue(snapshot.matches.isEmpty())
    }

    @Test
    fun invalidAssignmentReferencesAndDuplicateAssignmentsFailClosed() {
        val missingIdentity = GroupRotationUuidCloudRestorationMapper.map(
            payloads(assignments = listOf(assignment(PAIRING_AB, 1, UNKNOWN))),
            OWNER,
        )
        assertTrue(missingIdentity is GroupRotationUuidCloudRestorationMappingResult.Invalid)

        val duplicateLobby = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                identities = listOf(identity(U1, "One"), identity(U2, "Two")),
                assignments = listOf(
                    assignment(PAIRING_AB, 1, U1),
                    assignment(PAIRING_AB, 1, U2),
                ),
            ),
            OWNER,
        )
        assertTrue(duplicateLobby is GroupRotationUuidCloudRestorationMappingResult.Invalid)
    }

    @Test
    fun foreignOwnerAndInvalidTournamentRowsAreNotAcceptedAsEmptyData() {
        val foreignParent = payloads().copy(
            parent = payloads().parent.copy(
                tournament = payloads().parent.tournament.copy(ownerId = OTHER_OWNER),
            ),
        )
        assertTrue(
            GroupRotationUuidCloudRestorationMapper.map(foreignParent, OWNER) is
                GroupRotationUuidCloudRestorationMappingResult.Invalid,
        )

        val foreignIdentity = GroupRotationUuidCloudRestorationMapper.map(
            payloads(identities = listOf(identity(U1, "Phoenix").copy(tournamentId = OTHER_TOURNAMENT))),
            OWNER,
        )
        assertTrue(foreignIdentity is GroupRotationUuidCloudRestorationMappingResult.Invalid)
    }

    @Test
    fun preservesOriginalPairingAndImmutableUuidMatchSnapshot() {
        val match = baseMatch(PAIRING_AB)
        val result = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                identities = listOf(identity(U1, "Phoenix")),
                assignments = listOf(assignment(PAIRING_AB, 9, U1)),
                matches = listOf(match),
                uuidSnapshots = listOf(uuidHeader(PAIRING_AB)),
                uuidAssignments = listOf(uuidAssignment(9, U1)),
                uuidResults = listOf(
                    uuidResult(U1, "PARTICIPATED", placement = null, kills = 4),
                ),
            ),
            OWNER,
        )

        assertTrue(result is GroupRotationUuidCloudRestorationMappingResult.Success)
        val snapshot = (result as GroupRotationUuidCloudRestorationMappingResult.Success).snapshot
        val restored = snapshot.uuidMatches.single()
        assertEquals(PAIRING_AB, restored.identity.pairing)
        assertEquals(listOf(9), restored.identity.teams.map { it.lobbySlotNumber })
        assertEquals(U1, restored.identity.teams.single().teamUUID)
        assertEquals(null, restored.participantResults.single().placement)
        assertEquals(4, restored.participantResults.single().kills)
    }

    @Test
    fun rejectsHeaderlessMatchWithoutIndependentLegacyEvidence() {
        val result = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                identities = listOf(identity(U1, "Phoenix")),
                assignments = listOf(assignment(PAIRING_AB, 3, U1)),
                matches = listOf(baseMatch(PAIRING_AB)),
                pairingLobbySlots = legacyPairingMappings(PAIRING_AB),
            ),
            OWNER,
        )

        assertTrue(result is GroupRotationUuidCloudRestorationMappingResult.Invalid)
    }

    @Test
    fun legacyResultWithoutNumericMappingDoesNotEstablishLegacyIdentity() {
        val result = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                matches = listOf(baseMatch(PAIRING_AB)),
                legacyResults = listOf(legacyResult(MATCH)),
            ),
            OWNER,
        )

        assertTrue(result is GroupRotationUuidCloudRestorationMappingResult.Invalid)
    }

    @Test
    fun genuineLegacyDraftWithoutResultsRemainsCompatibleWithNumericMapping() {
        val result = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                matches = listOf(baseMatch(PAIRING_AB)),
                pairingLobbySlots = legacyPairingMappings(PAIRING_AB),
            ),
            OWNER,
        )

        assertTrue(result is GroupRotationUuidCloudRestorationMappingResult.NotUuidBacked)
    }

    @Test
    fun preservesMixedUuidAndLegacyMatchesWhenBothModesHaveEvidence() {
        val legacyMatchId = "99999999-9999-4999-8999-999999999999"
        val result = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                identities = listOf(identity(U1, "Phoenix")),
                assignments = listOf(assignment(PAIRING_AB, 3, U1)),
                matches = listOf(
                    baseMatch(PAIRING_AB),
                    baseMatch(PAIRING_AC, id = legacyMatchId, matchNumber = 2),
                ),
                uuidSnapshots = listOf(uuidHeader(PAIRING_AB)),
                uuidAssignments = listOf(uuidAssignment(3, U1)),
                uuidResults = listOf(uuidResult(U1, "PARTICIPATED", placement = null, kills = 4)),
                legacyResults = listOf(legacyResult(legacyMatchId)),
                pairingLobbySlots = legacyPairingMappings(PAIRING_AC),
            ),
            OWNER,
        )

        assertTrue(result is GroupRotationUuidCloudRestorationMappingResult.Success)
        val snapshot = (result as GroupRotationUuidCloudRestorationMappingResult.Success).snapshot
        assertEquals(listOf(MATCH, legacyMatchId), snapshot.matches.map { it.id })
        assertEquals(listOf(MATCH), snapshot.uuidMatches.map { it.match.id })
    }

    @Test
    fun finalizedResultsAndNoShowSemanticsArePreserved() {
        val result = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                identities = listOf(identity(U1, "Phoenix"), identity(U2, "Bravo")),
                matches = listOf(baseMatch(PAIRING_AC, status = "finalized")),
                uuidSnapshots = listOf(uuidHeader(PAIRING_AC, status = "finalized")),
                uuidAssignments = listOf(uuidAssignment(1, U1), uuidAssignment(5, U2)),
                uuidResults = listOf(
                    uuidResult(U1, "PARTICIPATED", placement = 1, kills = 4),
                    uuidResult(U2, "NO_SHOW", placement = null, kills = 0),
                ),
            ),
            OWNER,
        )

        assertTrue(result is GroupRotationUuidCloudRestorationMappingResult.Success)
        val restored = (result as GroupRotationUuidCloudRestorationMappingResult.Success)
            .snapshot.uuidMatches.single()
        assertEquals("FINALIZED", restored.match.status.name)
        assertEquals(listOf(1, null), restored.participantResults.map { it.placement })
        assertEquals(listOf(4, 0), restored.participantResults.map { it.kills })
        assertFalse(restored.participantResults.first().participationStatus.name == "NO_SHOW")
        assertEquals("NO_SHOW", restored.participantResults[1].participationStatus.name)
    }

    @Test
    fun invalidFinalizedResultAndMismatchedPairingAreRejected() {
        val invalidFinalization = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                identities = listOf(identity(U1, "Phoenix")),
                matches = listOf(baseMatch(PAIRING_AB, status = "finalized")),
                uuidSnapshots = listOf(uuidHeader(PAIRING_AB, status = "finalized")),
                uuidAssignments = listOf(uuidAssignment(1, U1)),
                uuidResults = listOf(uuidResult(U1, "PARTICIPATED", placement = null, kills = 4)),
            ),
            OWNER,
        )
        assertTrue(invalidFinalization is GroupRotationUuidCloudRestorationMappingResult.Invalid)

        val mismatched = GroupRotationUuidCloudRestorationMapper.map(
            payloads(
                identities = listOf(identity(U1, "Phoenix")),
                matches = listOf(baseMatch(PAIRING_AB)),
                uuidSnapshots = listOf(uuidHeader(PAIRING_AC)),
                uuidAssignments = listOf(uuidAssignment(1, U1)),
                uuidResults = listOf(uuidResult(U1, "PARTICIPATED", placement = null, kills = 4)),
            ),
            OWNER,
        )
        assertTrue(mismatched is GroupRotationUuidCloudRestorationMappingResult.Invalid)
    }

    @Test
    fun standardSnapshotCannotEnterUuidGroupRotationRestorationPath() {
        val standard = payloads().copy(
            parent = payloads().parent.copy(
                tournament = payloads().parent.tournament.copy(
                    format = TournamentFormat.STANDARD.toCloudValue(),
                    groupCount = null,
                    selectedGroupPairings = emptyList(),
                ),
                teamSlots = (1..12).map { slot ->
                    TeamSlotUploadPayload(
                        id = "00000000-0000-0000-0000-${slot.toString().padStart(12, '0')}",
                        tournamentId = T,
                        slotNumber = slot,
                        teamName = "",
                        status = "active",
                    )
                },
            ),
            identities = emptyList(),
            assignments = emptyList(),
        )
        assertTrue(
            GroupRotationUuidCloudRestorationMapper.map(standard, OWNER) is
                GroupRotationUuidCloudRestorationMappingResult.Invalid,
        )
    }

    private fun payloads(
        identities: List<GroupRotationUuidTeamIdentityPayload> = emptyList(),
        assignments: List<GroupRotationUuidPairingAssignmentPayload> = emptyList(),
        matches: List<MatchCloudRestorePayload> = emptyList(),
        legacyResults: List<MatchResultCloudRestorePayload> = emptyList(),
        pairingLobbySlots: List<GroupPairingLobbySlotUploadPayload> = emptyList(),
        uuidSnapshots: List<GroupRotationUuidMatchSnapshotRestorePayload> = emptyList(),
        uuidAssignments: List<GroupRotationUuidMatchAssignmentRestorePayload> = emptyList(),
        uuidResults: List<GroupRotationUuidMatchResultRestorePayload> = emptyList(),
    ): GroupRotationUuidCloudRestorationPayloads {
        val referencedTeamSlotNumbers = pairingLobbySlots
            .map { it.teamSlotNumber }
            .toSet()
        val parent = TournamentCloudRestorationPayloads(
            tournament = TournamentUploadPayload(
                id = T,
                ownerId = OWNER,
                name = "Group Rotation",
                stageName = "Stage",
                organizerContact = "",
                status = "draft",
                revision = 7,
                format = TournamentFormat.GROUP_ROTATION.toCloudValue(),
                groupCount = 3,
                selectedGroupPairings = listOf(
                    pairingPayload(PAIRING_AB),
                    pairingPayload(PAIRING_AC),
                ),
            ),
            teamSlots = (1..18).map { slot ->
                TeamSlotUploadPayload(
                    id = "00000000-0000-0000-0000-${slot.toString().padStart(12, '0')}",
                    tournamentId = T,
                    slotNumber = slot,
                    teamName = if (slot in referencedTeamSlotNumbers) "Legacy Team $slot" else "",
                    status = "active",
                    group = null,
                )
            },
            players = emptyList(),
            pairingLobbySlots = pairingLobbySlots,
        )
        return GroupRotationUuidCloudRestorationPayloads(
            parent = parent,
            matches = matches,
            legacyResults = legacyResults,
            identities = identities,
            assignments = assignments,
            uuidMatchSnapshots = uuidSnapshots,
            uuidMatchAssignments = uuidAssignments,
            uuidMatchResults = uuidResults,
        )
    }

    private fun identity(uuid: String, name: String) = GroupRotationUuidTeamIdentityPayload(
        tournamentId = T,
        teamUUID = uuid,
        displayName = name,
        normalizedName = name.lowercase(),
    )

    private fun assignment(
        pairing: GroupPairing,
        lobby: Int,
        uuid: String,
        nameCleared: Boolean = false,
    ) = GroupRotationUuidPairingAssignmentPayload(T, pairing.canonicalKey, lobby, uuid, nameCleared)

    private fun baseMatch(
        pairing: GroupPairing,
        status: String = "draft",
        id: String = MATCH,
        matchNumber: Int = 1,
    ) = MatchCloudRestorePayload(
        id = id,
        tournamentId = T,
        matchNumber = matchNumber,
        matchDate = "2026-10-12",
        mapName = "Erangel",
        status = status,
        revision = 1,
        groupPairingKey = pairing.canonicalKey,
    )

    private fun uuidHeader(pairing: GroupPairing, status: String = "draft") =
        GroupRotationUuidMatchSnapshotRestorePayload(
            matchId = MATCH,
            tournamentId = T,
            pairingKey = pairing.canonicalKey,
            status = status,
            revision = 1,
            finalizedAt = if (status == "finalized") "2026-10-12T10:00:00Z" else null,
            finalizedBy = if (status == "finalized") OWNER else null,
        )

    private fun legacyResult(matchId: String) = MatchResultCloudRestorePayload(
        id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        matchId = matchId,
        teamSlotId = TournamentCloudIdentity.teamSlotId(
            java.util.UUID.fromString(T),
            1,
        ),
        placement = 1,
        kills = 4,
        participationStatus = "PARTICIPATED",
    )

    private fun legacyPairingMappings(pairing: GroupPairing) = (1..12).map { lobbySlotNumber ->
        GroupPairingLobbySlotUploadPayload(
            tournamentId = T,
            pairingKey = pairing.canonicalKey,
            lobbySlotNumber = lobbySlotNumber,
            teamSlotNumber = lobbySlotNumber,
        )
    }

    private fun uuidAssignment(lobby: Int, uuid: String) =
        GroupRotationUuidMatchAssignmentRestorePayload(MATCH, T, lobby, uuid)

    private fun uuidResult(uuid: String, status: String, placement: Int?, kills: Int) =
        GroupRotationUuidMatchResultRestorePayload(MATCH, T, uuid, status, placement, kills)

    private fun pairingPayload(pairing: GroupPairing) = GroupPairingUploadPayload(
        firstGroup = pairing.firstGroup.name,
        secondGroup = pairing.secondGroup.name,
        pairingKey = pairing.canonicalKey,
    )

    companion object {
        private const val T = "11111111-1111-4111-8111-111111111111"
        private const val OTHER_TOURNAMENT = "22222222-2222-4222-8222-222222222222"
        private const val OWNER = "33333333-3333-4333-8333-333333333333"
        private const val OTHER_OWNER = "44444444-4444-4444-8444-444444444444"
        private const val MATCH = "55555555-5555-4555-8555-555555555555"
        private const val U1 = "66666666-6666-4666-8666-666666666666"
        private const val U2 = "77777777-7777-4777-8777-777777777777"
        private const val UNKNOWN = "88888888-8888-4888-8888-888888888888"
        private val PAIRING_AB = GroupPairing(TournamentGroup.A, TournamentGroup.B)
        private val PAIRING_AC = GroupPairing(TournamentGroup.A, TournamentGroup.C)
    }
}
