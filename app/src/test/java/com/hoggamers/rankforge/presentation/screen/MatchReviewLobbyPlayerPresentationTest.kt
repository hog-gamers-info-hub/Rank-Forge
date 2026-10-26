package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.ocr.matchlobby.LobbyPlayerRowCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreview
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewImage
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewOutcome
import com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewResult
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchReviewLobbyPlayerPresentationTest {
    @Test
    fun croppedSlotsAreRemovedFromStandalonePlayerDetails() {
        val state = stateWithSlots(1, 2, 3, 4)

        val remaining = state.lobbyPlayersNotCoveredByDisplayedCrops(
            mapOf(1 to cropResult(1, 2, 3, 4)),
        )

        assertEquals(emptyList<Int>(), remaining.map { it.slotNumber })
    }

    @Test
    fun unavailableCropsKeepSavedTextOnlyPlayerDetails() {
        val state = stateWithSlots(1, 2)

        val remaining = state.lobbyPlayersNotCoveredByDisplayedCrops(
            mapOf(1 to MatchLobbyTeamCropPreviewResult.Unavailable(
                com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewUnavailableReason.BITMAP_CREATION_FAILED,
            )),
        )

        assertEquals(listOf(1, 2), remaining.map { it.slotNumber })
    }

    @Test
    fun partialCropRestorationKeepsOnlyUncoveredSlotsInTextFallback() {
        val state = stateWithSlots(1, 2, 3, 4)

        val remaining = state.lobbyPlayersNotCoveredByDisplayedCrops(
            mapOf(1 to cropResult(localSlots = listOf(1, 3), canonicalTeamSlot = 0)),
        )

        assertEquals(listOf(2, 4), remaining.map { it.slotNumber })
    }

    @Test
    fun groupRotationUsesLocalLobbySlotNotCanonicalTeamIdentity() {
        val state = MatchOcrReviewUiState.Empty(
            isUuidBackedGroupRotation = true,
            usesPairRelativeIdentity = true,
            lobbySlotByTeamSlot = mapOf(13 to 1),
            lobbyPlayers = listOf(slot(1), slot(2)),
        )

        val remaining = state.lobbyPlayersNotCoveredByDisplayedCrops(
            mapOf(1 to cropResult(localSlot = 1, canonicalTeamSlot = 13)),
        )

        assertEquals(listOf(2), remaining.map { it.slotNumber })
    }

    @Test
    fun groupRotationMapsCanonicalIdentityOutsideLocalRangeToItsLocalLobbySlot() {
        val state = MatchOcrReviewUiState.Empty(
            usesPairRelativeIdentity = true,
            lobbySlotByTeamSlot = mapOf(18 to 3),
            lobbyPlayers = listOf(slot(3), slot(4)),
        )

        val remaining = state.lobbyPlayersNotCoveredByDisplayedCrops(
            mapOf(1 to cropResult(localSlot = 3, canonicalTeamSlot = 18)),
        )

        assertEquals(listOf(4), remaining.map { it.slotNumber })
    }

    @Test
    fun missingLobbyEvidenceDoesNotHideSavedPlayerDetails() {
        val state = stateWithSlots(1, 2)

        val remaining = state.lobbyPlayersNotCoveredByDisplayedCrops(emptyMap())

        assertEquals(listOf(1, 2), remaining.map { it.slotNumber })
    }

    @Test
    fun cropWithoutPlayerDetailsDoesNotHideStandalonePlayerDetails() {
        val state = stateWithSlots(1)
        val preview = MatchLobbyTeamCropPreview(
            visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
            detectedSlotNumber = 1,
            image = TestPreviewImage,
            playerRowPreviews = emptyList(),
        )

        val remaining = state.lobbyPlayersNotCoveredByDisplayedCrops(
            mapOf(
                1 to MatchLobbyTeamCropPreviewResult.Available(
                    previews = listOf(preview),
                    unavailable = RosterVisibleSlotPosition.entries.drop(1).map { position ->
                        MatchLobbyTeamCropPreviewOutcome.Unavailable(
                            visibleSlotPosition = position,
                            reason = com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE,
                        )
                    },
                ),
            ),
        )

        assertEquals(listOf(1), remaining.map { it.slotNumber })
    }

    @Test
    fun filteringLobbyPlayersDoesNotAlterResultDetails() {
        val resultDetails = MatchResultOcrPreviewUiState.Ready(
            roles = emptyList(),
            rows = emptyList(),
            ignoredLowerRows = emptyList(),
            manualReviewRows = emptyList(),
        )
        val state = MatchOcrReviewUiState.Empty(
            matchResultOcrPreview = resultDetails,
            lobbyPlayers = listOf(slot(1)),
        )

        state.lobbyPlayersNotCoveredByDisplayedCrops(mapOf(1 to cropResult(1)))

        assertEquals(resultDetails, state.matchResultOcrPreview)
    }

    private fun stateWithSlots(vararg slots: Int) = MatchOcrReviewUiState.Empty(
        lobbyPlayers = slots.map(::slot),
    )

    private fun slot(slot: Int) = MatchOcrReviewLobbySlotUiState(
        slotNumber = slot,
        players = listOf(
            MatchOcrReviewLobbyPlayerUiState(playerNumber = 1, playerName = "Player $slot"),
        ),
    )

    private fun cropResult(
        vararg localSlots: Int,
    ): MatchLobbyTeamCropPreviewResult.Available = cropResult(
        localSlots = localSlots.toList(),
        canonicalTeamSlot = 0,
    )

    private fun cropResult(
        localSlot: Int,
        canonicalTeamSlot: Int,
    ): MatchLobbyTeamCropPreviewResult.Available = cropResult(
        localSlots = listOf(localSlot),
        canonicalTeamSlot = canonicalTeamSlot,
    )

    private fun cropResult(
        localSlots: List<Int>,
        canonicalTeamSlot: Int,
    ): MatchLobbyTeamCropPreviewResult.Available {
        val previews = localSlots.mapIndexed { index, localSlot ->
            MatchLobbyTeamCropPreview(
                visibleSlotPosition = RosterVisibleSlotPosition.entries[index],
                detectedSlotNumber = localSlot,
                image = TestPreviewImage,
                playerRowPreviews = listOf(
                    LobbyPlayerRowCropPreview(
                        row = LobbyPlayerRow.ROW_1,
                        boundsInTeamCrop = LobbyPlayerRowCropBounds(0, 0, 1, 1),
                        slotAnchorSource = LobbySlotAnchorSource.TEAM_CROP_CENTER_FALLBACK,
                        slotAnchorY = 0.5,
                        structuralEvidence = null,
                        playerName = "Player $localSlot",
                    ),
                ),
                authoritativeTeamSlotNumber = canonicalTeamSlot,
            )
        }
        val unavailable = RosterVisibleSlotPosition.entries
            .filterNot { position -> position in previews.map { it.visibleSlotPosition } }
            .map { position ->
                MatchLobbyTeamCropPreviewOutcome.Unavailable(
                    visibleSlotPosition = position,
                    reason = com.hoggamers.rankforge.data.ocr.matchlobby.MatchLobbyTeamCropPreviewUnavailableReason.REQUIRED_SLOT_NUMBER_UNAVAILABLE,
                )
            }
        return MatchLobbyTeamCropPreviewResult.Available(previews, unavailable)
    }

    private data object TestPreviewImage : MatchLobbyTeamCropPreviewImage
}
