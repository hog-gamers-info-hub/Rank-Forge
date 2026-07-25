package com.hoggamers.rankforge.domain.tournament

object RosterNameNormalizer {
    fun normalize(value: String): String = value.trim()
}

data class RosterValidationTeam(
    val slotNumber: Int,
    val teamName: String,
    val players: List<RosterValidationPlayer>,
)

data class RosterValidationPlayer(
    val playerIndex: Int,
    val displayName: String,
)

sealed interface RosterValidationIssue {
    val slotNumber: Int
    val isBlocking: Boolean

    data class MissingTeamName(
        override val slotNumber: Int,
    ) : RosterValidationIssue {
        override val isBlocking: Boolean = false
    }

    data class DuplicateTeamName(
        override val slotNumber: Int,
        val firstSlotNumber: Int,
        val normalizedName: String,
    ) : RosterValidationIssue {
        override val isBlocking: Boolean = true
    }

    data class InvalidPlayerCount(
        override val slotNumber: Int,
        val playerCount: Int,
    ) : RosterValidationIssue {
        override val isBlocking: Boolean = false
    }

    data class DuplicatePlayerName(
        override val slotNumber: Int,
        val playerIndex: Int,
        val firstPlayerIndex: Int,
        val normalizedName: String,
    ) : RosterValidationIssue {
        override val isBlocking: Boolean = true
    }
}

data class RosterValidationResult(
    val issues: List<RosterValidationIssue>,
) {
    val hasBlockingIssues: Boolean
        get() = issues.any { it.isBlocking }
}

class RosterValidator {
    fun validate(teams: List<RosterValidationTeam>): RosterValidationResult {
        val orderedTeams = teams.sortedBy { it.slotNumber }
        val issues = mutableListOf<RosterValidationIssue>()
        val firstSlotByTeamName = linkedMapOf<String, Int>()

        orderedTeams.forEach { team ->
            val normalizedTeamName = RosterNameNormalizer.normalize(team.teamName)
            if (normalizedTeamName.isEmpty()) {
                issues += RosterValidationIssue.MissingTeamName(team.slotNumber)
            } else {
                val firstSlotNumber = firstSlotByTeamName[normalizedTeamName]
                if (firstSlotNumber == null) {
                    firstSlotByTeamName[normalizedTeamName] = team.slotNumber
                } else {
                    issues += RosterValidationIssue.DuplicateTeamName(
                        slotNumber = team.slotNumber,
                        firstSlotNumber = firstSlotNumber,
                        normalizedName = normalizedTeamName,
                    )
                }
            }

            if (team.players.size !in RosterPlayer.MIN_PLAYERS + 4..RosterPlayer.MAX_PLAYERS) {
                issues += RosterValidationIssue.InvalidPlayerCount(
                    slotNumber = team.slotNumber,
                    playerCount = team.players.size,
                )
            }

            val firstPlayerByName = linkedMapOf<String, Int>()
            team.players
                .sortedBy { it.playerIndex }
                .forEach { player ->
                    val normalizedPlayerName = RosterNameNormalizer.normalize(player.displayName)
                    if (normalizedPlayerName.isNotEmpty()) {
                        val firstPlayerIndex = firstPlayerByName[normalizedPlayerName]
                        if (firstPlayerIndex == null) {
                            firstPlayerByName[normalizedPlayerName] = player.playerIndex
                        } else {
                            issues += RosterValidationIssue.DuplicatePlayerName(
                                slotNumber = team.slotNumber,
                                playerIndex = player.playerIndex,
                                firstPlayerIndex = firstPlayerIndex,
                                normalizedName = normalizedPlayerName,
                            )
                        }
                    }
                }
        }

        return RosterValidationResult(issues = issues.toList())
    }
}
