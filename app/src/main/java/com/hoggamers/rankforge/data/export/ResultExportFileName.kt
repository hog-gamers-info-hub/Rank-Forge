package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel

enum class ResultExportFileFormat(
    val extension: String,
    val mimeType: String,
) {
    PDF("pdf", "application/pdf"),
    PNG("png", "image/png"),
}

object ResultExportFileName {
    const val MAX_TOURNAMENT_COMPONENT_LENGTH = 80
    const val FALLBACK_TOURNAMENT_COMPONENT = "Tournament"

    fun forMatch(
        model: MatchResultExportModel,
        format: ResultExportFileFormat,
    ): String = withExtension(
        baseName = "RankForge_${sanitizeTournamentComponent(model.tournamentName)}_" +
            "Match_${model.matchNumber}_Result",
        format = format,
    )

    fun forTournament(
        model: TournamentResultExportModel,
        format: ResultExportFileFormat,
    ): String = withExtension(
        baseName = "RankForge_${sanitizeTournamentComponent(model.tournamentName)}_Tournament_Result",
        format = format,
    )

    fun sanitizeTournamentComponent(value: String): String {
        val sanitized = buildString {
            value.trim().forEach { character ->
                if (character.isISOControl() || character.isWhitespace() || character in FORBIDDEN_CHARACTERS) {
                    if (isNotEmpty() && last() != SEPARATOR) {
                        append(SEPARATOR)
                    }
                } else if (character != SEPARATOR || isNotEmpty() && last() != SEPARATOR) {
                    append(character)
                }
            }
        }.trim(SEPARATOR, '.')
            .take(MAX_TOURNAMENT_COMPONENT_LENGTH)
            .trim(SEPARATOR, '.')

        return sanitized.ifBlank { FALLBACK_TOURNAMENT_COMPONENT }
    }

    private fun withExtension(
        baseName: String,
        format: ResultExportFileFormat,
    ): String = "$baseName.${format.extension}"

    private const val SEPARATOR = '_'
    private val FORBIDDEN_CHARACTERS = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
}
