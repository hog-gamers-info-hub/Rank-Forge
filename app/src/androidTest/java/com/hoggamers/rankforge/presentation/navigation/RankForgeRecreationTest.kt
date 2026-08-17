package com.hoggamers.rankforge.presentation.navigation

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportExecutionResult
import com.hoggamers.rankforge.data.export.GoogleSheetsStandingsExportRemoteDataSource
import com.hoggamers.rankforge.data.local.RankForgeDatabase
import com.hoggamers.rankforge.data.tournament.RoomTournamentRepository
import com.hoggamers.rankforge.domain.export.TournamentStandingsExportRow
import com.hoggamers.rankforge.domain.sync.QueueAwareActionResult
import com.hoggamers.rankforge.domain.sync.QueueRecordingResult
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncAction
import com.hoggamers.rankforge.domain.tournament.DraftMatchCloudSyncResult
import com.hoggamers.rankforge.domain.tournament.FinalizeMatchUseCase
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.Match
import com.hoggamers.rankforge.domain.tournament.MatchStatus
import com.hoggamers.rankforge.domain.tournament.ObserveMatchDraftValuesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentsUseCase
import com.hoggamers.rankforge.domain.tournament.RosterValidator
import com.hoggamers.rankforge.domain.tournament.SaveTeamSlotNamesUseCase
import com.hoggamers.rankforge.domain.tournament.Tournament
import com.hoggamers.rankforge.domain.tournament.TournamentStatus
import com.hoggamers.rankforge.domain.tournament.ValidateMatchResultUseCase
import com.hoggamers.rankforge.domain.tournament.ValidateTournamentRosterUseCase
import com.hoggamers.rankforge.presentation.auth.AuthUiState
import com.hoggamers.rankforge.presentation.screen.ImageCandidateMetadataReader
import com.hoggamers.rankforge.presentation.screen.ImageCandidateReadResult
import com.hoggamers.rankforge.presentation.screen.ImageCandidateValidator
import com.hoggamers.rankforge.presentation.screen.ImageSourceFingerprintGenerator
import com.hoggamers.rankforge.presentation.screen.ImageSourceStreamOpener
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import com.hoggamers.rankforge.presentation.screen.MATCH_REVIEW_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.MATCH_ITEM_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_DETAILS_SCREEN_TEST_TAG
import com.hoggamers.rankforge.presentation.screen.TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX
import com.hoggamers.rankforge.presentation.theme.RankForgeTheme
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private object RankForgeRecreationTestHarness {
    @Volatile
    var navController: NavHostController? = null
}

@RunWith(AndroidJUnit4::class)
class RankForgeRecreationTest {
    private val fixture = RecreationFixture.create().also { fixture ->
        RankForgeRecreationActivityConfiguration.content = {
            RankForgeTheme {
                val navController = rememberNavController()
                val navigationViewModels = remember { fixture.createNavigationViewModels() }
                SideEffect {
                    RankForgeRecreationTestHarness.navController = navController
                }
                RankForgeNavHost(
                    navController = navController,
                    authUiState = AuthUiState(isSignedIn = true),
                    listViewModel = navigationViewModels.listViewModel,
                    detailsViewModelFactory = navigationViewModels::detailsViewModel,
                    matchReviewViewModelFactory = navigationViewModels::matchReviewViewModel,
                    matchLobbyScreenshotIntakeContent = { _, _, _, _ -> },
                )
            }
        }
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<RankForgeRecreationActivity>()

    @After
    fun tearDown() {
        composeTestRule.activityRule.scenario.close()
        fixture.close()
        RankForgeRecreationTestHarness.navController = null
        RankForgeRecreationActivityConfiguration.content = null
    }

    @Test
    fun tournamentDetailsRecreationPreservesIdentityAndBackNavigation() {
        val tournament = fixture.tournament

        waitForTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournament.id)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournament.id)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertDetailsRoute(tournament.id)
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(tournament.name).assertIsDisplayed()

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        assertDetailsRoute(tournament.id)
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(tournament.name).assertIsDisplayed()

        pressBack()
        assertListRoute()
    }

    @Test
    fun matchReviewRecreationPreservesTournamentAndMatchIdentity() {
        val tournament = fixture.tournament
        val match = fixture.match

        waitForTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournament.id)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TOURNAMENT_LIST_ITEM_TEST_TAG_PREFIX + tournament.id)
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertDetailsRoute(tournament.id)
        composeTestRule.onNodeWithTag(MATCH_ITEM_TEST_TAG_PREFIX + match.matchNumber)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assertMatchReviewRoute(tournament.id, match.id)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Review Match ${match.matchNumber}").assertIsDisplayed()

        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        assertMatchReviewRoute(tournament.id, match.id)
        composeTestRule.onNodeWithTag(MATCH_REVIEW_SCREEN_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Review Match ${match.matchNumber}").assertIsDisplayed()

        pressBack()
        assertDetailsRoute(tournament.id)
        composeTestRule.onNodeWithTag(TOURNAMENT_DETAILS_SCREEN_TEST_TAG).assertIsDisplayed()
    }

    private fun assertListRoute() {
        composeTestRule.runOnIdle {
            val entry = requireNotNull(RankForgeRecreationTestHarness.navController)
                .currentBackStackEntry
            assertEquals(TournamentListDestination, entry?.toRoute<TournamentListDestination>())
        }
    }

    private fun assertDetailsRoute(tournamentId: String) {
        composeTestRule.runOnIdle {
            val entry = requireNotNull(RankForgeRecreationTestHarness.navController)
                .currentBackStackEntry
            assertEquals(
                TournamentDetailsDestination(tournamentId),
                entry?.toRoute<TournamentDetailsDestination>(),
            )
        }
    }

    private fun assertMatchReviewRoute(tournamentId: String, matchId: String) {
        composeTestRule.runOnIdle {
            val entry = requireNotNull(RankForgeRecreationTestHarness.navController)
                .currentBackStackEntry
            assertEquals(
                MatchReviewDestination(tournamentId, matchId),
                entry?.toRoute<MatchReviewDestination>(),
            )
        }
    }

    private fun pressBack() {
        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun waitForTag(tag: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private class RecreationFixture private constructor(
    private val context: android.content.Context,
    private val databaseName: String,
    private val database: RankForgeDatabase,
    private val repository: RoomTournamentRepository,
) {
    val tournament = Tournament(
        id = "recreation-tournament",
        name = "Recreation Cup",
        date = LocalDate.of(2026, 8, 15),
        organizerName = "Organizer",
        organizerContactNumber = "123",
        status = TournamentStatus.CONFIRMED,
    )
    val match = Match(
        id = "recreation-match",
        tournamentId = tournament.id,
        matchNumber = 1,
        date = tournament.date,
        mapName = "Bermuda",
        status = MatchStatus.DRAFT,
    )

    init {
        runBlocking {
            repository.create(tournament)
            repository.saveTeamNames(tournament.id, mapOf(1 to "Team One"))
            repository.createDraftMatch(match)
        }
    }

    fun createListViewModel() =
        com.hoggamers.rankforge.presentation.screen.TournamentListViewModel(
            ObserveTournamentsUseCase(repository),
        )

    fun createDetailsViewModel(tournamentId: String) =
        com.hoggamers.rankforge.presentation.screen.TournamentDetailsViewModel(
            getTournamentById = GetTournamentByIdUseCase(repository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeMatches = ObserveMatchesUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            googleSheetsStandingsExport = NoOpGoogleSheetsExport,
            saveTeamSlotNames = SaveTeamSlotNamesUseCase(repository),
            validateTournamentRoster = ValidateTournamentRosterUseCase(repository, RosterValidator()),
            createNextMatch = com.hoggamers.rankforge.domain.tournament.CreateNextMatchUseCase(repository),
            syncDraftMatches = NoOpDraftMatchSync,
        )

    fun createMatchReviewViewModel(tournamentId: String, matchId: String) =
        com.hoggamers.rankforge.presentation.screen.MatchReviewViewModel(
            getTournamentById = GetTournamentByIdUseCase(repository),
            observeMatches = ObserveMatchesUseCase(repository),
            observeTournamentSlots = ObserveTournamentSlotsUseCase(repository),
            observeRoster = ObserveRosterByTournamentUseCase(repository),
            observeDraftValues = ObserveMatchDraftValuesUseCase(repository),
            validateMatchResult = ValidateMatchResultUseCase(),
            finalizeMatch = FinalizeMatchUseCase(repository, ValidateMatchResultUseCase()),
            imageCandidateValidator = ImageCandidateValidator(
                ImageCandidateMetadataReader { ImageCandidateReadResult.Unreadable },
            ),
            screenshotDuplicateDetector = com.hoggamers.rankforge.presentation.screen.ScreenshotDuplicateDetector(
                ImageSourceFingerprintGenerator(ImageSourceStreamOpener { null }),
            ),
            localImagePreserver = LocalImagePreserver(
                appPrivateRoot = context.filesDir,
                sourceStreamOpener = ImageSourceStreamOpener { null },
                mimeTypeReader = com.hoggamers.rankforge.presentation.screen.ImageSourceMimeTypeReader { null },
            ),
        )

    fun close() {
        if (database.isOpen) database.close()
        context.deleteDatabase(databaseName)
    }

    fun createNavigationViewModels(): RecreationNavigationViewModels =
        RecreationNavigationViewModels(this)

    companion object {
        fun create(): RecreationFixture {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val databaseName = "cr0034_recreation_${UUID.randomUUID()}"
            val database = Room.databaseBuilder(
                context,
                RankForgeDatabase::class.java,
                databaseName,
            ).build()
            return RecreationFixture(
                context = context,
                databaseName = databaseName,
                database = database,
                repository = RoomTournamentRepository(database),
            )
        }
    }
}

private class RecreationNavigationViewModels(
    private val fixture: RecreationFixture,
) {
    val listViewModel = fixture.createListViewModel()
    private val detailsViewModels = mutableMapOf<String, com.hoggamers.rankforge.presentation.screen.TournamentDetailsViewModel>()
    private val matchReviewViewModels = mutableMapOf<String, com.hoggamers.rankforge.presentation.screen.MatchReviewViewModel>()

    fun detailsViewModel(tournamentId: String) =
        detailsViewModels.getOrPut(tournamentId) { fixture.createDetailsViewModel(tournamentId) }

    fun matchReviewViewModel(tournamentId: String, matchId: String) =
        matchReviewViewModels.getOrPut("$tournamentId:$matchId") {
            fixture.createMatchReviewViewModel(tournamentId, matchId)
        }
}

private object NoOpGoogleSheetsExport : GoogleSheetsStandingsExportRemoteDataSource {
    override suspend fun export(
        tournamentId: String,
        rows: List<TournamentStandingsExportRow>,
    ): GoogleSheetsStandingsExportExecutionResult =
        GoogleSheetsStandingsExportExecutionResult.Success(
            exportedMatchCount = 0,
            rowsWritten = rows.size,
        )
}

private object NoOpDraftMatchSync : DraftMatchCloudSyncAction {
    override suspend fun invoke(tournamentId: String): QueueAwareActionResult<DraftMatchCloudSyncResult> =
        QueueAwareActionResult(
            primaryResult = DraftMatchCloudSyncResult.Success,
            queueRecordingResult = QueueRecordingResult.NOT_REQUIRED,
        )
}
