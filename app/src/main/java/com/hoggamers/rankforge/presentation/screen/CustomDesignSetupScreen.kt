package com.hoggamers.rankforge.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val CUSTOM_DESIGN_SETUP_SCREEN_TEST_TAG = "custom_design_setup_screen"
const val CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG = "custom_design_team_name_field"
const val CUSTOM_DESIGN_WIN_FIELD_TEST_TAG = "custom_design_win_field"
const val CUSTOM_DESIGN_TOTAL_KILLS_FIELD_TEST_TAG = "custom_design_total_kills_field"
const val CUSTOM_DESIGN_POSITION_POINTS_FIELD_TEST_TAG = "custom_design_position_points_field"
const val CUSTOM_DESIGN_TOTAL_POINTS_FIELD_TEST_TAG = "custom_design_total_points_field"
const val CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG = "custom_design_upload_action"

@Composable
fun CustomDesignSetupRoute(
    onBack: () -> Unit,
    onDesignSelected: (String) -> Unit = {},
) {
    var teamName by rememberSaveable { mutableStateOf("") }
    var win by rememberSaveable { mutableStateOf("") }
    var totalKills by rememberSaveable { mutableStateOf("") }
    var positionPoints by rememberSaveable { mutableStateOf("") }
    var totalPoints by rememberSaveable { mutableStateOf("") }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { selectedUri ->
            selectedUri?.toString()?.let { uri ->
                onDesignSelected(uri)
            }
        },
    )

    BackHandler(onBack = onBack)

    CustomDesignSetupScreen(
        teamName = teamName,
        onTeamNameChanged = { teamName = it },
        win = win,
        onWinChanged = { win = it },
        totalKills = totalKills,
        onTotalKillsChanged = { totalKills = it },
        positionPoints = positionPoints,
        onPositionPointsChanged = { positionPoints = it },
        totalPoints = totalPoints,
        onTotalPointsChanged = { totalPoints = it },
        onUploadCustomDesign = {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
    )
}

@Composable
fun CustomDesignSetupScreen(
    teamName: String = "",
    onTeamNameChanged: (String) -> Unit = {},
    win: String = "",
    onWinChanged: (String) -> Unit = {},
    totalKills: String = "",
    onTotalKillsChanged: (String) -> Unit = {},
    positionPoints: String = "",
    onPositionPointsChanged: (String) -> Unit = {},
    totalPoints: String = "",
    onTotalPointsChanged: (String) -> Unit = {},
    onUploadCustomDesign: () -> Unit = {},
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(CUSTOM_DESIGN_SETUP_SCREEN_TEST_TAG)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.custom_design_setup_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        CustomDesignLabelField(
            value = teamName,
            onValueChange = onTeamNameChanged,
            label = stringResource(R.string.custom_design_team_name_label),
            testTag = CUSTOM_DESIGN_TEAM_NAME_FIELD_TEST_TAG,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        CustomDesignLabelField(
            value = win,
            onValueChange = onWinChanged,
            label = stringResource(R.string.custom_design_win_label),
            testTag = CUSTOM_DESIGN_WIN_FIELD_TEST_TAG,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        CustomDesignLabelField(
            value = totalKills,
            onValueChange = onTotalKillsChanged,
            label = stringResource(R.string.custom_design_total_kills_label),
            testTag = CUSTOM_DESIGN_TOTAL_KILLS_FIELD_TEST_TAG,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        CustomDesignLabelField(
            value = positionPoints,
            onValueChange = onPositionPointsChanged,
            label = stringResource(R.string.custom_design_position_points_label),
            testTag = CUSTOM_DESIGN_POSITION_POINTS_FIELD_TEST_TAG,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        CustomDesignLabelField(
            value = totalPoints,
            onValueChange = onTotalPointsChanged,
            label = stringResource(R.string.custom_design_total_points_label),
            testTag = CUSTOM_DESIGN_TOTAL_POINTS_FIELD_TEST_TAG,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Button(
            onClick = onUploadCustomDesign,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CUSTOM_DESIGN_UPLOAD_ACTION_TEST_TAG),
        ) {
            Text(stringResource(R.string.custom_design_upload_action))
        }
    }
}

@Composable
private fun CustomDesignLabelField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}
