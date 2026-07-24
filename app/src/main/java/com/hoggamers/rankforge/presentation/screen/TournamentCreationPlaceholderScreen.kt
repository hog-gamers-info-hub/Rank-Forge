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
fun TournamentCreationPlaceholderScreen(
    onBack: () -> Unit,
) {
    RankForgeScreenContainer(
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.tournament_creation_placeholder_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.tournament_creation_placeholder_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(onClick = onBack) {
            Text(text = stringResource(R.string.back_action))
        }
    }
}
