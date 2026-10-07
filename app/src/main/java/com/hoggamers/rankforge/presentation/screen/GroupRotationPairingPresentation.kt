package com.hoggamers.rankforge.presentation.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.tournament.GroupPairing
import com.hoggamers.rankforge.domain.tournament.TournamentGroup

@Composable
fun groupRotationPairingLabel(pairing: GroupPairing): String = stringResource(
    R.string.group_rotation_pairing_label,
    groupRotationGroupLabel(pairing.firstGroup),
    groupRotationGroupLabel(pairing.secondGroup),
)

@Composable
private fun groupRotationGroupLabel(group: TournamentGroup): String = stringResource(
    when (group) {
        TournamentGroup.A -> R.string.tournament_group_a
        TournamentGroup.B -> R.string.tournament_group_b
        TournamentGroup.C -> R.string.tournament_group_c
        TournamentGroup.D -> R.string.tournament_group_d
    },
)
