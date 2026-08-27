package com.hoggamers.rankforge.presentation.screen

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TournamentStandingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun finalizedStandingsDisplayAllDerivedFields() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(
                        isLoading = false,
                        rows = listOf(standingRow(order = 1, slot = 2)),
                    ),
                    onBackToTournamentDetails = {},
                    onShareStandings = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_LIST_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_STANDING_ROW_TEST_TAG_PREFIX + "2").assertIsDisplayed()
        listOf(
            context.getString(R.string.tournament_standing_team_slot_inline, 2),
            context.getString(R.string.tournament_standing_total_points_label),
            context.getString(R.string.tournament_standing_position_points_label),
            context.getString(R.string.tournament_standing_kill_points_label),
            context.getString(R.string.tournament_standing_first_place_finishes_label),
            context.getString(R.string.tournament_standing_latest_placement_label),
            context.getString(R.string.tournament_standing_matches_included_label),
        ).forEach { text ->
            composeTestRule
                .onNodeWithText(text)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun completeTieIsShownAsUnresolved() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(
                        isLoading = false,
                        rows = listOf(
                            standingRow(order = 1, slot = 1, isCompleteTie = true),
                            standingRow(order = 2, slot = 2, isCompleteTie = true),
                        ),
                    ),
                    onBackToTournamentDetails = {},
                    onShareStandings = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_STANDING_COMPLETE_TIE_TEST_TAG_PREFIX + "1")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(TOURNAMENT_STANDING_COMPLETE_TIE_TEST_TAG_PREFIX + "2")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.tournament_standing_complete_tie_message))
            .assertCountEquals(2)
    }

    @Test
    fun noFinalizedMatchesShowEmptyState() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(isLoading = false),
                    onBackToTournamentDetails = {},
                    onShareStandings = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_EMPTY_TEST_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.tournament_standings_empty_title))
            .assertIsDisplayed()
    }

    @Test
    fun populatedStandingsShowEnabledShareAndBackActions() {
        var shareClicks = 0
        var backClicks = 0
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(
                        isLoading = false,
                        rows = listOf(standingRow(order = 1, slot = 2)),
                    ),
                    onBackToTournamentDetails = { backClicks += 1 },
                    onShareStandings = { shareClicks += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(TOURNAMENT_STANDINGS_SHARE_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.back_action)).performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, shareClicks)
            assertEquals(1, backClicks)
        }
    }

    @Test
    fun emptyStandingsDoNotShowShare() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(isLoading = false),
                    onBackToTournamentDetails = {},
                    onShareStandings = {},
                )
            }
        }
        composeTestRule.onAllNodesWithTag(TOURNAMENT_STANDINGS_SHARE_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun loadingStandingsDoNotShowShare() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(isLoading = true),
                    onBackToTournamentDetails = {},
                    onShareStandings = {},
                )
            }
        }
        composeTestRule.onAllNodesWithTag(TOURNAMENT_STANDINGS_SHARE_ACTION_TEST_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun publishingDisablesShare() {
        composeTestRule.setContent {
            RankForgeTheme {
                TournamentStandingsScreen(
                    uiState = TournamentStandingsUiState(
                        isLoading = false,
                        rows = listOf(standingRow(order = 1, slot = 2)),
                        isPublishing = true,
                    ),
                    onBackToTournamentDetails = {},
                    onShareStandings = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(TOURNAMENT_STANDINGS_SHARE_ACTION_TEST_TAG)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun shareChooserIntentUsesExpectedSendIntentAndLocalizedTitle() {
        val publicUrl = "https://example.supabase.co/functions/v1/public-tournament-standings?token=test"
        val chooserIntent = createTournamentStandingsShareChooserIntent(
            publicUrl = publicUrl,
            shareTextTitle = "Tournament Standings",
            chooserTitle = "Share tournament standings",
        )
        val shareIntent = chooserIntent.extras?.get(Intent.EXTRA_INTENT) as? Intent

        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        assertEquals("Share tournament standings", chooserIntent.getCharSequenceExtra(Intent.EXTRA_TITLE))
        assertNotNull(shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent?.action)
        assertEquals("text/plain", shareIntent?.type)
        assertEquals(
            "Tournament Standings\n$publicUrl",
            shareIntent?.getStringExtra(Intent.EXTRA_TEXT),
        )
        assertFalse(shareIntent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty().contains("tournament-id"))
    }

    @Test
    fun shareUrlEventLaunchesOneChooserWithoutFailureMessage() {
        val events = Channel<TournamentStandingsShareEvent>(Channel.BUFFERED)
        val launchedIntents = mutableListOf<Intent>()
        val failureMessages = mutableListOf<String>()
        composeTestRule.setContent {
            TournamentStandingsShareEventEffect(
                shareEvents = events.receiveAsFlow(),
                shareTextTitle = "Tournament Standings",
                chooserTitle = "Share tournament standings",
                failureMessage = FAILURE_MESSAGE,
                startActivity = { launchedIntents += it },
                showFailure = { failureMessages += it },
            )
        }

        events.trySend(TournamentStandingsShareEvent.ShareUrl(PUBLIC_URL))
        composeTestRule.waitUntil(timeoutMillis = 5_000) { launchedIntents.size == 1 }

        assertEquals(1, launchedIntents.size)
        assertEquals(0, failureMessages.size)
        assertEquals(Intent.ACTION_CHOOSER, launchedIntents.single().action)
    }

    @Test
    fun shareFailedEventShowsOneFailureMessageWithoutChooser() {
        val events = Channel<TournamentStandingsShareEvent>(Channel.BUFFERED)
        val launchedIntents = mutableListOf<Intent>()
        val failureMessages = mutableListOf<String>()
        composeTestRule.setContent {
            TournamentStandingsShareEventEffect(
                shareEvents = events.receiveAsFlow(),
                shareTextTitle = "Tournament Standings",
                chooserTitle = "Share tournament standings",
                failureMessage = FAILURE_MESSAGE,
                startActivity = { launchedIntents += it },
                showFailure = { failureMessages += it },
            )
        }

        events.trySend(TournamentStandingsShareEvent.ShareFailed)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { failureMessages.size == 1 }

        assertEquals(listOf(FAILURE_MESSAGE), failureMessages)
        assertEquals(0, launchedIntents.size)
    }

    @Test
    fun consumedShareUrlIsNotReplayedAfterRecomposition() {
        val events = Channel<TournamentStandingsShareEvent>(Channel.BUFFERED)
        val launchedIntents = mutableListOf<Intent>()
        val recomposition = mutableStateOf(0)
        composeTestRule.setContent {
            recomposition.value
            TournamentStandingsShareEventEffect(
                shareEvents = events.receiveAsFlow(),
                shareTextTitle = "Tournament Standings",
                chooserTitle = "Share tournament standings",
                failureMessage = FAILURE_MESSAGE,
                startActivity = { launchedIntents += it },
                showFailure = {},
            )
        }

        events.trySend(TournamentStandingsShareEvent.ShareUrl(PUBLIC_URL))
        composeTestRule.waitUntil(timeoutMillis = 5_000) { launchedIntents.size == 1 }
        composeTestRule.runOnIdle { recomposition.value += 1 }
        composeTestRule.waitForIdle()

        assertEquals(1, launchedIntents.size)
    }

    private fun standingRow(
        order: Int,
        slot: Int,
        isCompleteTie: Boolean = false,
    ) = TournamentStandingRowUiState(
        displayOrder = order,
        teamSlotNumber = slot,
        teamName = null,
        totalPoints = 19,
        totalPositionPoints = 9,
        totalKillPoints = 10,
        firstPlaceFinishes = 0,
        latestMatchPlacement = 2,
        matchesIncluded = 1,
        isCompleteTie = isCompleteTie,
    )
}

private const val PUBLIC_URL =
    "https://example.supabase.co/functions/v1/public-tournament-standings?token=test"
private const val FAILURE_MESSAGE = "Unable to create share link. Try again."
