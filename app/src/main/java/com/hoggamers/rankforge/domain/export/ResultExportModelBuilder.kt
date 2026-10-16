package com.hoggamers.rankforge.domain.export

import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchFinalizationValidator
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchParticipantResult
import com.hoggamers.rankforge.domain.tournament.GroupRotationMatchPersistenceSnapshot
import com.hoggamers.rankforge.domain.tournament.MatchParticipationStatus
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.KillPointsEngine
import com.hoggamers.rankforge.domain.tournament.MatchTotalEngine
import com.hoggamers.rankforge.domain.tournament.PositionPointsEngine
import com.hoggamers.rankforge.domain.tournament.TournamentFormat

class ResultExportModelBuilder(
    private val matchCsvExporter: MatchCsvExporter = MatchCsvExporter(),
    private val tournamentCsvExporter: TournamentCsvExporter = TournamentCsvExporter(),
    private val tournamentResultImageModelBuilder: TournamentResultImageModelBuilder =
        TournamentResultImageModelBuilder(),
) {
    fun buildMatch(
        input: MatchCsvExportInput,
    ): MatchResultExportModelBuildResult {
        val v4Snapshot = input.groupRotationV4MatchPersistenceSnapshot
        if (v4Snapshot != null) {
            return buildUuidBackedMatch(input, v4Snapshot)
        }
        return when (val result = matchCsvExporter.buildMatchRows(input)) {
            is MatchExportRowsResult.Success -> MatchResultExportModelBuildResult.Success(
                model = MatchResultExportModel(
                    tournamentName = input.tournament.name,
                    stageName = input.tournament.stageName,
                    matchNumber = input.match.matchNumber,
                    matchDate = input.match.date,
                    mapName = input.match.mapName,
                    rows = result.rows.map { row -> row.toResultExportRow() },
                ),
            )
            is MatchExportRowsResult.Failure -> MatchResultExportModelBuildResult.Failure(
                failures = result.failures,
            )
        }
    }

    private fun buildUuidBackedMatch(
        input: MatchCsvExportInput,
        snapshot: GroupRotationMatchPersistenceSnapshot,
    ): MatchResultExportModelBuildResult {
        if (input.match.status != MatchStatus.FINALIZED) {
            return MatchResultExportModelBuildResult.Failure(
                setOf(MatchCsvExportFailure.MATCH_NOT_FINALIZED),
            )
        }
        val identity = snapshot.identity
        val identityByUuid = identity.teams.associateBy { it.teamUUID }
        val validationIssues = GroupRotationMatchFinalizationValidator().validatePersistedSnapshot(
            snapshot = identity,
            participantResults = snapshot.participantResults,
        )
        if (
            input.tournament.format != TournamentFormat.GROUP_ROTATION ||
                input.match.groupPairing == null ||
                identity.tournamentId != input.tournament.id ||
                identity.pairing != input.match.groupPairing ||
                validationIssues.isNotEmpty() ||
                identityByUuid.size != identity.teams.size
        ) {
            return MatchResultExportModelBuildResult.Failure(
                setOf(MatchCsvExportFailure.INVALID_UUID_RESULT_DATA),
            )
        }

        val positionPointsEngine = PositionPointsEngine()
        val killPointsEngine = KillPointsEngine()
        val matchTotalEngine = MatchTotalEngine(positionPointsEngine, killPointsEngine)
        val resultsByUuid = snapshot.participantResults.associateBy { it.teamUUID }
        val orderedResults = snapshot.participantResults.sortedWith(
            compareBy<GroupRotationMatchParticipantResult> {
                it.participationStatus == MatchParticipationStatus.NO_SHOW
            }
                .thenByDescending { result ->
                    if (result.participationStatus == MatchParticipationStatus.NO_SHOW) {
                        0
                    } else {
                        matchTotalEngine(requireNotNull(result.placement), result.kills) +
                            result.pointAdjustment
                    }
                }
                .thenBy { it.placement ?: Int.MAX_VALUE }
                .thenBy { identityByUuid.getValue(it.teamUUID).lobbySlotNumber },
        )
        if (resultsByUuid.keys != identityByUuid.keys) {
            return MatchResultExportModelBuildResult.Failure(
                setOf(MatchCsvExportFailure.INVALID_UUID_RESULT_DATA),
            )
        }

        val correctionStatus = if (input.match.correctionHistory.isEmpty()) {
            "original_finalized"
        } else {
            "corrected_finalized"
        }
        val rows = orderedResults.mapIndexed { index, result ->
            val isNoShow = result.participationStatus == MatchParticipationStatus.NO_SHOW
            val placement = result.placement
            val placementPoints = if (isNoShow) 0 else positionPointsEngine(requireNotNull(placement))
            val killPoints = if (isNoShow) 0 else killPointsEngine(result.kills)
            val totalPoints = if (isNoShow) {
                0
            } else {
                matchTotalEngine(requireNotNull(placement), result.kills) + result.pointAdjustment
            }
            com.hoggamers.rankforge.domain.export.ResultExportRow(
                rank = if (isNoShow) null else index + 1,
                teamName = identityByUuid.getValue(result.teamUUID).displayName,
                win = if (placement == 1) 1 else 0,
                totalKills = result.kills,
                positionPoints = placementPoints,
                totalPoints = totalPoints,
                matchesPlayed = if (isNoShow) 0 else 1,
            )
        }
        return MatchResultExportModelBuildResult.Success(
            MatchResultExportModel(
                tournamentName = input.tournament.name,
                stageName = input.tournament.stageName,
                matchNumber = input.displayMatchNumber ?: input.match.matchNumber,
                matchDate = input.match.date,
                mapName = input.match.mapName,
                rows = rows,
            ),
        )
    }

    fun buildTournament(
        input: TournamentCsvExportInput,
    ): TournamentResultExportModelBuildResult =
        when (val result = tournamentCsvExporter.buildStandingsRows(input)) {
            is TournamentStandingsExportRowsResult.Success ->
                TournamentResultExportModelBuildResult.Success(
                    model = TournamentResultExportModel(
                        tournamentName = input.tournament.name,
                        stageName = input.tournament.stageName,
                        finalizedMatchCount = result.rows.first().exportedMatchCount,
                        rows = result.rows.map { row -> row.toResultExportRow() },
                    ),
                )
            is TournamentStandingsExportRowsResult.Failure ->
                TournamentResultExportModelBuildResult.Failure(
                    failures = result.failures,
                )
        }

    fun buildTournamentImage(
        input: TournamentCsvExportInput,
    ): TournamentResultImageModelBuildResult =
        tournamentResultImageModelBuilder.build(input)

    private fun MatchExportRow.toResultExportRow(): ResultExportRow =
        ResultExportRow(
            rank = if (participationStatus == MatchParticipationStatus.NO_SHOW.name) null else rowNumber,
            teamName = teamName,
            win = if (placement == 1) 1 else 0,
            totalKills = kills,
            positionPoints = placementPoints,
            totalPoints = totalPoints,
            matchesPlayed = if (participationStatus == MatchParticipationStatus.PARTICIPATED.name) 1 else 0,
        )

    private fun TournamentStandingsExportRow.toResultExportRow(): ResultExportRow =
        ResultExportRow(
            rank = standingsRank,
            teamName = teamName,
            win = firstPlaceCount,
            totalKills = totalKills,
            positionPoints = totalPositionPoints,
            totalPoints = totalPoints,
            matchesPlayed = matchesPlayed,
        )
}
