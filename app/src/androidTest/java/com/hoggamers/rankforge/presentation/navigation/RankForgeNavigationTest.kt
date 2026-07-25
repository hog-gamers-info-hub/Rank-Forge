package com.hoggamers.rankforge.presentation.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.tournament.CreateTournamentUseCase
import com.hoggamers.rankforge.presentation.screen.TournamentCreationViewModel
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_CREATION_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme

@RunWith(AndroidJUnit4::class)
class RankForgeNavigationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun navigationMovesForwardAndBackThroughVisibleDestinations() {
        val creationViewModel = createCreationViewModel()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(creationViewModel = creationViewModel)
            }
        }

        val listTitle = context.getString(R.string.foundation_title)
        val openAction = context.getString(R.string.open_tournament_creation)

        composeTestRule.onNodeWithText(listTitle).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)

        composeTestRule.onNodeWithText(openAction).performClick()
        composeTestRule.onNodeWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()

        pressBackOnMainThread()
        composeTestRule.onNodeWithText(listTitle).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun dirtyBackShowsConfirmationBeforeReturningToList() {
        val creationViewModel = createCreationViewModel()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(creationViewModel = creationViewModel)
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.open_tournament_creation)).performClick()
        composeTestRule.runOnIdle { creationViewModel.onTournamentNameChanged("Draft") }
        composeTestRule.waitForIdle()
        pressBackOnMainThread()

        composeTestRule.onNodeWithText(context.getString(R.string.keep_editing_action)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TOURNAMENT_CREATION_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.discard_changes_action)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.foundation_title)).assertIsDisplayed()
    }

    @Test
    fun successfulCreationReturnsToList() {
        val creationViewModel = createCreationViewModel()
        composeTestRule.setContent {
            RankForgeTheme {
                RankForgeNavHost(creationViewModel = creationViewModel)
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.open_tournament_creation)).performClick()
        creationViewModel.onTournamentNameChanged("Summer Cup")
        creationViewModel.onTournamentDateChanged(LocalDate.of(2026, 7, 24))
        creationViewModel.onOrganizerNameChanged("Alex")
        creationViewModel.onOrganizerContactNumberChanged("123")
        creationViewModel.submit()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.foundation_title)).assertIsDisplayed()
    }

    private fun pressBackOnMainThread() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun createCreationViewModel(): TournamentCreationViewModel {
        val today = LocalDate.of(2026, 7, 24)
        return TournamentCreationViewModel(
            CreateTournamentUseCase(
                repository = InMemoryTournamentRepository(),
                clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
            ),
        )
    }
}
