package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreAction
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreResult
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryResult
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteAction
import com.hoggamers.rankforge.data.cloud.CustomDesignDeleteResult
import com.hoggamers.rankforge.data.export.CustomDesignBitmapComposeResult
import com.hoggamers.rankforge.data.export.CustomDesignBitmapComposer
import com.hoggamers.rankforge.data.export.CustomDesignResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.CustomDesignResultRowsResolver
import com.hoggamers.rankforge.data.export.CustomDesignResultRowsResult
import com.hoggamers.rankforge.data.export.FreeDesignBitmapComposer
import com.hoggamers.rankforge.data.export.FreeDesignBitmapComposeResult
import com.hoggamers.rankforge.data.export.FreeDesignResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.FreeDesignTemplateRegistry
import com.hoggamers.rankforge.data.export.ResultDocumentWriteResult
import com.hoggamers.rankforge.data.export.ResultDocumentWriter
import com.hoggamers.rankforge.data.export.ResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.ResultDownloadExecutionResult
import com.hoggamers.rankforge.data.export.ResultDownloadScope
import com.hoggamers.rankforge.data.export.ResultExportFileFormat
import com.hoggamers.rankforge.data.export.ResultPngRenderResult
import com.hoggamers.rankforge.data.export.ResultPngRenderer
import com.hoggamers.rankforge.data.export.ResultDownloadRequest
import com.hoggamers.rankforge.domain.export.MatchCsvExportInput
import com.hoggamers.rankforge.domain.export.MatchResultExportModelBuildResult
import com.hoggamers.rankforge.domain.export.ResultExportModelBuilder
import com.hoggamers.rankforge.domain.export.TournamentCsvExportInput
import com.hoggamers.rankforge.domain.export.TournamentResultExportModelBuildResult
import com.hoggamers.rankforge.domain.tournament.GetTournamentByIdUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveMatchesUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.ObserveTournamentSlotsUseCase
import com.hoggamers.rankforge.presentation.component.PointIqHomeSystemBars
import com.hoggamers.rankforge.presentation.component.PointIqConfirmationDialog
import com.hoggamers.rankforge.presentation.component.PointIqPageHeader
import com.hoggamers.rankforge.presentation.component.pointIqHomeBackground
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DOWNLOAD_RESULT_SCREEN_TEST_TAG = "download_result_screen"
const val DOWNLOAD_RESULT_OVERALL_OPTION_TEST_TAG = "download_result_overall_option"
const val DOWNLOAD_RESULT_MATCH_OPTION_TEST_TAG_PREFIX = "download_result_match_option_"
const val DOWNLOAD_RESULT_DESIGN_IMAGE_OPTION_TEST_TAG = "download_result_design_image"
const val DOWNLOAD_RESULT_DESIGN_FREE_OPTION_TEST_TAG = "download_result_design_free"
const val DOWNLOAD_RESULT_DESIGN_MY_OPTION_TEST_TAG = "download_result_design_my"
const val DOWNLOAD_RESULT_FREE_TEMPLATE_OPTION_TEST_TAG_PREFIX =
    "download_result_free_template_"

private val DownloadResultSelectedChipBackground = Color(0xFF0B2B55)
private val DownloadResultUnselectedChipBackground = Color(0xFF071B3E)
private val DownloadResultSelectedChipBorder = Color(0xFF17C9F2)
private val DownloadResultUnselectedChipBorder = Color(0xFF176AF7).copy(alpha = 0.55f)
private val DownloadResultSelectedChipText = Color(0xFFF6F8FF)
private val DownloadResultUnselectedChipText = Color(0xFF91AFE0)
private val DownloadResultSectionTitleColor = Color(0xFFF6F8FF)
private val DownloadResultPreviewSurface = Color(0xFF071B3E)
private val DownloadResultPreviewSkeletonBase = Color(0xFF082440)
private val DownloadResultPreviewSkeletonHighlight = Color(0xFF124A78)

data class DownloadResultMatchOption(
    val matchId: String,
    val matchNumber: Int,
)

data class DownloadResultFreeDesignOption(
    val id: String,
    val displayName: String,
)

data class DownloadResultUiState(
    val matches: List<DownloadResultMatchOption> = emptyList(),
    val freeDesignOptions: List<DownloadResultFreeDesignOption> =
        FreeDesignTemplateRegistry.all.map { template ->
            DownloadResultFreeDesignOption(
                id = template.id,
                displayName = template.displayName,
            )
        },
)

sealed interface DownloadResultSelection {
    val exportScope: ResultDownloadScope

    data object Overall : DownloadResultSelection {
        override val exportScope: ResultDownloadScope = ResultDownloadScope.WHOLE_TOURNAMENT
    }

    data class Match(
        val matchId: String,
    ) : DownloadResultSelection {
        override val exportScope: ResultDownloadScope = ResultDownloadScope.CURRENT_MATCH
    }
}

enum class DownloadResultDesignType {
    IMAGE,
    FREE_DESIGN,
    MY_DESIGN,
}

sealed interface DownloadResultPreviewState {
    data object Idle : DownloadResultPreviewState
    data object Loading : DownloadResultPreviewState
    data class ResultImage(val pngBytes: ByteArray) : DownloadResultPreviewState
    data object ImportYourDesign : DownloadResultPreviewState
    data object Unavailable : DownloadResultPreviewState
}

sealed interface DownloadResultDownloadState {
    data object Idle : DownloadResultDownloadState
    data object Generating : DownloadResultDownloadState
    data object Saving : DownloadResultDownloadState
    data class DestinationLaunchRequested(
        val format: ResultExportFileFormat,
        val suggestedDisplayName: String,
    ) : DownloadResultDownloadState
    data object WaitingForDestination : DownloadResultDownloadState
    data object Success : DownloadResultDownloadState
    data object Failure : DownloadResultDownloadState

    val isBusy: Boolean
        get() = this is Generating ||
            this is Saving ||
            this is DestinationLaunchRequested ||
            this is WaitingForDestination
}

@HiltViewModel
class DownloadResultViewModel @Inject constructor(
    private val observeMatches: ObserveMatchesUseCase,
    private val getTournamentById: GetTournamentByIdUseCase,
    private val observeTournamentSlots: ObserveTournamentSlotsUseCase,
    private val observeRoster: ObserveRosterByTournamentUseCase,
    private val customDesignSavedIdDiscovery: CustomDesignSavedIdDiscoveryAction,
    private val customDesignRestore: CustomDesignRestoreAction,
    private val customDesignDelete: CustomDesignDeleteAction,
    private val customDesignRowsResolver: CustomDesignResultRowsResolver,
    private val customDesignBitmapComposer: CustomDesignBitmapComposer,
    private val freeDesignBitmapComposer: FreeDesignBitmapComposer,
    private val resultDownloadCoordinator: ResultDownloadCoordinator,
    private val freeDesignResultDownloadCoordinator: FreeDesignResultDownloadCoordinator,
    private val customDesignResultDownloadCoordinator: CustomDesignResultDownloadCoordinator,
    private val resultDocumentWriter: ResultDocumentWriter,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadResultUiState())
    val uiState: StateFlow<DownloadResultUiState> = _uiState.asStateFlow()

    private val _selectedFreeDesignTemplateId = MutableStateFlow(
        FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
    )
    val selectedFreeDesignTemplateId: StateFlow<String> =
        _selectedFreeDesignTemplateId.asStateFlow()

    private val _previewState = MutableStateFlow<DownloadResultPreviewState>(DownloadResultPreviewState.Idle)
    val previewState: StateFlow<DownloadResultPreviewState> = _previewState.asStateFlow()

    private val _hasSavedCustomDesign = MutableStateFlow(false)
    val hasSavedCustomDesign: StateFlow<Boolean> = _hasSavedCustomDesign.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadResultDownloadState>(DownloadResultDownloadState.Idle)
    val downloadState: StateFlow<DownloadResultDownloadState> = _downloadState.asStateFlow()
    private val shareEventsChannel = Channel<ResultShareRequest>(Channel.BUFFERED)
    val shareEvents: Flow<ResultShareRequest> = shareEventsChannel.receiveAsFlow()

    private var loadedTournamentId: String? = null
    private var selectedTournamentId: String? = null
    private var selectedResult: DownloadResultSelection = DownloadResultSelection.Overall
    private var selectedDesign: DownloadResultDesignType = DownloadResultDesignType.IMAGE
    private var customDesignId: String? = null
    private var previewJob: kotlinx.coroutines.Job? = null
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var deleteJob: kotlinx.coroutines.Job? = null
    private var pendingDocument: PendingDocument? = null

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        viewModelScope.launch {
            observeMatches(tournamentId).collect { matches ->
                _uiState.value = DownloadResultUiState(
                    matches = matches
                        .sortedBy { it.matchNumber }
                        .map { match ->
                            DownloadResultMatchOption(
                                matchId = match.id,
                                matchNumber = match.matchNumber,
                            )
                        },
                )
            }
        }
    }

    fun select(
        tournamentId: String,
        result: DownloadResultSelection,
        design: DownloadResultDesignType,
    ) {
        selectedTournamentId = tournamentId
        selectedResult = result
        selectedDesign = design
        previewJob?.cancel()
        customDesignId = null
        _hasSavedCustomDesign.value = false
        _previewState.value = DownloadResultPreviewState.Loading
        previewJob = viewModelScope.launch {
            try {
                val request = buildRequest(tournamentId, result)
                if (request == null) {
                    _previewState.value = DownloadResultPreviewState.Unavailable
                    return@launch
                }
                val bytes = when (design) {
                    DownloadResultDesignType.IMAGE -> renderImagePreview(request)
                    DownloadResultDesignType.FREE_DESIGN -> renderFreeDesignPreview(request)
                    DownloadResultDesignType.MY_DESIGN -> renderCustomDesignPreview(request)
                }
                if (bytes == null) {
                    if (design == DownloadResultDesignType.MY_DESIGN && customDesignId == null) {
                        _previewState.value = DownloadResultPreviewState.ImportYourDesign
                    } else {
                        _previewState.value = DownloadResultPreviewState.Unavailable
                    }
                } else {
                    _previewState.value = DownloadResultPreviewState.ResultImage(bytes)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _previewState.value = DownloadResultPreviewState.Unavailable
            }
        }
    }

    fun selectFreeDesignTemplate(
        tournamentId: String,
        templateId: String,
    ) {
        if (FreeDesignTemplateRegistry.findById(templateId) == null) return
        if (_selectedFreeDesignTemplateId.value == templateId) return

        _selectedFreeDesignTemplateId.value = templateId
        if (selectedDesign == DownloadResultDesignType.FREE_DESIGN) {
            select(
                tournamentId = tournamentId,
                result = selectedResult,
                design = DownloadResultDesignType.FREE_DESIGN,
            )
        }
    }

    fun refreshSelection() {
        val tournamentId = selectedTournamentId ?: return
        select(tournamentId, selectedResult, selectedDesign)
    }

    fun deleteSavedCustomDesign() {
        val savedId = customDesignId ?: return
        if (!_hasSavedCustomDesign.value || deleteJob?.isActive == true) return
        deleteJob = viewModelScope.launch {
            val result = try {
                customDesignDelete.delete(savedId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                CustomDesignDeleteResult.Failed(
                    com.hoggamers.rankforge.data.cloud.CustomDesignDeleteFailure.DATABASE_DELETE,
                )
            }
            if (result is CustomDesignDeleteResult.Success) {
                customDesignId = null
                _hasSavedCustomDesign.value = false
                _previewState.value = DownloadResultPreviewState.ImportYourDesign
            }
        }
    }

    fun requestDownload(
        tournamentId: String,
        result: DownloadResultSelection,
        design: DownloadResultDesignType,
    ) {
        if (downloadState.value.isBusy) return
        downloadJob?.cancel()
        pendingDocument = null
        _downloadState.value = DownloadResultDownloadState.Generating
        downloadJob = viewModelScope.launch {
            val outcome = try {
                val request = buildRequest(tournamentId, result)
                if (request == null) {
                    ResultDownloadExecutionResult.Failure(
                        com.hoggamers.rankforge.data.export.ResultDownloadFailure.INVALID_CONTEXT,
                    )
                } else {
                    when (design) {
                        DownloadResultDesignType.IMAGE ->
                            resultDownloadCoordinator.execute(
                                request = request,
                                format = ResultExportFileFormat.PNG,
                                onSaving = { _downloadState.value = DownloadResultDownloadState.Saving },
                            )
                        DownloadResultDesignType.FREE_DESIGN ->
                            freeDesignResultDownloadCoordinator.execute(
                                request = request,
                                templateId = selectedFreeDesignTemplateId.value,
                                onSaving = { _downloadState.value = DownloadResultDownloadState.Saving },
                            )
                        DownloadResultDesignType.MY_DESIGN ->
                            customDesignId?.let { id ->
                                customDesignResultDownloadCoordinator.execute(
                                    customDesignId = id,
                                    request = request,
                                    onSaving = { _downloadState.value = DownloadResultDownloadState.Saving },
                                )
                            } ?: ResultDownloadExecutionResult.Failure(
                                com.hoggamers.rankforge.data.export.ResultDownloadFailure.INVALID_CONTEXT,
                            )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ResultDownloadExecutionResult.Failure(
                    com.hoggamers.rankforge.data.export.ResultDownloadFailure.GENERATION_FAILED,
                )
            }
            when (outcome) {
                is ResultDownloadExecutionResult.Saved -> {
                    _downloadState.value = DownloadResultDownloadState.Success
                    shareEventsChannel.send(
                        ResultShareRequest(
                            uri = outcome.uri,
                            format = outcome.format,
                            displayName = outcome.displayName,
                        ),
                    )
                }
                is ResultDownloadExecutionResult.UserDestinationRequired -> {
                    pendingDocument = PendingDocument(
                        format = outcome.format,
                        displayName = outcome.displayName,
                        bytes = outcome.bytes,
                    )
                    _downloadState.value = DownloadResultDownloadState.DestinationLaunchRequested(
                        format = outcome.format,
                        suggestedDisplayName = outcome.displayName,
                    )
                }
                is ResultDownloadExecutionResult.Failure ->
                    _downloadState.value = DownloadResultDownloadState.Failure
            }
        }
    }

    fun onDestinationLaunchHandled() {
        if (_downloadState.value is DownloadResultDownloadState.DestinationLaunchRequested) {
            _downloadState.value = DownloadResultDownloadState.WaitingForDestination
        }
    }

    fun onDestinationResult(uri: Uri?) {
        val document = pendingDocument ?: return
        pendingDocument = null
        viewModelScope.launch {
            _downloadState.value = DownloadResultDownloadState.Saving
            when (val writeResult = resultDocumentWriter.write(uri, document.bytes)) {
                is ResultDocumentWriteResult.Success -> {
                    _downloadState.value = DownloadResultDownloadState.Success
                    shareEventsChannel.send(
                        ResultShareRequest(
                            uri = writeResult.uri,
                            format = document.format,
                            displayName = document.displayName,
                        ),
                    )
                }
                is ResultDocumentWriteResult.Failure -> {
                    _downloadState.value = DownloadResultDownloadState.Failure
                }
            }
        }
    }

    private suspend fun buildRequest(
        tournamentId: String,
        result: DownloadResultSelection,
    ): ResultDownloadRequest? {
        val tournament = getTournamentById(tournamentId).first() ?: return null
        val matches = observeMatches(tournamentId).first()
        val teamSlots = observeTournamentSlots(tournamentId).first()
        val rosterPlayers = observeRoster(tournamentId).first().values.flatten()
        return when (result) {
            DownloadResultSelection.Overall -> ResultDownloadRequest.WholeTournament(
                TournamentCsvExportInput(
                    tournament = tournament,
                    matches = matches,
                    teamSlots = teamSlots,
                    rosterPlayers = rosterPlayers,
                ),
            )
            is DownloadResultSelection.Match -> matches.firstOrNull { it.id == result.matchId }
                ?.let { match ->
                    ResultDownloadRequest.CurrentMatch(
                        MatchCsvExportInput(
                            tournament = tournament,
                            match = match,
                            teamSlots = teamSlots,
                            rosterPlayers = rosterPlayers,
                        ),
                    )
                }
        }
    }

    private suspend fun renderImagePreview(request: ResultDownloadRequest): ByteArray? =
        withContext(Dispatchers.Default) {
            val builder = ResultExportModelBuilder()
            val renderer = ResultPngRenderer()
            when (request) {
                is ResultDownloadRequest.CurrentMatch ->
                    when (val result = builder.buildMatch(request.input)) {
                        is MatchResultExportModelBuildResult.Success ->
                            (renderer.render(result.model) as? ResultPngRenderResult.Success)?.pngBytes
                        is MatchResultExportModelBuildResult.Failure -> null
                    }
                is ResultDownloadRequest.WholeTournament ->
                    when (val result = builder.buildTournament(request.input)) {
                        is TournamentResultExportModelBuildResult.Success ->
                            (renderer.render(result.model) as? ResultPngRenderResult.Success)?.pngBytes
                        is TournamentResultExportModelBuildResult.Failure -> null
                    }
            }
        }

    private suspend fun renderFreeDesignPreview(request: ResultDownloadRequest): ByteArray? =
        withContext(Dispatchers.Default) {
            val template = FreeDesignTemplateRegistry.findById(
                selectedFreeDesignTemplateId.value,
            ) ?: return@withContext null
            val builder = ResultExportModelBuilder()
            val bitmap = when (request) {
                is ResultDownloadRequest.CurrentMatch ->
                    when (val result = builder.buildMatch(request.input)) {
                        is MatchResultExportModelBuildResult.Success ->
                            (freeDesignBitmapComposer.compose(result.model, template) as? FreeDesignBitmapComposeResult.Success)?.bitmap
                        is MatchResultExportModelBuildResult.Failure -> null
                    }
                is ResultDownloadRequest.WholeTournament ->
                    when (val result = builder.buildTournament(request.input)) {
                        is TournamentResultExportModelBuildResult.Success ->
                            (freeDesignBitmapComposer.compose(result.model, template) as? FreeDesignBitmapComposeResult.Success)?.bitmap
                        is TournamentResultExportModelBuildResult.Failure -> null
                    }
            } ?: return@withContext null
            try {
                val output = ByteArrayOutputStream()
                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    output.toByteArray().takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            } finally {
                bitmap.recycle()
            }
        }

    private suspend fun renderCustomDesignPreview(request: ResultDownloadRequest): ByteArray? {
        val savedId = when (val result = customDesignSavedIdDiscovery.find()) {
            is CustomDesignSavedIdDiscoveryResult.Found -> result.customDesignId.also {
                _hasSavedCustomDesign.value = true
            }
            CustomDesignSavedIdDiscoveryResult.None,
            CustomDesignSavedIdDiscoveryResult.Ambiguous,
            is CustomDesignSavedIdDiscoveryResult.Failed,
            -> {
                _hasSavedCustomDesign.value = false
                return null
            }
        }
        val design = when (val result = customDesignRestore.restore(savedId)) {
            is CustomDesignRestoreResult.Success -> result.design
            is CustomDesignRestoreResult.Failed -> return null
        }
        customDesignId = savedId
        return withContext(Dispatchers.Default) {
            val rows = when (val result = customDesignRowsResolver.resolve(request)) {
                is CustomDesignResultRowsResult.Success -> result.rows
                is CustomDesignResultRowsResult.MatchFailure,
                is CustomDesignResultRowsResult.TournamentFailure,
                -> return@withContext null
            }
            val bitmap = when (
                val result = customDesignBitmapComposer.compose(
                    imageReference = design.localImageReference,
                    rows = rows,
                    geometry = design.geometry,
                    textColors = design.textColors,
                    averageRankingBoundingBoxHeightPx = design.averageRankingBoundingBoxHeightPx,
                )
            ) {
                is CustomDesignBitmapComposeResult.Success -> result.bitmap
                is CustomDesignBitmapComposeResult.Failure -> return@withContext null
            }
            try {
                val output = ByteArrayOutputStream()
                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    output.toByteArray().takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    private data class PendingDocument(
        val format: ResultExportFileFormat,
        val displayName: String,
        val bytes: ByteArray,
    )
}

@Composable
fun DownloadResultRoute(
    tournamentId: String,
    sourceMatchId: String? = null,
    initialDesign: DownloadResultDesignType = DownloadResultDesignType.IMAGE,
    initialResult: DownloadResultSelection = DownloadResultSelection.Overall,
    onBack: () -> Unit,
    onOpenCustomDesignSetup: (String, ResultDownloadScope) -> Unit = { _, _ -> },
    viewModel: DownloadResultViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val hasSavedCustomDesign by viewModel.hasSavedCustomDesign.collectAsStateWithLifecycle()
    val selectedFreeDesignTemplateId by viewModel.selectedFreeDesignTemplateId.collectAsStateWithLifecycle()
    ResultShareEventEffect(shareEvents = viewModel.shareEvents)
    val lifecycleOwner = LocalLifecycleOwner.current
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
        onResult = viewModel::onDestinationResult,
    )

    LaunchedEffect(downloadState) {
        val requested = downloadState as? DownloadResultDownloadState.DestinationLaunchRequested
            ?: return@LaunchedEffect
        viewModel.onDestinationLaunchHandled()
        documentLauncher.launch(requested.suggestedDisplayName)
    }

    DisposableEffect(lifecycleOwner, tournamentId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSelection()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DownloadResultScreen(
        matches = uiState.matches,
        freeDesignOptions = uiState.freeDesignOptions,
        onBack = onBack,
        initialDesign = initialDesign,
        initialResult = initialResult,
        selectedFreeDesignTemplateId = selectedFreeDesignTemplateId,
        previewState = previewState,
        downloadState = downloadState,
        hasSavedCustomDesign = hasSavedCustomDesign,
        onResultSelected = { selection, design ->
            viewModel.select(tournamentId, selection, design)
        },
        onDesignSelected = { selection, design ->
            viewModel.select(tournamentId, selection, design)
        },
        onFreeDesignTemplateSelected = { templateId ->
            viewModel.selectFreeDesignTemplate(tournamentId, templateId)
        },
        onImportYourDesign = { selection ->
            val matchId = (selection as? DownloadResultSelection.Match)?.matchId ?: sourceMatchId
            if (matchId != null) {
                onOpenCustomDesignSetup(matchId, selection.exportScope)
            }
        },
        onDeleteSavedCustomDesign = viewModel::deleteSavedCustomDesign,
        onDownload = { selection, design ->
            viewModel.requestDownload(tournamentId, selection, design)
        },
    )
}

@Composable
fun DownloadResultScreen(
    matches: List<DownloadResultMatchOption>,
    onBack: () -> Unit,
    freeDesignOptions: List<DownloadResultFreeDesignOption> =
        FreeDesignTemplateRegistry.all.map { template ->
            DownloadResultFreeDesignOption(
                id = template.id,
                displayName = template.displayName,
            )
        },
    initialDesign: DownloadResultDesignType = DownloadResultDesignType.IMAGE,
    initialResult: DownloadResultSelection = DownloadResultSelection.Overall,
    selectedFreeDesignTemplateId: String = FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
    previewState: DownloadResultPreviewState = DownloadResultPreviewState.Idle,
    downloadState: DownloadResultDownloadState = DownloadResultDownloadState.Idle,
    hasSavedCustomDesign: Boolean = false,
    onResultSelected: (DownloadResultSelection, DownloadResultDesignType) -> Unit = { _, _ -> },
    onDesignSelected: (DownloadResultSelection, DownloadResultDesignType) -> Unit = { _, _ -> },
    onFreeDesignTemplateSelected: (String) -> Unit = {},
    onImportYourDesign: (DownloadResultSelection) -> Unit = {},
    onDeleteSavedCustomDesign: () -> Unit = {},
    onDownload: (DownloadResultSelection, DownloadResultDesignType) -> Unit = { _, _ -> },
) {
    var selectedResult by remember(initialResult) {
        mutableStateOf(initialResult)
    }
    var selectedDesign by remember(initialDesign) { mutableStateOf(initialDesign) }
    var selectedTemplateId by remember(selectedFreeDesignTemplateId) {
        mutableStateOf(selectedFreeDesignTemplateId)
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val orderedMatches = remember(matches) { matches.sortedBy { it.matchNumber } }

    LaunchedEffect(initialResult, initialDesign) {
        onResultSelected(initialResult, initialDesign)
        onDesignSelected(initialResult, initialDesign)
    }

    LaunchedEffect(orderedMatches) {
        val selectedMatch = selectedResult as? DownloadResultSelection.Match
        if (selectedMatch != null && orderedMatches.none { it.matchId == selectedMatch.matchId }) {
            selectedResult = DownloadResultSelection.Overall
            onResultSelected(DownloadResultSelection.Overall, selectedDesign)
        }
    }

    BackHandler(onBack = onBack)
    PointIqHomeSystemBars()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointIqHomeBackground()
            .padding(start = 24.dp, top = 28.dp, end = 24.dp)
            .testTag(DOWNLOAD_RESULT_SCREEN_TEST_TAG),
    ) {
        PointIqPageHeader(
            title = stringResource(R.string.match_review_download_result_action),
            onBack = onBack,
            backTestTag = DOWNLOAD_RESULT_SCREEN_TEST_TAG + "_back",
        )

        Spacer(modifier = Modifier.height(24.dp))

        DownloadResultSectionTitle(text = "Result")

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PointIqSelectionChip(
                label = "Overall",
                selected = selectedResult == DownloadResultSelection.Overall,
                onClick = {
                    val selection = DownloadResultSelection.Overall
                    selectedResult = selection
                    onResultSelected(selection, selectedDesign)
                },
                testTag = DOWNLOAD_RESULT_OVERALL_OPTION_TEST_TAG,
            )
            orderedMatches.forEach { match ->
                PointIqSelectionChip(
                    label = "Match ${match.matchNumber}",
                    selected = selectedResult == DownloadResultSelection.Match(match.matchId),
                    onClick = {
                        val selection = DownloadResultSelection.Match(match.matchId)
                        selectedResult = selection
                        onResultSelected(selection, selectedDesign)
                    },
                    testTag = DOWNLOAD_RESULT_MATCH_OPTION_TEST_TAG_PREFIX + match.matchNumber,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        DownloadResultSectionTitle(text = "Design")

        Spacer(modifier = Modifier.height(12.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val designRowModifier = if (maxWidth < 300.dp) {
                Modifier.horizontalScroll(rememberScrollState())
            } else {
                Modifier
            }
            Row(
                modifier = designRowModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PointIqSelectionChip(
                    label = "Image",
                    selected = selectedDesign == DownloadResultDesignType.IMAGE,
                    onClick = {
                        val design = DownloadResultDesignType.IMAGE
                        selectedDesign = design
                        onDesignSelected(selectedResult, design)
                    },
                    testTag = DOWNLOAD_RESULT_DESIGN_IMAGE_OPTION_TEST_TAG,
                )
                PointIqSelectionChip(
                    label = "Free Design",
                    selected = selectedDesign == DownloadResultDesignType.FREE_DESIGN,
                    onClick = {
                        val design = DownloadResultDesignType.FREE_DESIGN
                        selectedDesign = design
                        onDesignSelected(selectedResult, design)
                    },
                    testTag = DOWNLOAD_RESULT_DESIGN_FREE_OPTION_TEST_TAG,
                )
                PointIqSelectionChip(
                    label = "My Design",
                    selected = selectedDesign == DownloadResultDesignType.MY_DESIGN,
                    onClick = {
                        val design = DownloadResultDesignType.MY_DESIGN
                        selectedDesign = design
                        onDesignSelected(selectedResult, design)
                    },
                    testTag = DOWNLOAD_RESULT_DESIGN_MY_OPTION_TEST_TAG,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            when (selectedDesign) {
                DownloadResultDesignType.IMAGE,
                DownloadResultDesignType.FREE_DESIGN,
                DownloadResultDesignType.MY_DESIGN,
                -> when (previewState) {
                    is DownloadResultPreviewState.ResultImage ->
                        DownloadResultBitmapPreview(
                            pngBytes = previewState.pngBytes,
                            modifier = Modifier.fillMaxSize(),
                            showDeleteControl = selectedDesign == DownloadResultDesignType.MY_DESIGN &&
                                hasSavedCustomDesign,
                            onDeleteClick = { showDeleteConfirmation = true },
                        )
                    DownloadResultPreviewState.ImportYourDesign ->
                        Button(
                            onClick = { onImportYourDesign(selectedResult) },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DownloadResultSelectedChipBorder),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DownloadResultSelectedChipBackground,
                                contentColor = DownloadResultSelectedChipText,
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 24.dp,
                                vertical = 0.dp,
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                disabledElevation = 0.dp,
                            ),
                        ) {
                            Text(
                                text = "Import Your Design",
                                color = DownloadResultSelectedChipText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    DownloadResultPreviewState.Idle,
                    DownloadResultPreviewState.Unavailable,
                    -> Unit
                    DownloadResultPreviewState.Loading -> if (
                        selectedDesign == DownloadResultDesignType.MY_DESIGN ||
                            selectedDesign == DownloadResultDesignType.FREE_DESIGN
                    ) {
                        DownloadResultStandingsShimmerPreview(modifier = Modifier.fillMaxSize())
                    }
                }
            }

        }

        if (selectedDesign == DownloadResultDesignType.FREE_DESIGN && freeDesignOptions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            DownloadResultSectionTitle(text = "Template")

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                freeDesignOptions.forEach { option ->
                    FreeDesignTemplateRegistry.findById(option.id)?.let { template ->
                        FreeDesignTemplateThumbnail(
                            label = option.displayName,
                            assetPath = template.assetPath,
                            selected = selectedTemplateId == option.id,
                            onClick = {
                                selectedTemplateId = option.id
                                onFreeDesignTemplateSelected(option.id)
                            },
                            testTag = DOWNLOAD_RESULT_FREE_TEMPLATE_OPTION_TEST_TAG_PREFIX + option.id,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        DownloadResultPrimaryButton(
            enabled = previewState is DownloadResultPreviewState.ResultImage &&
                !downloadState.isBusy,
            onClick = { onDownload(selectedResult, selectedDesign) },
        )
    }

    if (showDeleteConfirmation && selectedDesign == DownloadResultDesignType.MY_DESIGN &&
        hasSavedCustomDesign
    ) {
        PointIqConfirmationDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = "Delete custom design?",
            message = "This will remove your saved custom design.",
            onDismiss = { showDeleteConfirmation = false },
            dismissLabel = "Cancel",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                showDeleteConfirmation = false
                onDeleteSavedCustomDesign()
            },
        )
    }
}

@Composable
private fun DownloadResultBitmapPreview(
    pngBytes: ByteArray,
    modifier: Modifier,
    showDeleteControl: Boolean = false,
    onDeleteClick: () -> Unit = {},
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, pngBytes) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
        }
    }
    val previewBitmap = bitmap ?: return
    DisposableEffect(previewBitmap) {
            onDispose {
                if (!previewBitmap.isRecycled) previewBitmap.recycle()
            }
        }
    if (showDeleteControl) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            val imageAspectRatio = previewBitmap.width.toFloat() / previewBitmap.height.toFloat()
            val imageContainerModifier = if (imageAspectRatio >= 1f) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspectRatio)
            } else {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(imageAspectRatio)
            }
            Box(
                modifier = imageContainerModifier,
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                val deleteShape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .zIndex(1f)
                        .size(30.dp)
                        .clip(deleteShape)
                        .background(DownloadResultUnselectedChipBackground)
                        .border(
                            width = 1.dp,
                            color = DownloadResultUnselectedChipText.copy(alpha = 0.65f),
                            shape = deleteShape,
                        )
                        .clickable(onClick = onDeleteClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete saved custom design",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    } else {
        Image(
            bitmap = previewBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}

@Composable
private fun DownloadResultStandingsShimmerPreview(
    modifier: Modifier = Modifier,
) {
    val shimmerTransition = rememberInfiniteTransition(label = "download result preview skeleton")
    val shimmerProgress by shimmerTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
        label = "download result preview shimmer progress",
    )

    Column(
        modifier = modifier
            .background(DownloadResultPreviewSurface)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DownloadResultShimmerBlock(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .height(14.dp),
            shimmerProgress = shimmerProgress,
            cornerRadius = 7.dp,
        )
        DownloadResultShimmerBlock(
            modifier = Modifier
                .fillMaxWidth(0.3f)
                .height(10.dp),
            shimmerProgress = shimmerProgress,
            cornerRadius = 5.dp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DownloadResultShimmerBlock(
                    modifier = Modifier.size(22.dp),
                    shimmerProgress = shimmerProgress,
                    cornerRadius = 5.dp,
                )
                DownloadResultShimmerBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp),
                    shimmerProgress = shimmerProgress,
                    cornerRadius = 6.dp,
                )
                repeat(3) {
                    DownloadResultShimmerBlock(
                        modifier = Modifier
                            .size(width = 20.dp, height = 12.dp),
                        shimmerProgress = shimmerProgress,
                        cornerRadius = 5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadResultShimmerBlock(
    modifier: Modifier,
    shimmerProgress: Float,
    cornerRadius: Dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .drawWithCache {
                val shimmerWidth = size.width * 0.45f
                val shimmerStart = shimmerProgress * (size.width + shimmerWidth) - shimmerWidth
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        DownloadResultPreviewSkeletonBase,
                        DownloadResultPreviewSkeletonHighlight,
                        DownloadResultPreviewSkeletonBase,
                    ),
                    start = Offset(shimmerStart, 0f),
                    end = Offset(shimmerStart + shimmerWidth, 0f),
                )
                onDrawBehind {
                    drawRect(shimmerBrush)
                }
            },
    )
}

private val DownloadResultCtaTopBlue = Color(0xFF159CF8)
private val DownloadResultCtaMiddleBlue = Color(0xFF1688F7)
private val DownloadResultCtaDeepBlue = Color(0xFF1675F0)
private val DownloadResultCtaBorder = Color(0xFF4AAFF7)

@Composable
private fun DownloadResultPrimaryButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = shape,
        border = BorderStroke(1.dp, DownloadResultCtaBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to DownloadResultCtaTopBlue,
                            0.52f to DownloadResultCtaMiddleBlue,
                            1f to DownloadResultCtaDeepBlue,
                        ),
                    ),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Download",
                color = Color(0xFFF6F8FF),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DownloadResultSectionTitle(
    text: String,
) {
    Text(
        text = text,
        color = DownloadResultSectionTitleColor,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun FreeDesignTemplateThumbnail(
    label: String,
    assetPath: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val context = LocalContext.current
    val thumbnailBitmap by produceState<Bitmap?>(initialValue = null, assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).use { input ->
                    BitmapFactory.decodeStream(
                        input,
                        null,
                        BitmapFactory.Options().apply { inSampleSize = 4 },
                    )
                }
            }.getOrNull()
        }
    }
    val aspectRatio = thumbnailBitmap?.let { bitmap ->
        bitmap.width.toFloat() / bitmap.height.toFloat()
    } ?: 1f
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .height(86.dp)
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(
                color = if (selected) {
                    DownloadResultSelectedChipBackground
                } else {
                    DownloadResultUnselectedChipBackground
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    DownloadResultSelectedChipBorder
                } else {
                    DownloadResultUnselectedChipBorder
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
                this.selected = selected
            }
            .testTag(testTag)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        thumbnailBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PointIqSelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                color = if (selected) {
                    DownloadResultSelectedChipBackground
                } else {
                    DownloadResultUnselectedChipBackground
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    DownloadResultSelectedChipBorder
                } else {
                    DownloadResultUnselectedChipBorder
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) {
                DownloadResultSelectedChipText
            } else {
                DownloadResultUnselectedChipText
            },
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
