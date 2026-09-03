package com.hoggamers.rankforge.data.local

import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MatchCalculatedEvidenceCodec @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeLobby(evidence: LobbyCalculatedEvidence): String = json.encodeToString(evidence)

    fun decodeLobby(payload: String): LobbyCalculatedEvidence? = runCatching {
        json.decodeFromString<LobbyCalculatedEvidence>(payload)
    }.getOrNull()

    fun encodeResult(evidence: ResultCalculatedEvidence): String = json.encodeToString(evidence)

    fun decodeResult(payload: String): ResultCalculatedEvidence? = runCatching {
        json.decodeFromString<ResultCalculatedEvidence>(payload)
    }.getOrNull()

    fun decode(lobbyPayload: String, resultPayload: String): MatchCalculatedEvidence? = runCatching {
        MatchCalculatedEvidence(
            lobby = json.decodeFromString<LobbyCalculatedEvidence>(lobbyPayload),
            result = json.decodeFromString<ResultCalculatedEvidence>(resultPayload),
        )
    }.getOrNull()
}
