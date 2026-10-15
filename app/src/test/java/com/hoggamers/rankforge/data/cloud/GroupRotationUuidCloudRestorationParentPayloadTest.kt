package com.hoggamers.rankforge.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationUuidCloudRestorationParentPayloadTest {
    @Test
    fun fetchedGroupPairingsAreIncludedInTheParentPayloadAndMapSuccessfully() {
        val fetchedPairings = listOf(pairing("A:B"), pairing("A:C"), pairing("B:C"))
        val parent = groupParent().toUploadPayload(fetchedPairings)

        assertEquals(fetchedPairings, parent.selectedGroupPairings)
        assertTrue(
            TournamentCloudRestorationMapper.mapSnapshot(
                TournamentCloudRestorationPayloads(
                    tournament = parent,
                    teamSlots = groupSlots(),
                    players = emptyList(),
                ),
            ) is TournamentCloudRestorationMappingResult.Success,
        )
    }

    @Test
    fun missingGroupPairingsRemainInvalidWithoutFallback() {
        val parent = groupParent().toUploadPayload(emptyList())

        assertFalse(
            TournamentCloudRestorationMapper.mapSnapshot(
                TournamentCloudRestorationPayloads(
                    tournament = parent,
                    teamSlots = groupSlots(),
                    players = emptyList(),
                ),
            ) is TournamentCloudRestorationMappingResult.Success,
        )
    }

    @Test
    fun malformedDuplicateAndIncompatiblePairingsRemainInvalid() {
        val cases = listOf(
            listOf(GroupPairingUploadPayload("A", "B", "A:X")),
            listOf(pairing("A:B"), pairing("A:B")),
            listOf(pairing("A:D")),
        )

        cases.forEach { fetchedPairings ->
            val parent = groupParent().toUploadPayload(fetchedPairings)
            assertFalse(
                TournamentCloudRestorationMapper.mapSnapshot(
                    TournamentCloudRestorationPayloads(
                        tournament = parent,
                        teamSlots = groupSlots(),
                        players = emptyList(),
                    ),
                ) is TournamentCloudRestorationMappingResult.Success,
            )
        }
    }

    @Test
    fun standardParentPayloadRemainsValidWithNoGroupPairings() {
        val parent = standardParent().toUploadPayload(emptyList())

        assertTrue(
            TournamentCloudRestorationMapper.mapSnapshot(
                TournamentCloudRestorationPayloads(
                    tournament = parent,
                    teamSlots = standardSlots(),
                    players = emptyList(),
                ),
            ) is TournamentCloudRestorationMappingResult.Success,
        )
    }

    @Test
    fun legacyGroupMetadataRemainsValidWhenPairingsAreFetched() {
        val parent = groupParent().toUploadPayload(listOf(pairing("A:B")))

        assertTrue(
            TournamentCloudRestorationMapper.mapSnapshot(
                TournamentCloudRestorationPayloads(
                    tournament = parent,
                    teamSlots = groupSlots(includeLegacyGroups = true),
                    players = emptyList(),
                ),
            ) is TournamentCloudRestorationMappingResult.Success,
        )
    }

    private fun pairing(key: String): GroupPairingUploadPayload {
        val (first, second) = key.split(":")
        return GroupPairingUploadPayload(first, second, key)
    }

    private fun groupParent() = TournamentCloudRestorePayload(
        id = TOURNAMENT_ID,
        ownerId = OWNER_ID,
        name = "Group Rotation",
        stageName = "Stage",
        organizerContact = "",
        status = "draft",
        revision = 4,
        format = "group_rotation",
        groupCount = 3,
    )

    private fun standardParent() = TournamentCloudRestorePayload(
        id = TOURNAMENT_ID,
        ownerId = OWNER_ID,
        name = "Standard",
        stageName = "Stage",
        organizerContact = "",
        status = "draft",
        revision = 4,
        format = "standard",
    )

    private fun groupSlots(includeLegacyGroups: Boolean = false) = (1..18).map { slot ->
        TeamSlotUploadPayload(
            id = "slot-$slot",
            tournamentId = TOURNAMENT_ID,
            slotNumber = slot,
            teamName = "",
            status = "draft",
            group = if (includeLegacyGroups) {
                when (slot) {
                    in 1..6 -> "A"
                    in 7..12 -> "B"
                    else -> "C"
                }
            } else {
                null
            },
        )
    }

    private fun standardSlots() = (1..12).map { slot ->
        TeamSlotUploadPayload(
            id = "slot-$slot",
            tournamentId = TOURNAMENT_ID,
            slotNumber = slot,
            teamName = "",
            status = "draft",
        )
    }

    companion object {
        private const val TOURNAMENT_ID = "11111111-1111-4111-8111-111111111111"
        private const val OWNER_ID = "22222222-2222-4222-8222-222222222222"
    }
}
