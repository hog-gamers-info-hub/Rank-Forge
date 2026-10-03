package com.hoggamers.rankforge.domain.tournament

data class Tournament(
    val id: String,
    val name: String,
    val stageName: String,
    val organizerContactNumber: String,
    val status: TournamentStatus,
    val ownerUserId: String? = null,
    val format: TournamentFormat = TournamentFormat.STANDARD,
    val groupCount: Int? = null,
    val selectedGroupPairings: List<GroupPairing> = emptyList(),
) {
    init {
        when (format) {
            TournamentFormat.STANDARD -> {
                require(groupCount == null) { "Standard tournaments cannot define groups." }
                require(selectedGroupPairings.isEmpty()) {
                    "Standard tournaments cannot define group pairings."
                }
            }
            TournamentFormat.GROUP_ROTATION -> {
                require(groupCount == 3 || groupCount == 4) {
                    "Group Rotation tournaments must have 3 or 4 groups."
                }
                require(selectedGroupPairings.isNotEmpty()) {
                    "Group Rotation tournaments must select at least one pairing."
                }
                require(selectedGroupPairings.map { it.canonicalKey }.distinct().size == selectedGroupPairings.size) {
                    "Group pairings must be unique."
                }
                require(selectedGroupPairings.all { pairing ->
                    pairing.firstGroup.ordinal < groupCount && pairing.secondGroup.ordinal < groupCount
                }) {
                    "Group pairings must use selected tournament groups."
                }
            }
        }
    }
}
