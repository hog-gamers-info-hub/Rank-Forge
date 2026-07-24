package com.hoggamers.rankforge.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

@Composable
fun TournamentListPlaceholderScreen(
    onCreateTournament: () -> Unit,
) {
    RankForgeScreenContainer(
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.foundation_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.foundation_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.foundation_version),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onCreateTournament) {
            Text(text = stringResource(R.string.open_tournament_creation))
        }
    }
}
