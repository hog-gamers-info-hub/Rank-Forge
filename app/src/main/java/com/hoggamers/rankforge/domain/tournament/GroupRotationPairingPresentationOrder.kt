package com.hoggamers.rankforge.domain.tournament

fun orderedGroupRotationPairings(tournament: Tournament): List<GroupPairing> {
    val selected = tournament.selectedGroupPairings
    val selectedKeys = selected.map { it.canonicalKey }.toSet()
    val preferred = defaultGroupPairings(requireNotNull(tournament.groupCount))
        .filter { it.canonicalKey in selectedKeys }
    val preferredKeys = preferred.map { it.canonicalKey }.toSet()
    return preferred + selected
        .filter { it.canonicalKey !in preferredKeys }
        .sortedBy { it.canonicalKey }
}
