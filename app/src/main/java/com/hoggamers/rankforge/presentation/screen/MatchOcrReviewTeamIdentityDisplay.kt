package com.hoggamers.rankforge.presentation.screen

/**
 * Formats the user-facing identity without exposing Group Rotation's permanent slot number.
 * The canonical slot remains the value carried by the draft and evidence models.
 */
internal fun displayTeamIdentityLabel(
    teamSlot: Int?,
    teamNamesBySlot: Map<Int, String>,
    lobbySlotByTeamSlot: Map<Int, Int>,
    usesPairRelativeIdentity: Boolean,
): String {
    if (teamSlot == null) return "Not matched"

    val teamName = teamNamesBySlot[teamSlot]
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    if (!usesPairRelativeIdentity) {
        return teamName ?: "Slot $teamSlot"
    }

    val lobbySlot = lobbySlotByTeamSlot[teamSlot]
    val lobbyLabel = lobbySlot?.let {
        "Lobby ${it.toString().padStart(2, '0')}"
    }

    return when {
        lobbyLabel != null && teamName != null ->
            "$lobbyLabel \u00B7 $teamName"
        lobbyLabel != null -> lobbyLabel
        teamName != null -> teamName
        else -> "Unnamed team"
    }
}

internal fun canonicalTeamSlotForLobby(
    lobbySlot: Int,
    lobbySlotByTeamSlot: Map<Int, Int>,
): Int? = lobbySlotByTeamSlot.entries
    .firstOrNull { (_, mappedLobbySlot) -> mappedLobbySlot == lobbySlot }
    ?.key
