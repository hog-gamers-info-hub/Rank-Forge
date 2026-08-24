package com.hoggamers.rankforge.presentation.navigation

import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import android.graphics.Bitmap
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchResultScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchResultScreenshotCropSaveResult
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.auth.AUTH_ACCOUNT_BACK_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AUTH_ACCOUNT_HOME_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AUTH_LOGOUT_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AUTH_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportExecutionResult
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportRemoteDataSource
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.CheckTournamentQuotaUseCase
import com.hoggamers.rankforge.domain.tournament.LocalDeletionRepository
import com.hoggamers.rankforge.domain.tournament.LocalDeletionResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadAction
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadResult
import com.hoggamers.rankforge.domain.tournament.TournamentQuotaRepository
import com.hoggamers.rankforge.domain.tournament.TournamentQuotaResult
import com.hoggamers.rankforge.domain.tournament.CreateMatchUseCase
import com.hoggamers.rankforge.domain.tournament.CreateMatchInput
import com.hoggamers.rankforge.domain.tournament.CreateMatchResult
import com.hoggamers.rankforge.domain.tournament.CreateNextMatchUseCase
import com.hoggamers.rankforge.domain.tournament.ConfirmTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterPlayersUseCase
import com.hoggamers.rankforge.domain.tournament.SaveRosterUseCase
import com.hoggamers.rankforge.domain.ocr.matchlobby.MatchLobbyAutoCropProposer
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.RosterPlayer
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.presentation.screen.TEAM_ENTRY_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TeamEntryViewModel
import com.hoggamers.rankforge.presentation.screen.ROSTER_ENTRY_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.RosterEntryViewModel
import com.hoggamers.rankforge.presentation.screen.RosterReviewViewModel
import com.hoggamers.rankforge.presentation.screen.RosterOcrReviewViewModel
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader
import com.hoggamers.rankforge.presentation.screen.MatchLobbyScreenshotCropViewModel
import com.hoggamers.rankforge.presentation.screen.MatchLobbyScreenshotUploadCheckpointAction
import com.hoggamers.rankforge.presentation.screen.MatchLobbyScreenshotUploadCheckpointResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetEntity
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetRepository
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotAssetSaveResult
import com.hoggamers.rankforge.data.local.MatchLobbyScreenshotCropSaveResult
import com.hoggamers.rankforge.data.local.ScreenshotLocalStatus
import com.hoggamers.rankforge.data.local.ScreenshotUploadStatus
import com.hoggamers.rankforge.presentation.screen.ROSTER_REVIEW_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ROSTER_REVIEW_CONFIRM_BUTTON_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ROSTER_SCREENSHOT_CROP_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ImageCandidateMetadataReader
import com.hoggamers.rankforge.presentation.screen.ImageCandidateReadResult
import com.hoggamers.rankforge.presentation.screen.ImageCandidateValidator
import com.hoggamers.rankforge.presentation.screen.ImageSourceFingerprintGenerator
import com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener
import com.hoggamers.rankforge.presentation.screen.RosterScreenshotIntakeViewModel
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_DETAILS_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_EMPTY_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_DRAWER_TEST_TAG
import com.hoggamers.rankforge.presentation.component.LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ALL_TOURNAMENTS_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.ALL_TOURNAMENTS_BACK_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TournamentDetailsViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentListViewModel
import com.hoggamers.rankforge.presentation.screen.TournamentCreationViewModel
import com.hoggamers.rankforge.presentation.screen.MatchCreationViewModel
import com.hoggamers.rankforge.presentation.screen.MatchPlacementViewModel
import com.hoggamers.rankforge.presentation.screen.MatchKillViewModel
import com.hoggamers.rankforge.presentation.screen.MatchReviewViewModel
import com.hoggamers.rankforge.presentation.screen.MatchResultScreenshotCropViewModel
import com.hoggamers.rankforge.presentation.screen.MatchResultScreenshotUploadCheckpointAction
import com.hoggamers.rankforge.presentation.screen.MatchResultScreenshotUploadCheckpointResult
import com.hoggamers.rankforge.presentation.screen.MatchOcrReviewTestTags
import com.hoggamers.rankforge.presentation.screen.MatchOcrReviewViewModel
import com.hoggamers.rankforge.presentation.screen.MatchCorrectionViewModel
import com.hoggamers.rankforge.presentation.screen.MATCH_LOBBY_SCREENSHOT_CROP_CANCEL_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_LOBBY_SCREENSHOT_CROP_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_RESULT_SCREENSHOT_CROP_CANCEL_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_RESULT_SCREENSHOT_CROP_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_RESULT_SCREENSHOT_CROP_EDITOR_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.OCR_VISUAL_CROP_CONFIRM_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_RESULT_SCREENSHOTS_SECTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_RESULT_SCREENSHOT_2_CROP_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_PLACEMENT_BACK_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_KILLS_ACTION_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_KILL_FIELD_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_KILL_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_KILL_SAVE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_ACTION_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_KILLS_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_DETAILS_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_OCR_PREFLIGHT_CALCULATE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_FINALIZE_CONFIRM_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_CORRECTION_HISTORY_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_ROW_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_KILLS_FIELD_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_SUBMIT_CONFIRM_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_DISCARD_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CORRECTION_DISCARD_CONFIRM_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_CREATION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CREATION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_CREATE_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_MAP_FIELD_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_NUMBER_FIELD_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.CREATE_MATCH_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_ITEM_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.OPEN_STANDINGS_ACTION_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_STANDINGS_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TournamentStandingsViewModel
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import com.hoggamers.rankforge.domain.tournament.SaveMatchPlacementsUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchKillsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.SaveMatchDraftValueUseCase
import com.hoggamers.rankforge.domain.tournament.ClearDraftMatchUseCase
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.FinalizeOcrCorrectionMatchUseCase
import com.hoggamers.rankforge.domain.tournament.SubmitMatchCorrectionUseCase
import com.hoggamers.rankforge.domain.tournament.ClearMatchCorrectionDraftUseCase
import com.hoggamers.rankforge.domain.tournament.MatchKill
import com.hoggamers.rankforge.domain.tournament.MatchPlacement
import com.hoggamers.rankforge.domain.tournament.CumulativeTournamentStandingsEngine
import com.hoggamers.rankforge.domain.tournament.TieBreakRules
import com.hoggamers.rankforge.domain.ocr.layout.OcrCropValidationProfiles
import com.hoggamers.rankforge.domain.ocr.layout.OcrNormalizedCropRect
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchLobbyScreenshotIdentity
import com.hoggamers.rankforge.domain.ocr.screenshot.OcrScreenshotKind
import com.hoggamers.rankforge.domain.ocr.review.ProcessRosterOcrUseCase
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparer
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationResult
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrPanelPreparationFailure
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrSourceProvider
import com.hoggamers.rankforge.domain.ocr.review.RosterOcrSourceProviderResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidator
import com.hoggamers.rankforge.domain.ocr.parsing.FixedLayoutRosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.FixedRosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.parsing.DefaultRosterOcrValidator
import com.hoggamers.rankforge.domain.sync.PersistentSyncQueueRepository
import com.hoggamers.rankforge.domain.sync.RecordSyncQueueOutcome
import com.hoggamers.rankforge.domain.sync.SyncQueueEntry
import com.hoggamers.rankforge.domain.sync.SyncQueueOperationType
import com.hoggamers.rankforge.domain.sync.SyncQueueStatus
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.tournament.ReplaceConfirmedTournamentRosterUseCase
import com.hoggamers.rankforge.domain.tournament.ReplaceTournamentRosterInCloudUseCase
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadRepository
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRepository
import com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacementResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationRemoteResult
import com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationFailureCategory
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.presentation.screen.ScreenshotReconciliationScheduler

@RunWith(AndroidJUnit4::class)
class RankForgeNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val OPEN_ROSTER_SCREENSHOT_CROP_TEST_TAG = "open_roster_screenshot_crop"
    }

    @Test
    fun openingAuthWhileAlreadySignedInKeepsAccountDestinationOpen() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))
        val authUiState = AuthUiState(
            isSignedIn = true,
            accountEmail = "user@example.com",
        )

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = authUiState,
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .performClick()
        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(AUTH_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AUTH_LOGOUT_ACTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.tournament_list_title)).assertCountEquals(0)
    }

    @Test
    fun accountHomeReturnsToHomepageWithDrawerClosed() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(
                        isSignedIn = true,
                        accountEmail = "user@example.com",
                    ),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_HOME_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
        .assertIsNotDisplayed()
    }

    @Test
    fun accountBackReturnsToHomepageWithDrawerOpen() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(
                        isSignedIn = true,
                        accountEmail = "user@example.com",
                    ),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(AUTH_ACCOUNT_BACK_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG).assertIsDisplayed()
    }

    @Test
fun accountSystemBackReturnsToHomepageWithDrawerOpen() {
    val repository = InMemoryTournamentRepository()
    val listViewModel = TournamentListViewModel(
        ObserveTournamentsUseCase(repository),
    )

    composeTestRule.setContent {
        RankForgeTheme {
            RankForgeNavHost(
                authUiState = AuthUiState(
                    isSignedIn = true,
                    accountEmail = "user@example.com",
                ),
                listViewModel = listViewModel,
            )
        }
    }

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .performClick()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule.runOnIdle {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
        .assertIsDisplayed()
}

   @Test
fun logoutFromAccountStaysOnAuthAndShowsSignedOutLogin() {
    val repository = InMemoryTournamentRepository()
    val listViewModel = TournamentListViewModel(
        ObserveTournamentsUseCase(repository),
    )
    var authUiState by mutableStateOf(
        AuthUiState(
            isSignedIn = true,
            accountEmail = "user@example.com",
        ),
    )

    composeTestRule.setContent {
        RankForgeTheme {
            RankForgeNavHost(
                authUiState = authUiState,
                onAuthLogout = {
                    authUiState = authUiState.copy(
                        isSignedIn = false,
                        accountEmail = null,
                    )
                },
                listViewModel = listViewModel,
            )
        }
    }

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .performClick()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_ACCOUNT_ITEM_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(AUTH_LOGOUT_ACTION_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(AUTH_SCREEN_TEST_TAG)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithText(context.getString(R.string.auth_login_heading))
        .assertIsDisplayed()

    composeTestRule
        .onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG)
        .assertCountEquals(0)

    composeTestRule
        .onAllNodesWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .assertCountEquals(0)
}

   @Test
    fun allTournamentsMenuItemOpensDedicatedDestination() {
    val repository = InMemoryTournamentRepository()
    val listViewModel = TournamentListViewModel(
        ObserveTournamentsUseCase(repository),
    )

    composeTestRule.setContent {
        RankForgeTheme {
            RankForgeNavHost(
                authUiState = AuthUiState(isSignedIn = true),
                listViewModel = listViewModel,
            )
        }
    }

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .performClick()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ALL_TOURNAMENTS_SCREEN_TEST_TAG)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithText("All Tournaments")
        .assertIsDisplayed()

        composeTestRule
            .onAllNodesWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun allTournamentsUsesPreloadedSharedTournamentListState() {
        val repository = InMemoryTournamentRepository()
        val tournaments = (1..4).map { index ->
            confirmedTournament().copy(
                id = "test$index-id",
                name = "test $index",
            )
        }
        runBlocking {
            tournaments.forEach { repository.create(it) }
        }
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(isSignedIn = true),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            listViewModel.uiState.value.tournaments.size == tournaments.size
        }
        composeTestRule.waitForIdle()
        listOf("test2-id", "test3-id", "test4-id").forEach { tournamentId ->
            composeTestRule
                .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournamentId)
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeTestRule
            .onAllNodesWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "test1-id")
            .assertCountEquals(0)

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule
            .onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodesWithTag(TOURNAMENT_LIST_EMPTY_TEST_TAG)
            .assertCountEquals(0)
        tournaments.forEach { tournament ->
            composeTestRule
                .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournament.id)
                .performScrollTo()
                .assertIsDisplayed()
        }

        composeTestRule.onNodeWithTag(ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        listOf("test2-id", "test3-id", "test4-id").forEach { tournamentId ->
            composeTestRule
                .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournamentId)
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeTestRule
            .onAllNodesWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "test1-id")
            .assertCountEquals(0)
    }

    @Test
    fun allTournamentsHomeReturnsToHomepageWithDrawerClosed() {
    val repository = InMemoryTournamentRepository()
    val listViewModel = TournamentListViewModel(
        ObserveTournamentsUseCase(repository),
    )

    composeTestRule.setContent {
        RankForgeTheme {
            RankForgeNavHost(
                authUiState = AuthUiState(isSignedIn = true),
                listViewModel = listViewModel,
            )
        }
    }

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG)
        .performClick()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(ALL_TOURNAMENTS_HOME_ACTION_TEST_TAG)
        .performClick()

    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG)
        .assertIsDisplayed()

    composeTestRule
        .onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG)
        .assertIsNotDisplayed()
}

    @Test
    fun allTournamentsBackReturnsToHomepageWithDrawerOpen() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(isSignedIn = true),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ALL_TOURNAMENTS_BACK_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun allTournamentsSystemBackReturnsToHomepageWithDrawerOpen() {
        val repository = InMemoryTournamentRepository()
        val listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository))

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    authUiState = AuthUiState(isSignedIn = true),
                    listViewModel = listViewModel,
                )
            }
        }

        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_MENU_BUTTON_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_ALL_TOURNAMENTS_ITEM_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LOGGED_IN_HOME_DRAWER_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun navigationMovesForwardAndBackThroughVisibleDestinations() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                )
            }
        }

        val openAction = context.getString(R.string.open_tournament_creation)

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)

        composeTestRule.onNodeWithText(openAction).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()

        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun dirtyBackShowsConfirmationBeforeReturningToList() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.open_tournament_creation)).performClick()
        composeTestRule.runOnIdle { viewModels.creationViewModel.onTournamentNameChanged("Draft") }
        composeTestRule.waitForIdle()
        pressBackOnMainThread()

        composeTestRule.onNodeWithText(context.getString(R.string.keep_editing_action)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.discard_changes_action)).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun rosterScreenshotCropDestinationOpensAndBackReturnsToRosterReview() {
        val viewModels = createNavigationViewModels()
        createTournamentFromViewModel(viewModels.creationViewModel)
        val rosterOcrViewModel = createRosterOcrReviewViewModel(viewModels.repository)
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            RankForgeTheme {
                navController = rememberNavController()
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterOcrReviewViewModelFactory = { rosterOcrViewModel },
                    rosterScreenshotIntakeContent = { _, onOpenCropEditor ->
                        Button(
                            onClick = { onOpenCropEditor(1) },
                            modifier = Modifier.testTag(OPEN_ROSTER_SCREENSHOT_CROP_TEST_TAG),
                        ) {
                            Text("Open crop")
                        }
                    },
                    rosterScreenshotCropViewModelFactory = { createCropNavigationViewModel() },
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModels.listViewModel.uiState.value.tournaments.size == 1
        }
        val tournamentId = viewModels.listViewModel.uiState.value.tournaments.single().id

        composeTestRule.runOnIdle {
            navController.navigate(RosterReviewDestination(tournamentId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule.onNodeWithTag(OPEN_ROSTER_SCREENSHOT_CROP_TEST_TAG).performClick()

        composeTestRule.onNodeWithTag(ROSTER_SCREENSHOT_CROP_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertCountEquals(0)

        pressBackOnMainThread()

        composeTestRule.onNodeWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun successfulCreationEntersSetupAndBackReturnsToCreatedTournamentDetails() {
        val viewModels = createNavigationViewModels()
        var loadedTeamEntryTournamentId: String? = null
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = { tournamentId ->
                        loadedTeamEntryTournamentId = tournamentId
                        viewModels.teamEntryViewModel(tournamentId)
                    },
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.open_tournament_creation)).performClick()
        viewModels.creationViewModel.onTournamentNameChanged("Summer Cup")
        viewModels.creationViewModel.onTournamentDateChanged(LocalDate.of(2026, 7, 24))
        viewModels.creationViewModel.onOrganizerNameChanged("Alex")
        viewModels.creationViewModel.onOrganizerContactNumberChanged("123")
        viewModels.creationViewModel.submit()
        composeTestRule.waitForIdle()
        val createdTournamentId = viewModels.listViewModel.uiState.value.tournaments.single().id

        composeTestRule.onAllNodesWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(TEAM_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()
        assertEquals(createdTournamentId, loadedTeamEntryTournamentId)

        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date: 24 Jul 2026").assertIsDisplayed()
    }

    @Test
    fun tappingCreatedTournamentOpensDetailsAndBackReturnsToList() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                )
            }
        }

        createTournamentFromViewModel(viewModels.creationViewModel)
        composeTestRule.waitForIdle()
        val createdTournamentId = viewModels.listViewModel.uiState.value.tournaments.single().id

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + createdTournamentId).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Summer Cup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Date: 24 Jul 2026").assertIsDisplayed()

        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun detailsEntryActionOpensTeamEntryAndBackReturnsToDetails() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                )
            }
        }

        createTournamentFromViewModel(viewModels.creationViewModel)
        composeTestRule.waitForIdle()
        val createdTournamentId = viewModels.listViewModel.uiState.value.tournaments.single().id

        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + createdTournamentId).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.enter_teams_action)).performClick()
        composeTestRule.onNodeWithTag(TEAM_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(TEAM_ENTRY_ROSTER_BUTTON_TEST_TAG_PREFIX + 1)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ROSTER_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()

        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(TEAM_ENTRY_SCREEN_TEST_TAG).assertIsDisplayed()
        pressBackOnMainThread()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun rosterConfirmationReturnsToSameTournamentDetails() {
        val viewModels = createNavigationViewModels()
        val tournamentId = "setup-id"
        runBlocking {
            createValidRoster(viewModels.repository, tournamentId)
        }
        val rosterOcrViewModel = createRosterOcrReviewViewModel(viewModels.repository)
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterOcrReviewViewModelFactory = { rosterOcrViewModel },
                    rosterScreenshotIntakeContent = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournamentId).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.enter_teams_action)).performClick()
        composeTestRule
            .onNodeWithText(context.getString(R.string.overview_team_details_action))
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(ROSTER_REVIEW_CONFIRM_BUTTON_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithTag(ROSTER_REVIEW_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Setup Cup").assertIsDisplayed()
        assertEquals(
            TournamentStatus.CONFIRMED,
            runBlocking { viewModels.repository.observeById(tournamentId).first()?.status },
        )
    }

    @Test
    fun unknownDetailsIdShowsNotFoundStateWithoutCrashing() {
        val viewModels = createNavigationViewModels()
        composeTestRule.setContent {
            val navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                )
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate(TournamentDetailsDestination("missing"))
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_NOT_FOUND_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.tournament_not_found_title)).assertIsDisplayed()
    }

    @Test
    fun confirmedTournamentDetailsCalculatePointsNavigatesToMatchReview() {
        val viewModels = createNavigationViewModels()
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            viewModels.repository.saveTeamNames(
                "confirmed-id",
                (1..12).associateWith { slotNumber -> "Team $slotNumber" },
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(CREATE_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithText("Review Match 1").assertIsDisplayed()
    }

    @Test
    fun existingMatchRowOpensThatPersistedMatchWithoutCreatingAnother() {
        val viewModels = createNavigationViewModels()
        val matchId = runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
            (CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            ) as CreateMatchResult.Created).match.id
        }

        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule
            .onNodeWithTag(MATCH_ITEM_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Review Match 1").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)
        assertEquals(1, runBlocking { viewModels.repository.observeMatchesByTournamentId("confirmed-id").first().size })
        assertEquals(
            matchId,
            runBlocking { viewModels.repository.observeMatchesByTournamentId("confirmed-id").first().single().id },
        )

    }

    @Test
    fun manualMatchFlowMovesFromCreationToReviewAndSameTournamentDetails() {
        val viewModels = createNavigationViewModels()
        var activeMatchCreationViewModel: MatchCreationViewModel? = null
        var cachedMatchCreationViewModel: MatchCreationViewModel? = null
        lateinit var navController: NavHostController
        runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
        }
        composeTestRule.setContent {
            RankForgeTheme {
                navController = rememberNavController()
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                    matchCreationViewModelFactory = { tournamentId ->
                        val viewModel = cachedMatchCreationViewModel
                            ?: viewModels.matchCreationViewModel(tournamentId).also {
                                cachedMatchCreationViewModel = it
                            }
                        activeMatchCreationViewModel = viewModel
                        viewModel
                    },
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.runOnIdle {
            navController.navigate(MatchCreationDestination("confirmed-id"))
        }
        composeTestRule.onNodeWithTag(MATCH_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_NUMBER_FIELD_TEST_TAG).performTextInput("1")
        composeTestRule.runOnIdle {
            activeMatchCreationViewModel?.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        }
        composeTestRule.onNodeWithTag(MATCH_MAP_FIELD_TEST_TAG).performTextInput("Bermuda")
        composeTestRule.onNodeWithTag(MATCH_CREATE_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_PLACEMENT_SCREEN_TEST_TAG).assertIsDisplayed()
        val matchId = runBlocking {
            viewModels.repository.observeMatchesByTournamentId("confirmed-id").first().single().id
        }
        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput(slotNumber.toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_KILL_SCREEN_TEST_TAG).assertIsDisplayed()
        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_KILL_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput((slotNumber - 1).toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_KILL_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        assertEquals(
            (1..12).map { MatchPlacement(it, it) },
            runBlocking { viewModels.repository.observeMatchById(matchId).first()?.placements },
        )
        assertEquals(
            (1..12).map { MatchKill(it, it - 1) },
            runBlocking { viewModels.repository.observeMatchById(matchId).first()?.kills },
        )
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(CREATE_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun manualMatchFlowFinalizesAndOpensUpdatedSameTournamentStandings() {
        val viewModels = createNavigationViewModels()
        var activeMatchCreationViewModel: MatchCreationViewModel? = null
        var cachedMatchCreationViewModel: MatchCreationViewModel? = null
        var activeMatchReviewViewModel: MatchReviewViewModel? = null
        var cachedMatchReviewViewModel: MatchReviewViewModel? = null
        lateinit var navController: NavHostController
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            viewModels.repository.saveTeamNames(
                "confirmed-id",
                mapOf(1 to "Team 1"),
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                navController = rememberNavController()
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = { tournamentId ->
                        val viewModel = cachedMatchCreationViewModel
                            ?: viewModels.matchCreationViewModel(tournamentId).also {
                                cachedMatchCreationViewModel = it
                            }
                        activeMatchCreationViewModel = viewModel
                        viewModel
                    },
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = { tournamentId, matchId ->
                        val viewModel = cachedMatchReviewViewModel
                            ?: viewModels.matchReviewViewModel(tournamentId, matchId).also {
                                cachedMatchReviewViewModel = it
                            }
                        activeMatchReviewViewModel = viewModel
                        viewModel
                    },
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    showLegacyManualReviewContent = true,
                    standingsViewModelFactory = viewModels.standingsViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.runOnIdle {
            navController.navigate(MatchCreationDestination("confirmed-id"))
        }
        composeTestRule.onNodeWithTag(MATCH_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_NUMBER_FIELD_TEST_TAG).performTextInput("1")
        composeTestRule.runOnIdle {
            activeMatchCreationViewModel?.onMatchDateChanged(LocalDate.of(2026, 7, 24))
        }
        composeTestRule.onNodeWithTag(MATCH_MAP_FIELD_TEST_TAG).performTextInput("Bermuda")
        composeTestRule.onNodeWithTag(MATCH_CREATE_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput(slotNumber.toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_KILL_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput(if (slotNumber == 2) "10" else (slotNumber - 1).toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_KILL_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_FINALIZE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZE_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()

        val match = runBlocking {
            viewModels.repository.observeMatchesByTournamentId("confirmed-id").first().single()
        }
        assertEquals("confirmed-id", match.tournamentId)
        assertEquals(com.hoggamers.rankforge.domain.tournament.MatchStatus.FINALIZED, match.status)
        assertEquals("confirmed-id", activeMatchReviewViewModel?.uiState?.value?.tournamentId)
        assertEquals(match.id, activeMatchReviewViewModel?.uiState?.value?.matchId)

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Match 1 - Completed").performScrollTo().assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(OPEN_STANDINGS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX + "2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total points: 19").assertIsDisplayed()
    }

    @Test
    fun finalizedMatchCorrectionBackReturnsToSameMatchReview() {
        val viewModels = createNavigationViewModels()
        val tournamentId = "confirmed-id"
        val matchId = createFinalizedMatch(viewModels)
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            RankForgeTheme {
                navController = rememberNavController()
                RankForgeNavHost(
                    navController = navController,
                    authUiState = AuthUiState(isSignedIn = true),
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                    showLegacyManualReviewContent = true,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournamentId)
            .performClick()
        composeTestRule
            .onNodeWithTag(MATCH_ITEM_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(
                MatchReviewDestination(tournamentId, matchId),
                navController.currentBackStackEntry?.toRoute<MatchReviewDestination>(),
            )
        }

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Start correction").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(
                MatchCorrectionDestination(tournamentId, matchId),
                navController.currentBackStackEntry?.toRoute<MatchCorrectionDestination>(),
            )
        }

        pressBackOnMainThread()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.runOnIdle {
            assertEquals(
                MatchReviewDestination(tournamentId, matchId),
                navController.currentBackStackEntry?.toRoute<MatchReviewDestination>(),
            )
        }
    }

    @Test
    fun tournamentStandingsBackReturnsToSameTournamentDetails() {
        val viewModels = createNavigationViewModels()
        val tournamentId = "confirmed-id"
        createFinalizedMatch(viewModels)
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            RankForgeTheme {
                navController = rememberNavController()
                RankForgeNavHost(
                    navController = navController,
                    authUiState = AuthUiState(isSignedIn = true),
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    standingsViewModelFactory = viewModels.standingsViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournamentId)
            .performClick()
        composeTestRule
            .onNodeWithTag(OPEN_STANDINGS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(
                TournamentStandingsDestination(tournamentId),
                navController.currentBackStackEntry?.toRoute<TournamentStandingsDestination>(),
            )
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.back_to_tournament_details_action))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Confirmed Cup").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_LIST_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule.runOnIdle {
            assertEquals(
                TournamentDetailsDestination(tournamentId),
                navController.currentBackStackEntry?.toRoute<TournamentDetailsDestination>(),
            )
        }
    }

    @Test
    fun draftMatchDetailsNavigatesToPlacementEntry() {
        val viewModels = createNavigationViewModels()
        val matchId = runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
            (
                CreateMatchUseCase(viewModels.repository)(
                    CreateMatchInput(
                        tournamentId = "confirmed-id",
                        matchNumber = "1",
                        date = LocalDate.of(2026, 7, 24),
                        mapName = "Bermuda",
                    ),
                ) as CreateMatchResult.Created
            ).match.id
        }
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.runOnIdle {
            navController.navigate(MatchPlacementDestination("confirmed-id", matchId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_PLACEMENT_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_PLACEMENT_FIELD_TEST_TAG_PREFIX + "1")
            .assertIsDisplayed()
        assert(matchId.isNotBlank())
    }

    @Test
    fun draftMatchDetailsNavigatesToKillEntry() {
        val viewModels = createNavigationViewModels()
        val matchId = runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
            (CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            ) as CreateMatchResult.Created).match.id
        }
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    showLegacyManualReviewContent = true,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.runOnIdle {
            navController.navigate(MatchKillDestination("confirmed-id", matchId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_KILL_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_KILL_FIELD_TEST_TAG_PREFIX + "1")
            .assertIsDisplayed()
    }

    @Test
    fun draftMatchReviewNavigatesToPlacementKillsAndDetails() {
        val viewModels = createNavigationViewModels()
        val matchId = runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
            (CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            ) as CreateMatchResult.Created).match.id
        }
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.runOnIdle {
            navController.navigate(MatchReviewDestination("confirmed-id", matchId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_LOBBY_SCREENSHOTS_SECTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_SECTION_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun reviewMatchLobbyCropKeepsExactMatchContextAndReturnsToReview() {
        val viewModels = createNavigationViewModels()
        val tournamentId = "confirmed-id"
        val matchId = runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
            val id = "lobby-crop-match"
            viewModels.repository.createDraftMatch(
                com.hoggamers.rankforge.domain.tournament.Match(
                    id = id,
                    tournamentId = tournamentId,
                    matchNumber = 1,
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                    status = com.hoggamers.rankforge.domain.tournament.MatchStatus.DRAFT,
                ),
            )
            id
        }
        val cropViewModel = createLobbyCropNavigationViewModel(viewModels.repository)
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    matchLobbyScreenshotCropViewModelFactory = { _, _, _ -> cropViewModel },
                    matchLobbyScreenshotIntakeContent = { _, _, onOpenCrop, _ ->
                        Button(
                            onClick = { onOpenCrop(2) },
                            modifier = Modifier.testTag("open_lobby_crop_2"),
                        ) { Text("Open Lobby Screenshot 2") }
                    },
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            navController.navigate(MatchReviewDestination(tournamentId, matchId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_lobby_crop_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_CROP_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            val destination = navController.currentBackStackEntry?.toRoute<MatchLobbyScreenshotCropDestination>()
            assertEquals(tournamentId, destination?.tournamentId)
            assertEquals(matchId, destination?.matchId)
            assertEquals(2, destination?.lobbyScreenshotIndex)
        }
        assertEquals(1, runBlocking { viewModels.repository.observeMatchesByTournamentId(tournamentId).first().size })
        composeTestRule.onNodeWithTag(MATCH_LOBBY_SCREENSHOT_CROP_CANCEL_TEST_TAG).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(
                MatchReviewDestination(tournamentId, matchId),
                navController.currentBackStackEntry?.toRoute<MatchReviewDestination>(),
            )
        }
    }

    @Test
    fun reviewMatchUpperResultCropCancelReturnsToSameReview() {
        runResultCropNavigationCase(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            confirm = false,
        )
    }

    @Test
    fun reviewMatchLowerResultCropCancelReturnsToSameReview() {
        runResultCropNavigationCase(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            confirm = false,
        )
    }

    @Test
    fun reviewMatchUpperResultCropConfirmPersistsOnlyUpperAndReturnsToSameReview() {
        runResultCropNavigationCase(
            role = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            confirm = true,
        )
    }

    @Test
    fun reviewMatchLowerResultCropConfirmPersistsOnlyLowerAndReturnsToSameReview() {
        runResultCropNavigationCase(
            role = MatchResultScreenshotRole.MATCH_RESULT_LOWER,
            confirm = true,
        )
    }

    private fun runResultCropNavigationCase(
        role: MatchResultScreenshotRole,
        confirm: Boolean,
    ) {
        val fixture = createResultCropNavigationFixture(role)
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = fixture.viewModels.creationViewModel,
                    listViewModel = fixture.viewModels.listViewModel,
                    detailsViewModelFactory = fixture.viewModels.detailsViewModel,
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                    matchReviewViewModelFactory = { _, _ -> fixture.matchReviewViewModel },
                    matchOcrReviewViewModelFactory = fixture.viewModels.matchOcrReviewViewModel,
                    matchResultScreenshotCropViewModelFactory = { fixture.cropViewModel },
                    showLegacyManualReviewContent = false,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            navController.navigate(MatchReviewDestination(fixture.tournamentId, fixture.matchId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        if (role == MatchResultScreenshotRole.MATCH_RESULT_LOWER) {
            composeTestRule
                .onNodeWithTag(MATCH_REVIEW_RESULT_SCREENSHOTS_PAGER_TEST_TAG)
                .performScrollTo()
                .performTouchInput { swipeLeft() }
            composeTestRule.waitForIdle()
        }

        val cropActionTag = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MATCH_REVIEW_RESULT_SCREENSHOT_1_CROP_TEST_TAG
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> MATCH_REVIEW_RESULT_SCREENSHOT_2_CROP_TEST_TAG
        }
        composeTestRule.onNodeWithTag(cropActionTag).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            val destination = navController.currentBackStackEntry
                ?.toRoute<MatchResultScreenshotCropDestination>()
            assertEquals(fixture.tournamentId, destination?.tournamentId)
            assertEquals(fixture.matchId, destination?.matchId)
            assertEquals(role.name, destination?.screenshotRole)
        }

        if (confirm) {
            composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_EDITOR_TEST_TAG).assertIsDisplayed()
            composeTestRule.onNodeWithTag(OCR_VISUAL_CROP_CONFIRM_ACTION_TEST_TAG).performClick()
            composeTestRule.waitForIdle()
            assertNotNull(runBlocking { fixture.assetRepository.getByIdentity(fixture.identity(role))?.cropProfileId })
        } else {
            composeTestRule.onNodeWithTag(MATCH_RESULT_SCREENSHOT_CROP_CANCEL_TEST_TAG).performClick()
            composeTestRule.waitForIdle()
            assertNull(runBlocking { fixture.assetRepository.getByIdentity(fixture.identity(role))?.cropProfileId })
        }

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertEquals(
                MatchReviewDestination(fixture.tournamentId, fixture.matchId),
                navController.currentBackStackEntry?.toRoute<MatchReviewDestination>(),
            )
        }

        val otherRole = when (role) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MatchResultScreenshotRole.MATCH_RESULT_LOWER
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> MatchResultScreenshotRole.MATCH_RESULT_UPPER
        }
        assertEquals(
            fixture.initialAssets.getValue(otherRole),
            runBlocking { fixture.assetRepository.getByIdentity(fixture.identity(otherRole)) },
        )
        if (confirm) {
            assertEquals(
                OcrCropValidationProfiles.MatchResult.id,
                runBlocking { fixture.assetRepository.getByIdentity(fixture.identity(role))?.cropProfileId },
            )
        }
    }

    @Test
    fun linkedDraftScreenshotOpensOcrReviewAndReturnsToSameReviewAndDetails() {
        val crop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9)
        val fixture = createResultCropNavigationFixture(
            targetRole = MatchResultScreenshotRole.MATCH_RESULT_UPPER,
            targetCrop = crop,
        )
        val viewModels = fixture.viewModels
        val matchId = fixture.matchId
        var activeMatchReviewViewModel: MatchReviewViewModel? = null
        val cachedMatchReviewViewModels = mutableMapOf<String, MatchReviewViewModel>()
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = { tournamentId, matchId ->
                        cachedMatchReviewViewModels.getOrPut("$tournamentId:$matchId") {
                            fixture.matchReviewViewModel
                        }.also {
                            activeMatchReviewViewModel = it
                        }
                    },
                    showLegacyManualReviewContent = false,
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModels.listViewModel.uiState.value.tournaments.any { it.id == "confirmed-id" }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.runOnIdle {
            navController.navigate(MatchReviewDestination("confirmed-id", matchId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()

        composeTestRule.runOnIdle {
            activeMatchReviewViewModel?.onPhotoPickerResult("content://picker/ocr")
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle {
            activeMatchReviewViewModel?.linkScreenshot()
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            activeMatchReviewViewModel?.uiState?.value?.canOpenOcrReview == true
        }
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_OCR_REVIEW_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_OCR_PREFLIGHT_CALCULATE_ACTION_TEST_TAG)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_RESULT_OCR_DETAILS_SECTION_TEST_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithTag(MatchOcrReviewTestTags.SCREEN)
            .assertCountEquals(0)
        composeTestRule.runOnIdle {
            assertEquals(
                MatchReviewDestination("confirmed-id", matchId),
                navController.currentBackStackEntry?.toRoute<MatchReviewDestination>(),
            )
        }

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(CREATE_MATCH_ACTION_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun finalizedMatchReviewDoesNotOfferPlacementOrKillEditing() {
        val viewModels = createNavigationViewModels()
        val matchId = runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
            val match = (
                CreateMatchUseCase(viewModels.repository)(
                    CreateMatchInput(
                        tournamentId = "confirmed-id",
                        matchNumber = "1",
                        date = LocalDate.of(2026, 7, 24),
                        mapName = "Bermuda",
                    ),
                ) as CreateMatchResult.Created
            ).match
            viewModels.repository.finalizeDraftMatch(
                matchId = match.id,
                placements = (1..12).map { MatchPlacement(it, it) },
                kills = (1..12).map { MatchKill(it, it - 1) },
            )
            match.id
        }
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    showLegacyManualReviewContent = true,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.runOnIdle {
            navController.navigate(MatchReviewDestination("confirmed-id", matchId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("This opens an editable correction copy. The finalized result stays unchanged until you submit it.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Start correction").performClick()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_DISCARD_ACTION_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_DISCARD_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_DETAILS_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Match 1 - Completed").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_PLACEMENT_ACTION_TEST_TAG_PREFIX + "1").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_KILLS_ACTION_TEST_TAG_PREFIX + "1").assertCountEquals(0)
    }

    @Test
    fun finalizedMatchCorrectionReturnsToCorrectedReadOnlyReview() {
        val viewModels = createNavigationViewModels()
        val matchId = runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
            val match = (
                CreateMatchUseCase(viewModels.repository)(
                    CreateMatchInput(
                        tournamentId = "confirmed-id",
                        matchNumber = "1",
                        date = LocalDate.of(2026, 7, 24),
                        mapName = "Bermuda",
                    ),
                ) as CreateMatchResult.Created
            ).match
            viewModels.repository.finalizeDraftMatch(
                matchId = match.id,
                placements = (1..12).map { MatchPlacement(it, it) },
                kills = (1..12).map { MatchKill(it, it - 1) },
            )
            match.id
        }
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    showLegacyManualReviewContent = true,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.runOnIdle {
            navController.navigate(MatchReviewDestination("confirmed-id", matchId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(MATCH_REVIEW_CORRECTION_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("Start correction").performClick()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SCREEN_TEST_TAG).assertIsDisplayed()

        val correctionKillsField = composeTestRule
            .onNodeWithTag(MATCH_CORRECTION_KILLS_FIELD_TEST_TAG_PREFIX + "1")
            .performScrollTo()
        correctionKillsField.performTextClearance()
        correctionKillsField.performTextInput("5")
        composeTestRule
            .onNodeWithTag(MATCH_CORRECTION_SUBMIT_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText("This replaces the current finalized result and preserves the previous result in correction history.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_CORRECTION_SUBMIT_CONFIRM_ACTION_TEST_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_FINALIZED_STATUS_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(
            hasText("Kills: 5", substring = false) and
                hasAnyAncestor(hasTestTag(MATCH_REVIEW_ROW_TEST_TAG_PREFIX + "1")),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MATCH_REVIEW_CORRECTION_HISTORY_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Corrected result — Slot 1: placement 1, kills 5").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_PLACEMENTS_ACTION_TEST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(MATCH_REVIEW_KILLS_ACTION_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun draftMatchDetailsHidesLegacyValidationDetails() {
        val viewModels = createNavigationViewModels()
        runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
            CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            )
        }
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.onNodeWithText("Match 1 - In Progress").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(MATCH_VALIDATION_ISSUES_TEST_TAG_PREFIX + "1").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Map: Bermuda").assertCountEquals(0)
    }

    @Test
    fun draftMatchAllowsEnteringAndDisplayingAllKills() {
        val viewModels = createNavigationViewModels()
        val matchId = runBlocking {
            createConfirmedTournamentWithOneActiveTeam(viewModels.repository)
            (CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = "confirmed-id",
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            ) as CreateMatchResult.Created).match.id
        }
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            RankForgeTheme {
                RankForgeNavHost(
                    navController = navController,
                    creationViewModel = viewModels.creationViewModel,
                    listViewModel = viewModels.listViewModel,
                    detailsViewModelFactory = viewModels.detailsViewModel,
                    teamEntryViewModelFactory = viewModels.teamEntryViewModel,
                    rosterEntryViewModelFactory = viewModels.rosterEntryViewModel,
                    rosterReviewViewModelFactory = viewModels.rosterReviewViewModel,
                    rosterScreenshotIntakeContent = { _, _ -> },
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                    matchCreationViewModelFactory = viewModels.matchCreationViewModel,
                    matchPlacementViewModelFactory = viewModels.matchPlacementViewModel,
                    matchKillViewModelFactory = viewModels.matchKillViewModel,
                    matchReviewViewModelFactory = viewModels.matchReviewViewModel,
                    matchOcrReviewViewModelFactory = viewModels.matchOcrReviewViewModel,
                    matchCorrectionViewModelFactory = viewModels.matchCorrectionViewModel,
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + "confirmed-id").performClick()
        composeTestRule.runOnIdle {
            navController.navigate(MatchKillDestination("confirmed-id", matchId))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MATCH_KILL_SCREEN_TEST_TAG).assertIsDisplayed()

        (1..12).forEach { slotNumber ->
            composeTestRule
                .onNodeWithTag(MATCH_KILL_FIELD_TEST_TAG_PREFIX + slotNumber)
                .performScrollTo()
                .performTextInput((slotNumber - 1).toString())
        }
        composeTestRule
            .onNodeWithTag(MATCH_KILL_SAVE_ACTION_TEST_TAG)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    private class FakeGoogleSheetsStandingsExportRemoteDataSource :
        GoogleSheetsStandingsExportRemoteDataSource {
        override suspend fun export(
            tournamentId: String,
            rows: List<com.hoggamers.rankforge.domain.export.TournamentStandingsExportRow>,
        ): GoogleSheetsStandingsExportExecutionResult =
            GoogleSheetsStandingsExportExecutionResult.Success(
                exportedMatchCount = rows.firstOrNull()?.exportedMatchCount ?: 0,
                rowsWritten = rows.size,
            )
    }

    private fun pressBackOnMainThread() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun createCropNavigationViewModel(): RosterScreenshotIntakeViewModel =
        RosterScreenshotIntakeViewModel(
            imageCandidateValidator = ImageCandidateValidator(
                ImageCandidateMetadataReader { ImageCandidateReadResult.Unreadable },
            ),
            fingerprintGenerator = ImageSourceFingerprintGenerator(
                streamOpener = ImageSourceStreamOpener { null },
            ),
            authRepository = object : AuthRepository {
                override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedOut)
                override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
                override suspend fun signUp(email: String, password: String): AuthOperationResult = error("unused")
                override suspend fun login(email: String, password: String): AuthOperationResult = error("unused")
                override suspend fun logout(): AuthOperationResult = error("unused")
            },
        )

    private fun createRosterOcrReviewViewModel(
        repository: InMemoryTournamentRepository,
    ): RosterOcrReviewViewModel {
        val processRosterOcr = ProcessRosterOcrUseCase(
            sourceProvider = object : RosterOcrSourceProvider {
                override suspend fun load(tournamentId: String): RosterOcrSourceProviderResult =
                    RosterOcrSourceProviderResult.IncompleteScreenshotSet

                override suspend fun load(
                    tournamentId: String,
                    expectedOwnerUserId: String,
                ): RosterOcrSourceProviderResult = load(tournamentId)
            },
            panelPreparer = object : RosterOcrPanelPreparer {
                override suspend fun prepare(source: com.hoggamers.rankforge.domain.ocr.review.RosterOcrScreenshotSource): RosterOcrPanelPreparationResult =
                    RosterOcrPanelPreparationResult.Failed(
                        RosterOcrPanelPreparationFailure.UNKNOWN,
                    )
            },
            extractor = object : RosterRawOcrExtractor {
                override suspend fun extract(input: RosterRawOcrExtractionInput): List<RosterRawOcrExtractionResult> =
                    emptyList()
            },
            parser = FixedLayoutRosterCandidateParser(),
            associator = FixedRosterSlotAssociator(),
            validator = DefaultRosterOcrValidator(),
            authRepository = object : AuthRepository {
                override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedOut)
                override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
                override suspend fun signUp(email: String, password: String): AuthOperationResult = error("unused")
                override suspend fun login(email: String, password: String): AuthOperationResult = error("unused")
                override suspend fun logout(): AuthOperationResult = error("unused")
            },
        )
        val cloudReplacement = ReplaceTournamentRosterInCloudUseCase(
            tournamentRepository = repository,
            authRepository = object : AuthRepository {
                override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedOut)
                override suspend fun restoreSession(): AuthRestorationResult = AuthRestorationResult.NoSavedSession
                override suspend fun signUp(email: String, password: String): AuthOperationResult = error("unused")
                override suspend fun login(email: String, password: String): AuthOperationResult = error("unused")
                override suspend fun logout(): AuthOperationResult = error("unused")
            },
            cloudReplacementRepository = object : TournamentRosterCloudReplacementRepository {
                override suspend fun replace(
                    snapshot: com.hoggamers.rankforge.domain.tournament.TournamentRosterCloudReplacement,
                    ownerId: String,
                ): TournamentRosterCloudReplacementResult = TournamentRosterCloudReplacementResult.NetworkFailure
            },
            cloudUploadRepository = object : TournamentCloudUploadRepository {
                override suspend fun upload(
                    snapshot: com.hoggamers.rankforge.domain.tournament.TournamentCloudUploadSnapshot,
                    ownerId: String,
                ): TournamentCloudUploadResult = TournamentCloudUploadResult.NetworkFailure
            },
            cloudRestorationRepository = object : TournamentCloudRestorationRepository {
                override suspend fun listOwnedTournaments(): TournamentCloudRestorationRemoteResult<List<com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSummary>> =
                    TournamentCloudRestorationRemoteResult.Success(emptyList())

                override suspend fun readOwnedTournament(
                    tournamentId: String,
                ): TournamentCloudRestorationRemoteResult<com.hoggamers.rankforge.domain.tournament.TournamentCloudRestorationSnapshot> =
                    TournamentCloudRestorationRemoteResult.Failure(TournamentCloudRestorationFailureCategory.NOT_FOUND)
            },
            queueRecorder = RecordSyncQueueOutcome(NoOpPersistentSyncQueueRepository),
        )
        return RosterOcrReviewViewModel(
            getTournamentById = GetTournamentByIdUseCase(repository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            processRosterOcr = processRosterOcr,
            replaceConfirmedTournamentRoster = ReplaceConfirmedTournamentRosterUseCase(
                repository = repository,
                rosterValidator = RosterValidator(),
            ),
            replaceTournamentRosterInCloud = cloudReplacement,
        )
    }

    private fun createLobbyCropNavigationViewModel(
        repository: InMemoryTournamentRepository,
    ): MatchLobbyScreenshotCropViewModel = MatchLobbyScreenshotCropViewModel(
        observeMatches = ObserveMatchesUseCase(repository),
        assetRepository = EmptyLobbyScreenshotAssetRepository,
        localImagePreserver = LocalImagePreserver(
            appPrivateRoot = context.filesDir,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = ImageSourceMimeTypeReader { "image/png" },
            ioDispatcher = Dispatchers.Unconfined,
        ),
        clock = Clock.systemUTC(),
        uploadCheckpoint = MatchLobbyScreenshotUploadCheckpointAction {
            MatchLobbyScreenshotUploadCheckpointResult.Skipped
        },
        reconciliationScheduler = ScreenshotReconciliationScheduler(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            testOnly = true,
        ),
        autoCropProposer = MatchLobbyAutoCropProposer {
            com.hoggamers.rankforge.domain.ocr.matchlobby.MatchLobbyAutoCropResult.NoProposal
        },
    )

    private object EmptyLobbyScreenshotAssetRepository : MatchLobbyScreenshotAssetRepository {
        override fun observeByMatchId(matchId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
            flowOf(emptyList())

        override fun observeByIdentity(identity: MatchLobbyScreenshotIdentity): Flow<MatchLobbyScreenshotAssetEntity?> =
            flowOf(null)

        override suspend fun getByIdentity(identity: MatchLobbyScreenshotIdentity): MatchLobbyScreenshotAssetEntity? = null

        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchLobbyScreenshotAssetEntity>> =
            flowOf(emptyList())

        override suspend fun findDuplicateFingerprint(
            identity: MatchLobbyScreenshotIdentity,
            sha256: String,
        ): MatchLobbyScreenshotAssetEntity? = null

        override suspend fun saveOrReplace(asset: MatchLobbyScreenshotAssetEntity): MatchLobbyScreenshotAssetSaveResult =
            MatchLobbyScreenshotAssetSaveResult.Saved

        override suspend fun markLocalMissing(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun markCleanupFailure(identity: MatchLobbyScreenshotIdentity, updatedAt: Long) = Unit

        override suspend fun deleteByIdentity(identity: MatchLobbyScreenshotIdentity) = Unit

        override suspend fun deleteByMatchId(matchId: String) = Unit

        override suspend fun persistConfirmedCrop(
            identity: MatchLobbyScreenshotIdentity,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.MissingAsset

        override suspend fun clearConfirmedCrop(
            identity: MatchLobbyScreenshotIdentity,
            updatedAt: Long,
        ): MatchLobbyScreenshotCropSaveResult = MatchLobbyScreenshotCropSaveResult.MissingAsset
    }

    private object NoOpPersistentSyncQueueRepository : PersistentSyncQueueRepository {
        override fun observeAll(): Flow<List<SyncQueueEntry>> = flowOf(emptyList())

        override suspend fun enqueue(
            operationType: SyncQueueOperationType,
            tournamentId: String?,
            status: SyncQueueStatus,
            failureCategory: String?,
        ): SyncQueueEntry = SyncQueueEntry(
            id = "test",
            operationType = operationType,
            tournamentId = tournamentId,
            createdAtEpochMillis = 0L,
            status = status,
            failureCategory = failureCategory,
            attemptCount = 0,
        )

        override suspend fun completeOldestUnresolved(
            operationType: SyncQueueOperationType,
            tournamentId: String?,
        ) = Unit

        override suspend fun incrementAttemptCount(id: String) = Unit
        override suspend fun updateRetryFailure(id: String, status: SyncQueueStatus, failureCategory: String?) = Unit
        override suspend fun markCompleted(id: String) = Unit
        override suspend fun remove(id: String) = Unit
    }

    private fun createResultCropNavigationFixture(
        targetRole: MatchResultScreenshotRole,
        targetCrop: OcrNormalizedCropRect? = null,
    ): ResultCropNavigationFixture {
        val viewModels = createNavigationViewModels()
        val tournamentId = "confirmed-id"
        val matchId = "result-crop-${targetRole.name.lowercase()}"
        runBlocking {
            viewModels.repository.create(confirmedTournament())
            viewModels.repository.saveTeamNames(
                tournamentId,
                mapOf(1 to "Team 1"),
            )
            viewModels.repository.createDraftMatch(
                com.hoggamers.rankforge.domain.tournament.Match(
                    id = matchId,
                    tournamentId = tournamentId,
                    matchNumber = 1,
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                    status = com.hoggamers.rankforge.domain.tournament.MatchStatus.DRAFT,
                ),
            )
        }

        val localImagePreserver = com.hoggamers.rankforge.presentation.screen.LocalImagePreserver(
            appPrivateRoot = context.filesDir,
            sourceStreamOpener = ImageSourceStreamOpener { null },
            mimeTypeReader = com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader {
                "image/png"
            },
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val otherRole = when (targetRole) {
            MatchResultScreenshotRole.MATCH_RESULT_UPPER -> MatchResultScreenshotRole.MATCH_RESULT_LOWER
            MatchResultScreenshotRole.MATCH_RESULT_LOWER -> MatchResultScreenshotRole.MATCH_RESULT_UPPER
        }
        val crop = OcrNormalizedCropRect(0.1, 0.1, 0.9, 0.9)
        val initialAssets = mapOf(
            targetRole to resultAsset(
                localImagePreserver = localImagePreserver,
                tournamentId = tournamentId,
                matchId = matchId,
                role = targetRole,
                crop = targetCrop,
            ),
            otherRole to resultAsset(
                localImagePreserver = localImagePreserver,
                tournamentId = tournamentId,
                matchId = matchId,
                role = otherRole,
                crop = crop,
            ),
        )
        initialAssets.keys.forEach { role ->
            val file = localImagePreserver.matchResultPreservedFile(
                tournamentId = tournamentId,
                matchId = matchId,
                role = role,
                extension = "png",
            )
            file.parentFile?.mkdirs()
            FileOutputStream(file).use { output ->
                val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
                try {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                } finally {
                    bitmap.recycle()
                }
            }
        }
        val assetRepository = InMemoryMatchResultScreenshotAssetRepository(initialAssets.values.toList())
        val matchReviewViewModel = MatchReviewViewModel(
            getTournamentById = GetTournamentByIdUseCase(viewModels.repository),
            observeMatches = ObserveMatchesUseCase(viewModels.repository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(viewModels.repository),
            observeRoster = ObserveRosterByTournamentUseCase(viewModels.repository),
            observeDraftValues = ObserveMatchDraftValuesUseCase(viewModels.repository),
            validateMatchResult = ValidateMatchResultUseCase(),
            finalizeMatch = FinalizeMatchUseCase(viewModels.repository, ValidateMatchResultUseCase()),
            imageCandidateValidator = ImageCandidateValidator(
                ImageCandidateMetadataReader {
                    ImageCandidateReadResult.Metadata(
                        "image/png",
                        width = 1920,
                        height = 1080,
                    )
                },
            ),
            screenshotDuplicateDetector = com.hoggamers.rankforge.presentation.screen.ScreenshotDuplicateDetector(
                com.hoggamers.rankforge.presentation.screen.ImageSourceFingerprintGenerator(
                    ImageSourceStreamOpener { uri -> uri.encodeToByteArray().inputStream() },
                    kotlinx.coroutines.Dispatchers.Unconfined,
                ),
            ),
            localImagePreserver = localImagePreserver,
            matchResultScreenshotAssetRepository = assetRepository,
        )
        val cropViewModel = MatchResultScreenshotCropViewModel(
            observeMatches = ObserveMatchesUseCase(viewModels.repository),
            assetRepository = assetRepository,
            localImagePreserver = localImagePreserver,
            clock = Clock.fixed(
                LocalDate.of(2026, 7, 24).atStartOfDay(ZoneOffset.UTC).toInstant(),
                ZoneOffset.UTC,
            ),
            uploadCheckpoint = MatchResultScreenshotUploadCheckpointAction {
                MatchResultScreenshotUploadCheckpointResult.Skipped
            },
            reconciliationScheduler = ScreenshotReconciliationScheduler(),
        )
        return ResultCropNavigationFixture(
            viewModels = viewModels,
            matchReviewViewModel = matchReviewViewModel,
            cropViewModel = cropViewModel,
            assetRepository = assetRepository,
            initialAssets = initialAssets,
            tournamentId = tournamentId,
            matchId = matchId,
        )
    }

    private fun resultAsset(
        localImagePreserver: com.hoggamers.rankforge.presentation.screen.LocalImagePreserver,
        tournamentId: String,
        matchId: String,
        role: MatchResultScreenshotRole,
        crop: OcrNormalizedCropRect?,
    ): MatchResultScreenshotAssetEntity = MatchResultScreenshotAssetEntity(
        tournamentId = tournamentId,
        matchId = matchId,
        screenshotKind = OcrScreenshotKind.MATCH_RESULT.name,
        screenshotRole = role.name,
        ownerUserId = "owner-id",
        localRelativePath = localImagePreserver.matchResultRelativePath(
            tournamentId = tournamentId,
            matchId = matchId,
            role = role,
            extension = "png",
        ),
        fileExtension = "png",
        mimeType = "image/png",
        originalWidth = 1920,
        originalHeight = 1080,
        byteSize = 1,
        sha256 = role.name.lowercase().padEnd(64, 'a'),
        localStatus = ScreenshotLocalStatus.PRESERVED.name,
        uploadStatus = ScreenshotUploadStatus.FAILED.name,
        uploadFailureCode = null,
        storageBucket = null,
        storageObjectPath = null,
        cropProfileId = crop?.let { OcrCropValidationProfiles.MatchResult.id },
        cropLeft = crop?.left,
        cropTop = crop?.top,
        cropRight = crop?.right,
        cropBottom = crop?.bottom,
        createdAt = 1,
        updatedAt = 1,
        preservedAt = 1,
        uploadedAt = null,
        revision = 1,
    )

    private fun createNavigationViewModels(
        repository: InMemoryTournamentRepository = InMemoryTournamentRepository(),
    ): NavigationViewModels {
        val uploadAction = TournamentCloudUploadAction {
            QueueAwareActionResult(
                primaryResult = TournamentCloudUploadResult.Success(1),
                queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
            )
        }
        val draftMatchSyncAction = DraftMatchCloudSyncAction {
            QueueAwareActionResult(
                primaryResult = DraftMatchCloudSyncResult.Success,
                queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
            )
        }
        return NavigationViewModels(
            repository = repository,
            creationViewModel = createCreationViewModel(repository, uploadAction),
            listViewModel = TournamentListViewModel(ObserveTournamentsUseCase(repository)),
            detailsViewModel = { tournamentId ->
                TournamentDetailsViewModel(
                    getTournamentById = GetTournamentByIdUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    googleSheetsStandingsExport = FakeGoogleSheetsStandingsExportRemoteDataSource(),
                    saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository),
                    validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
                    createNextMatch = CreateNextMatchUseCase(repository),
                    syncDraftMatches = draftMatchSyncAction,
                ).also {
                    it.load(tournamentId)
                }
            },
            standingsViewModel = { tournamentId ->
                TournamentStandingsViewModel(
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    cumulativeStandings = CumulativeTournamentStandingsEngine(),
                    tieBreakRules = TieBreakRules(),
                ).also {
                    it.load(tournamentId)
                }
            },
            teamEntryViewModel = { tournamentId ->
                TeamEntryViewModel(
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository),
                    validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
                    uploadTournament = uploadAction,
                ).also {
                    it.load(tournamentId)
                }
            },
            rosterEntryViewModel = { tournamentId, slotNumber ->
                RosterEntryViewModel(
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRosterPlayers = ObserveRosterPlayersUseCase(repository),
                    saveRoster = SaveRosterUseCase(repository),
                    rosterValidator = RosterValidator(),
                ).also {
                    it.load(tournamentId, slotNumber)
                }
            },
            rosterReviewViewModel = { tournamentId ->
                val validator = RosterValidator()
                RosterReviewViewModel(
                    getTournamentById = GetTournamentByIdUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRosterPlayers = ObserveRosterPlayersUseCase(repository),
                    validateTournamentRoster = ValidateTournamentRosterUseCase(repository, validator),
                    confirmTournamentRoster = ConfirmTournamentRosterUseCase(
                        repository = repository,
                        validateTournamentRoster = ValidateTournamentRosterUseCase(repository, validator),
                    ),
                ).also {
                    it.load(tournamentId)
                }
            },
            matchCreationViewModel = { tournamentId ->
                MatchCreationViewModel(CreateMatchUseCase(repository)).also {
                    it.load(tournamentId)
                }
            },
            matchPlacementViewModel = { tournamentId, matchId ->
                MatchPlacementViewModel(
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
                    saveMatchPlacements = SaveMatchPlacementsUseCase(repository),
                    saveDraftValue = SaveMatchDraftValueUseCase(repository),
                    clearDraftMatch = ClearDraftMatchUseCase(repository),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
            matchKillViewModel = { tournamentId, matchId ->
                MatchKillViewModel(
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
                    saveMatchKills = SaveMatchKillsUseCase(repository),
                    saveDraftValue = SaveMatchDraftValueUseCase(repository),
                    clearDraftMatch = ClearDraftMatchUseCase(repository),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
            matchReviewViewModel = { tournamentId, matchId ->
                MatchReviewViewModel(
                    getTournamentById = GetTournamentByIdUseCase(repository),
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
                    validateMatchResult = ValidateMatchResultUseCase(),
                    finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
                    imageCandidateValidator = com.hoggamers.rankforge.presentation.screen.ImageCandidateValidator(
                        com.hoggamers.rankforge.presentation.screen.ImageCandidateMetadataReader {
                            com.hoggamers.rankforge.presentation.screen.ImageCandidateReadResult.Metadata(
                                "image/png",
                                width = 1080,
                                height = 1920,
                            )
                        },
                    ),
                    screenshotDuplicateDetector = com.hoggamers.rankforge.presentation.screen.ScreenshotDuplicateDetector(
                        com.hoggamers.rankforge.presentation.screen.ImageSourceFingerprintGenerator(
                            com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener { uri ->
                                uri.encodeToByteArray().inputStream()
                            },
                            kotlinx.coroutines.Dispatchers.Unconfined,
                        ),
                    ),
                    localImagePreserver = com.hoggamers.rankforge.presentation.screen.LocalImagePreserver(
                        appPrivateRoot = context.filesDir,
                        sourceStreamOpener = com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener { uri ->
                            uri.encodeToByteArray().inputStream()
                        },
                        mimeTypeReader = com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader {
                            "image/png"
                        },
                        ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
                    ),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
            matchOcrReviewViewModel = { tournamentId, matchId ->
                MatchOcrReviewViewModel(
                    FinalizeOcrCorrectionMatchUseCase(
                        repository,
                        FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
                    ),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
            matchCorrectionViewModel = { tournamentId, matchId ->
                MatchCorrectionViewModel(
                    observeMatches = ObserveMatchesUseCase(repository),
                    observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
                    observeRoster = ObserveRosterByTournamentUseCase(repository),
                    observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
                    validateMatchResult = ValidateMatchResultUseCase(),
                    submitCorrection = SubmitMatchCorrectionUseCase(
                        repository,
                        ValidateMatchResultUseCase(),
                        com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionAction {
                            com.hoggamers.rankforge.domain.tournament.ProtectedMatchCorrectionResult.Success(2)
                        },
                    ),
                    saveDraftValue = SaveMatchDraftValueUseCase(repository),
                    clearCorrectionDraft = ClearMatchCorrectionDraftUseCase(repository),
                ).also {
                    it.load(tournamentId, matchId)
                }
            },
        )
    }

    private class InMemoryMatchResultScreenshotAssetRepository(
        initialAssets: List<MatchResultScreenshotAssetEntity>,
    ) : MatchResultScreenshotAssetRepository {
        private val assets = MutableStateFlow(initialAssets)

        override fun observeByMatchId(matchId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            assets.map { values -> values.filter { it.matchId == matchId } }

        override fun observeByIdentity(
            identity: MatchResultScreenshotIdentity,
        ): Flow<MatchResultScreenshotAssetEntity?> =
            assets.map { values -> values.firstOrNull { it.matches(identity) } }

        override suspend fun getByIdentity(identity: MatchResultScreenshotIdentity): MatchResultScreenshotAssetEntity? =
            assets.value.firstOrNull { it.matches(identity) }

        override fun observeByTournamentId(tournamentId: String): Flow<List<MatchResultScreenshotAssetEntity>> =
            assets.map { values -> values.filter { it.tournamentId == tournamentId } }

        override suspend fun findDuplicateFingerprint(
            identity: MatchResultScreenshotIdentity,
            sha256: String,
        ): MatchResultScreenshotAssetEntity? = null

        override suspend fun saveOrReplace(asset: MatchResultScreenshotAssetEntity): MatchResultScreenshotAssetSaveResult {
            assets.value = assets.value.filterNot { it.matches(asset) } + asset
            return MatchResultScreenshotAssetSaveResult.Saved
        }

        override suspend fun markLocalMissing(identity: MatchResultScreenshotIdentity, updatedAt: Long) {
            update(identity) { it.copy(localStatus = ScreenshotLocalStatus.MISSING.name, updatedAt = updatedAt) }
        }

        override suspend fun markCleanupFailure(identity: MatchResultScreenshotIdentity, updatedAt: Long) {
            update(identity) { it.copy(localStatus = ScreenshotLocalStatus.CLEANUP_FAILED.name, updatedAt = updatedAt) }
        }

        override suspend fun persistConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            crop: OcrNormalizedCropRect,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult {
            if (getByIdentity(identity) == null) return MatchResultScreenshotCropSaveResult.MissingAsset
            update(identity) {
                it.copy(
                    cropProfileId = OcrCropValidationProfiles.MatchResult.id,
                    cropLeft = crop.left,
                    cropTop = crop.top,
                    cropRight = crop.right,
                    cropBottom = crop.bottom,
                    updatedAt = updatedAt,
                    revision = it.revision + 1,
                )
            }
            return MatchResultScreenshotCropSaveResult.Saved
        }

        override suspend fun clearConfirmedCrop(
            identity: MatchResultScreenshotIdentity,
            updatedAt: Long,
        ): MatchResultScreenshotCropSaveResult {
            if (getByIdentity(identity) == null) return MatchResultScreenshotCropSaveResult.MissingAsset
            update(identity) {
                it.copy(
                    cropProfileId = null,
                    cropLeft = null,
                    cropTop = null,
                    cropRight = null,
                    cropBottom = null,
                    updatedAt = updatedAt,
                    revision = it.revision + 1,
                )
            }
            return MatchResultScreenshotCropSaveResult.Saved
        }

        override suspend fun deleteByIdentity(identity: MatchResultScreenshotIdentity) {
            assets.value = assets.value.filterNot { it.matches(identity) }
        }

        override suspend fun deleteByMatchId(matchId: String) {
            assets.value = assets.value.filterNot { it.matchId == matchId }
        }

        private fun update(
            identity: MatchResultScreenshotIdentity,
            transform: (MatchResultScreenshotAssetEntity) -> MatchResultScreenshotAssetEntity,
        ) {
            assets.value = assets.value.map { asset ->
                if (asset.matches(identity)) transform(asset) else asset
            }
        }

        private fun MatchResultScreenshotAssetEntity.matches(
            identity: MatchResultScreenshotIdentity,
        ): Boolean =
            tournamentId == identity.tournamentId &&
                matchId == identity.matchId &&
                screenshotKind == identity.kind.name &&
                screenshotRole == identity.role.name

        private fun MatchResultScreenshotAssetEntity.matches(
            asset: MatchResultScreenshotAssetEntity,
        ): Boolean =
            tournamentId == asset.tournamentId &&
                matchId == asset.matchId &&
                screenshotKind == asset.screenshotKind &&
                screenshotRole == asset.screenshotRole
    }

    private suspend fun createValidRoster(
        repository: InMemoryTournamentRepository,
        tournamentId: String,
    ) {
        repository.create(
            Tournament(
                id = tournamentId,
                name = "Setup Cup",
                date = LocalDate.of(2026, 7, 24),
                organizerName = "Alex",
                organizerContactNumber = "123",
                status = TournamentStatus.DRAFT,
            ),
        )
        SaveTeamSlotNamesUseCase(repository)(
            tournamentId,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        val saveRoster = SaveRosterUseCase(repository)
        (1..12).forEach { slotNumber ->
            saveRoster(
                tournamentId = tournamentId,
                slotNumber = slotNumber,
                players = (0..3).map { playerIndex ->
                    RosterPlayer.create(tournamentId, slotNumber, "Player $playerIndex")
                },
            )
        }
    }

    private fun confirmedTournament() = Tournament(
        id = "confirmed-id",
        name = "Confirmed Cup",
        date = LocalDate.of(2026, 7, 24),
        organizerName = "Alex",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
    )

    private fun createConfirmedTournamentWithOneActiveTeam(
        repository: InMemoryTournamentRepository,
    ) = runBlocking {
        repository.create(confirmedTournament())
        repository.saveTeamNames("confirmed-id", mapOf(1 to "Team 1"))
    }

    private fun createFinalizedMatch(viewModels: NavigationViewModels): String = runBlocking {
        val tournamentId = "confirmed-id"
        viewModels.repository.create(confirmedTournament())
        viewModels.repository.saveTeamNames(
            tournamentId,
            (1..12).associateWith { slotNumber -> "Team $slotNumber" },
        )
        val match = (
            CreateMatchUseCase(viewModels.repository)(
                CreateMatchInput(
                    tournamentId = tournamentId,
                    matchNumber = "1",
                    date = LocalDate.of(2026, 7, 24),
                    mapName = "Bermuda",
                ),
            ) as CreateMatchResult.Created
        ).match
        viewModels.repository.finalizeDraftMatch(
            matchId = match.id,
            placements = (1..12).map { MatchPlacement(it, it) },
            kills = (1..12).map { MatchKill(it, it - 1) },
        )
        match.id
    }

    private fun createCreationViewModel(
        repository: InMemoryTournamentRepository,
        uploadAction: TournamentCloudUploadAction,
    ): TournamentCreationViewModel {
        val today = LocalDate.of(2026, 7, 24)
        return TournamentCreationViewModel(
            createTournament = CreateTournamentUseCase(
                repository = repository,
                authRepository = object : AuthRepository {
                    override fun observeAuthState(): Flow<AuthState> = flowOf(
                        AuthState.SignedIn(AuthUser("navigation-user", null)),
                    )

                    override suspend fun restoreSession(): AuthRestorationResult =
                        AuthRestorationResult.NoSavedSession

                    override suspend fun signUp(email: String, password: String): AuthOperationResult =
                        error("unused")

                    override suspend fun login(email: String, password: String): AuthOperationResult =
                        error("unused")

                    override suspend fun logout(): AuthOperationResult = error("unused")
                },
                clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            ),
            clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            checkTournamentQuota = CheckTournamentQuotaUseCase(
                object : TournamentQuotaRepository {
                    override suspend fun checkQuota(): TournamentQuotaResult =
                        TournamentQuotaResult.Allowed(0)
                },
            ),
            uploadTournament = uploadAction,
            localDeletionRepository = object : LocalDeletionRepository {
                override suspend fun deleteMatchLocally(matchId: String): LocalDeletionResult =
                    LocalDeletionResult.NotFound

                override suspend fun deleteTournamentLocally(tournamentId: String): LocalDeletionResult =
                    LocalDeletionResult.NotFound
            },
        )
    }

    private fun createTournamentFromViewModel(viewModel: TournamentCreationViewModel) {
        viewModel.onTournamentNameChanged("Summer Cup")
        viewModel.onTournamentDateChanged(LocalDate.of(2026, 7, 24))
        viewModel.onOrganizerNameChanged("Alex")
        viewModel.onOrganizerContactNumberChanged("123")
        viewModel.submit()
    }

    private data class ResultCropNavigationFixture(
        val viewModels: NavigationViewModels,
        val matchReviewViewModel: MatchReviewViewModel,
        val cropViewModel: MatchResultScreenshotCropViewModel,
        val assetRepository: InMemoryMatchResultScreenshotAssetRepository,
        val initialAssets: Map<MatchResultScreenshotRole, MatchResultScreenshotAssetEntity>,
        val tournamentId: String,
        val matchId: String,
    ) {
        fun identity(role: MatchResultScreenshotRole): MatchResultScreenshotIdentity =
            MatchResultScreenshotIdentity(
                tournamentId = tournamentId,
                matchId = matchId,
                role = role,
            )
    }

    private data class NavigationViewModels(
        val repository: InMemoryTournamentRepository,
        val creationViewModel: TournamentCreationViewModel,
        val listViewModel: TournamentListViewModel,
        val detailsViewModel: (String) -> TournamentDetailsViewModel,
        val standingsViewModel: (String) -> TournamentStandingsViewModel,
        val teamEntryViewModel: (String) -> TeamEntryViewModel,
        val rosterEntryViewModel: (String, Int) -> RosterEntryViewModel,
        val rosterReviewViewModel: (String) -> RosterReviewViewModel,
        val matchCreationViewModel: (String) -> MatchCreationViewModel,
        val matchPlacementViewModel: (String, String) -> MatchPlacementViewModel,
        val matchKillViewModel: (String, String) -> MatchKillViewModel,
        val matchReviewViewModel: (String, String) -> MatchReviewViewModel,
        val matchOcrReviewViewModel: (String, String) -> MatchOcrReviewViewModel,
        val matchCorrectionViewModel: (String, String) -> MatchCorrectionViewModel,
    )
}
