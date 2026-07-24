package com.hoggamers.rankforge.presentation.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

@Composable
fun RankForgeLoadingState(message: String) {
    RankForgeScreenContainer {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun RankForgeEmptyState(
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    RankForgeMessageState(title, description, actionLabel, onAction)
}

@Composable
fun RankForgeSuccessState(
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    RankForgeMessageState(title, description, actionLabel, onAction)
}

@Composable
fun RankForgeWarningState(
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    RankForgeMessageState(title, description, actionLabel, onAction)
}

@Composable
fun RankForgeErrorState(
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    RankForgeMessageState(title, description, actionLabel, onAction)
}

@Composable
private fun RankForgeMessageState(
    title: String,
    description: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
) {
    RankForgeScreenContainer {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            Button(onClick = onAction) {
                Text(text = actionLabel)
            }
        }
    }
}
