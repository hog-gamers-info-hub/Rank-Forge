package com.hoggamers.rankforge.data.ocr.matchlobby

import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MatchLobbyOcrCacheCodec @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(slots: List<MatchLobbyPlayersOcrSlot>): String =
        json.encodeToString(MatchLobbyOcrCachedPayload(slots.map { it.toDto() }))

    fun decode(payload: String): List<MatchLobbyPlayersOcrSlot>? = runCatching {
        val decoded = json.decodeFromString<MatchLobbyOcrCachedPayload>(payload)
        if (decoded.payloadVersion != MATCH_LOBBY_OCR_CACHE_PAYLOAD_VERSION) return null

        val slots = decoded.slots
        val slotNumbers = slots.map { it.slotNumber }
        if (slots.size != REQUIRED_SLOT_COUNT ||
            slotNumbers.distinct().size != slots.size ||
            APPROVED_SEMANTIC_SLOT_GROUPS.none { it == slotNumbers.toSet() }
        ) return null

        slots.map { slot ->
            val players = slot.players
            if (players.size != REQUIRED_PLAYER_COUNT ||
                players.map { it.playerNumber }.distinct().size != players.size ||
                players.map { it.playerNumber }.toSet() != REQUIRED_PLAYER_NUMBERS
            ) return null
            MatchLobbyPlayersOcrSlot(
                slotNumber = slot.slotNumber,
                players = players.sortedBy { it.playerNumber }.map { player ->
                    MatchLobbyPlayersOcrPlayer(player.playerNumber, player.playerName)
                },
            )
        }.sortedBy { it.slotNumber }
    }.getOrNull()
}

private const val MATCH_LOBBY_OCR_CACHE_PAYLOAD_VERSION = 2
private const val REQUIRED_SLOT_COUNT = 4
private const val REQUIRED_PLAYER_COUNT = 4
private val REQUIRED_PLAYER_NUMBERS = (1..REQUIRED_PLAYER_COUNT).toSet()
private val APPROVED_SEMANTIC_SLOT_GROUPS = listOf(
    (1..4).toSet(),
    (5..8).toSet(),
    (9..12).toSet(),
)

@Serializable
private data class MatchLobbyOcrCachedPayload(
    val slots: List<MatchLobbyOcrCachedSlot>,
    val payloadVersion: Int = MATCH_LOBBY_OCR_CACHE_PAYLOAD_VERSION,
)

@Serializable
private data class MatchLobbyOcrCachedSlot(
    val slotNumber: Int,
    val players: List<MatchLobbyOcrCachedPlayer>,
)

@Serializable
private data class MatchLobbyOcrCachedPlayer(
    val playerNumber: Int,
    val playerName: String?,
)

private fun MatchLobbyPlayersOcrSlot.toDto() = MatchLobbyOcrCachedSlot(
    slotNumber = slotNumber,
    players = players.map { it.toDto() },
)

private fun MatchLobbyPlayersOcrPlayer.toDto() = MatchLobbyOcrCachedPlayer(
    playerNumber = playerNumber,
    playerName = playerName,
)
