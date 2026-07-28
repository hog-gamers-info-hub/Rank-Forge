package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.sync.CloudRevision
import com.hoggamers.rankforge.domain.sync.RevisionConflict
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RevisionWriteResponse(
    val outcome: String,
    val revision: Int? = null,
)

@Serializable
data class TournamentSnapshotWriteParameters(
    @SerialName("p_tournament") val tournament: TournamentUploadPayload,
    @SerialName("p_team_slots") val teamSlots: List<TeamSlotUploadPayload>,
    @SerialName("p_players") val players: List<PlayerUploadPayload>,
    @SerialName("p_expected_revision") val expectedRevision: Int,
)

@Serializable
data class MatchSnapshotWriteParameters<M, R>(
    @SerialName("p_tournament_id") val tournamentId: String,
    @SerialName("p_matches") val matches: List<M>,
    @SerialName("p_match_results") val matchResults: List<R>,
    @SerialName("p_expected_revision") val expectedRevision: Int,
)

@Serializable
data class ProtectedMatchFinalizationParameters<M, R>(
    @SerialName("p_tournament_id") val tournamentId: String,
    @SerialName("p_match") val match: M,
    @SerialName("p_match_results") val matchResults: List<R>,
    @SerialName("p_expected_revision") val expectedRevision: Int,
)

fun RevisionWriteResponse.toRevisionConflict(expectedRevision: Int): RevisionConflict =
    if (outcome == "stale_write" && expectedRevision > 0 && revision != null && revision > 0) {
        RevisionConflict.StaleWrite(CloudRevision(expectedRevision), CloudRevision(revision))
    } else {
        RevisionConflict.MissingRevision
    }
