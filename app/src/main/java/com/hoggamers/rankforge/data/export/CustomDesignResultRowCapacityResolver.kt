package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignRowCapacity
import com.hoggamers.rankforge.domain.tournament.TournamentFormat

/** One source of truth for the required Custom Design geometry for a result request. */
fun ResultDownloadRequest.requiredCustomDesignRowCapacityOrNull(): Int? = when (this) {
    is ResultDownloadRequest.CurrentMatch -> CustomDesignRowCapacity.DEFAULT_ROW_COUNT
    is ResultDownloadRequest.WholeTournament -> when {
        input.tournament.format != TournamentFormat.GROUP_ROTATION ->
            CustomDesignRowCapacity.DEFAULT_ROW_COUNT
        input.pairingScope != null -> CustomDesignRowCapacity.DEFAULT_ROW_COUNT
        input.tournament.groupCount == 3 -> 18
        input.tournament.groupCount == 4 -> 24
        else -> null
    }
}

fun requiredCustomDesignRowCapacityOrNull(
    tournamentFormat: TournamentFormat?,
    groupCount: Int?,
    isCurrentMatch: Boolean,
    pairingSelected: Boolean,
): Int? = when {
    isCurrentMatch -> CustomDesignRowCapacity.DEFAULT_ROW_COUNT
    tournamentFormat != TournamentFormat.GROUP_ROTATION -> CustomDesignRowCapacity.DEFAULT_ROW_COUNT
    pairingSelected -> CustomDesignRowCapacity.DEFAULT_ROW_COUNT
    groupCount == 3 -> 18
    groupCount == 4 -> 24
    else -> null
}
