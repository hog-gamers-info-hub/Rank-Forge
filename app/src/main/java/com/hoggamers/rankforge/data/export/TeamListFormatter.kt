package com.hoggamers.rankforge.data.export

data class TeamListEntry(
    val slotNumber: Int,
    val teamName: String,
)

object TeamListFormatter {
    fun format(
        tournamentName: String,
        stageName: String,
        entries: List<TeamListEntry>,
    ): String = buildList {
        add(tournamentName.trim())
        add("Stage: ${stageName.trim()}")
        add("")
        add("Team-List:")
        entries
            .asSequence()
            .map { entry -> entry.copy(teamName = entry.teamName.trim()) }
            .filter { it.teamName.isNotEmpty() }
            .sortedBy { it.slotNumber }
            .forEach { entry ->
                add("${entry.slotNumber.toString().padStart(2, '0')}. ${entry.teamName}")
            }
    }.joinToString("\n")

    fun fileName(tournamentName: String): String {
        val sanitized = tournamentName
            .filterNot { character ->
                character.isISOControl() || character in FORBIDDEN_FILENAME_CHARACTERS
            }
            .trim()
            .trim('.', ' ')
        return if (sanitized.isEmpty()) {
            "Team_List.txt"
        } else {
            "$sanitized.txt"
        }
    }

    private val FORBIDDEN_FILENAME_CHARACTERS = setOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
}
