package com.hoggamers.rankforge.domain.tournament

const val STANDARD_TOURNAMENT_SLOT_CAPACITY = 12
const val MAX_TOURNAMENT_SLOT_CAPACITY = 24
const val MAX_TEAMS_PER_GROUP = 6

enum class TournamentFormat {
    STANDARD,
    GROUP_ROTATION,
}

fun TournamentFormat.toCloudValue(): String = when (this) {
    TournamentFormat.STANDARD -> "standard"
    TournamentFormat.GROUP_ROTATION -> "group_rotation"
}

fun tournamentFormatFromCloudValue(value: String?): TournamentFormat? = when (value?.lowercase()) {
    null, "", "standard" -> TournamentFormat.STANDARD
    "group_rotation" -> TournamentFormat.GROUP_ROTATION
    else -> null
}

enum class TournamentGroup {
    A,
    B,
    C,
    D,
}

class GroupPairing private constructor(
    val firstGroup: TournamentGroup,
    val secondGroup: TournamentGroup,
) {
    init {
        require(firstGroup != secondGroup) { "A group cannot be paired with itself." }
        require(firstGroup.ordinal < secondGroup.ordinal) {
            "Group pairings must use canonical group ordering."
        }
    }

    val canonicalKey: String
        get() = "${firstGroup.name}:${secondGroup.name}"

    override fun equals(other: Any?): Boolean =
        other is GroupPairing &&
            firstGroup == other.firstGroup &&
            secondGroup == other.secondGroup

    override fun hashCode(): Int = 31 * firstGroup.hashCode() + secondGroup.hashCode()

    override fun toString(): String = "GroupPairing($firstGroup, $secondGroup)"

    companion object {
        operator fun invoke(
            firstGroup: TournamentGroup,
            secondGroup: TournamentGroup,
        ): GroupPairing = of(firstGroup, secondGroup)

        fun of(
            firstGroup: TournamentGroup,
            secondGroup: TournamentGroup,
        ): GroupPairing {
            require(firstGroup != secondGroup) { "A group cannot be paired with itself." }
            val ordered = listOf(firstGroup, secondGroup).sortedBy { it.ordinal }
            return GroupPairing(ordered[0], ordered[1])
        }

        fun fromCanonicalKey(canonicalKey: String): GroupPairing {
            val groups = canonicalKey.split(":")
            require(groups.size == 2) { "Invalid group pairing key." }
            return of(
                firstGroup = TournamentGroup.valueOf(groups[0]),
                secondGroup = TournamentGroup.valueOf(groups[1]),
            ).also { pairing ->
                require(pairing.canonicalKey == canonicalKey) {
                    "Group pairing key is not canonical."
                }
            }
        }
    }
}

fun Tournament.formatDerivedSlots(): List<TeamSlot> = when (format) {
    TournamentFormat.STANDARD -> TeamSlot.fixedSlotsForTournament(id)
    TournamentFormat.GROUP_ROTATION -> {
        val groups = TournamentGroup.entries.take(requireNotNull(groupCount))
        groups.flatMapIndexed { groupIndex, group ->
            val firstSlot = groupIndex * MAX_TEAMS_PER_GROUP + 1
            (firstSlot until firstSlot + MAX_TEAMS_PER_GROUP).map { slotNumber ->
                TeamSlot.create(
                    tournamentId = id,
                    slotNumber = slotNumber,
                    group = group,
                )
            }
        }
    }
}

fun defaultGroupPairings(groupCount: Int): List<GroupPairing> = when (groupCount) {
    3 -> listOf(
        GroupPairing(TournamentGroup.A, TournamentGroup.B),
        GroupPairing(TournamentGroup.B, TournamentGroup.C),
        GroupPairing(TournamentGroup.A, TournamentGroup.C),
    )
    4 -> listOf(
        GroupPairing(TournamentGroup.A, TournamentGroup.B),
        GroupPairing(TournamentGroup.B, TournamentGroup.C),
        GroupPairing(TournamentGroup.C, TournamentGroup.D),
        GroupPairing(TournamentGroup.A, TournamentGroup.D),
    )
    else -> emptyList()
}

fun availableGroupPairings(groupCount: Int): List<GroupPairing> =
    TournamentGroup.entries.take(groupCount).flatMapIndexed { firstIndex, firstGroup ->
        TournamentGroup.entries.drop(firstIndex + 1).take(groupCount - firstIndex - 1).map { secondGroup ->
            GroupPairing(firstGroup, secondGroup)
        }
    }
