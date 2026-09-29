package com.hoggamers.rankforge.presentation.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.hoggamers.rankforge.data.export.PointTableLogoRenderData
import com.hoggamers.rankforge.data.export.PointTableLogoRenderResolver
import com.hoggamers.rankforge.data.export.ResultDocumentWriteResult
import com.hoggamers.rankforge.data.export.ResultDocumentWriter
import com.hoggamers.rankforge.data.export.ResultDownloadCoordinator
import com.hoggamers.rankforge.data.export.ResultDownloadExecutionResult
import com.hoggamers.rankforge.data.export.ResultDownloadScope
import com.hoggamers.rankforge.data.export.ResultExportFileFormat
import com.hoggamers.rankforge.data.export.ResultPngRenderResult
import com.hoggamers.rankforge.data.export.ResultPngRenderer
import com.hoggamers.rankforge.data.export.ResultDownloadRequest
import com.hoggamers.rankforge.data.local.PointTableDetails
import com.hoggamers.rankforge.data.local.PointTableDetailsRepository
import com.hoggamers.rankforge.data.local.PointTableLogoCandidate
import com.hoggamers.rankforge.data.local.PointTableLogoImageStore
import com.hoggamers.rankforge.data.local.PointTableLogoImageStoreResult
import com.hoggamers.rankforge.data.local.PointTableLogoPlacement
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
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
private val PointTableDetailsDialogSurface = Color(0xFF071B3E)
private val PointTableDetailsDialogBorder = Color(0xFF176AF7)
private val PointTableDetailsDialogTitle = Color(0xFFF6F8FF)
private val PointTableDetailsDialogBody = Color(0xFF91AFE0)
private val PointTableDetailsDialogAction = Color(0xFF17C9F2)
private val PointTableDetailsDialogSecondaryAction = Color(0xFF91AFE0)
private val PointTableDetailsDialogFieldInactive = Color(0xFF7D9DCE)
private val PointTableDetailsDateFormatter = DateTimeFormatter.ofPattern(
    "dd MMM yyyy",
    Locale.ENGLISH,
)

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

data class PointTableDetailsUiState(
    val organizationName: String = "",
    val date: LocalDate? = null,
    val organizationLogoPath: String? = null,
)

private data class PointTableLogoPickerRequest(
    val designKey: String,
    val previewPngBytes: ByteArray,
)

private fun PointTableDetails.toUiState(): PointTableDetailsUiState = PointTableDetailsUiState(
    organizationName = organizationName,
    date = displayDate,
    organizationLogoPath = organizationLogoPath,
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

data class DownloadResultSelectionContext(
    val tournamentId: String? = null,
    val result: DownloadResultSelection = DownloadResultSelection.Overall,
    val design: DownloadResultDesignType = DownloadResultDesignType.IMAGE,
    val freeDesignTemplateId: String = FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
)

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
    private val pointTableDetailsRepository: PointTableDetailsRepository,
    private val pointTableLogoImageStore: PointTableLogoImageStore,
    private val pointTableLogoRenderResolver: PointTableLogoRenderResolver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadResultUiState())
    val uiState: StateFlow<DownloadResultUiState> = _uiState.asStateFlow()

    private val _selectionContext = MutableStateFlow(DownloadResultSelectionContext())
    val selectionContext: StateFlow<DownloadResultSelectionContext> =
        _selectionContext.asStateFlow()

    private val _previewState = MutableStateFlow<DownloadResultPreviewState>(DownloadResultPreviewState.Idle)
    val previewState: StateFlow<DownloadResultPreviewState> = _previewState.asStateFlow()

    private val _hasSavedCustomDesign = MutableStateFlow(false)
    val hasSavedCustomDesign: StateFlow<Boolean> = _hasSavedCustomDesign.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadResultDownloadState>(DownloadResultDownloadState.Idle)
    val downloadState: StateFlow<DownloadResultDownloadState> = _downloadState.asStateFlow()
    private val _pointTableDetails = MutableStateFlow(PointTableDetailsUiState())
    val pointTableDetails: StateFlow<PointTableDetailsUiState> = _pointTableDetails.asStateFlow()
    private val _organizationLogoDisplayUri = MutableStateFlow<String?>(null)
    val organizationLogoDisplayUri: StateFlow<String?> = _organizationLogoDisplayUri.asStateFlow()
    private val _logoEditorState = MutableStateFlow<PointTableLogoEditorState?>(null)
    val logoEditorState: StateFlow<PointTableLogoEditorState?> = _logoEditorState.asStateFlow()
    private val _logoOperationError = MutableStateFlow<String?>(null)
    val logoOperationError: StateFlow<String?> = _logoOperationError.asStateFlow()
    private val _logoSaveInProgress = MutableStateFlow(false)
    val logoSaveInProgress: StateFlow<Boolean> = _logoSaveInProgress.asStateFlow()
    private val shareEventsChannel = Channel<ResultShareRequest>(Channel.BUFFERED)
    val shareEvents: Flow<ResultShareRequest> = shareEventsChannel.receiveAsFlow()

    private var loadedTournamentId: String? = null
    private var customDesignId: String? = null
    private var previewJob: kotlinx.coroutines.Job? = null
    private var downloadJob: kotlinx.coroutines.Job? = null
    private var deleteJob: kotlinx.coroutines.Job? = null
    private var pointTableDetailsLoadJob: kotlinx.coroutines.Job? = null
    private var pendingDocument: PendingDocument? = null

    private val selectedTournamentId: String?
        get() = _selectionContext.value.tournamentId
    private val selectedResult: DownloadResultSelection
        get() = _selectionContext.value.result
    private val selectedDesign: DownloadResultDesignType
        get() = _selectionContext.value.design

    fun load(tournamentId: String) {
        if (loadedTournamentId == tournamentId) return
        loadedTournamentId = tournamentId
        _pointTableDetails.value = PointTableDetailsUiState()
        _organizationLogoDisplayUri.value = null
        _logoOperationError.value = null
        _logoEditorState.value = null
        pointTableDetailsLoadJob?.cancel()
        pointTableDetailsLoadJob = viewModelScope.launch {
            val details = pointTableDetailsRepository.getPointTableDetails(tournamentId)
            if (loadedTournamentId == tournamentId) {
                _pointTableDetails.value = details?.toUiState() ?: PointTableDetailsUiState()
                _organizationLogoDisplayUri.value = details?.organizationLogoPath
                    ?.let(pointTableLogoImageStore::displayUriOrNull)
                if (
                    selectedTournamentId == tournamentId &&
                    (
                        selectedDesign == DownloadResultDesignType.IMAGE ||
                            selectedDesign == DownloadResultDesignType.FREE_DESIGN
                        )
                ) {
                    select(tournamentId, selectedResult, selectedDesign)
                }
            }
        }
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

    fun savePointTableDetails(
        tournamentId: String,
        details: PointTableDetailsUiState,
    ) {
        if (loadedTournamentId != tournamentId) return
        val normalized = details.copy(
            organizationName = details.organizationName.trim(),
            organizationLogoPath = details.organizationLogoPath
                ?: _pointTableDetails.value.organizationLogoPath,
        )
        _pointTableDetails.value = normalized
        viewModelScope.launch {
            pointTableDetailsRepository.savePointTableDetails(
                PointTableDetails(
                    tournamentId = tournamentId,
                    organizationName = normalized.organizationName,
                    displayDate = normalized.date,
                    organizationLogoPath = normalized.organizationLogoPath,
                ),
            )
            if (
                selectedTournamentId == tournamentId &&
                (
                    selectedDesign == DownloadResultDesignType.IMAGE ||
                        selectedDesign == DownloadResultDesignType.FREE_DESIGN
                    )
            ) {
                select(tournamentId, selectedResult, selectedDesign)
            }
        }
    }

    fun prepareOrganizationLogoCandidate(
        tournamentId: String,
        designKey: String,
        previewPngBytes: ByteArray,
        selectedUri: String,
    ) {
        if (loadedTournamentId != tournamentId || designKey.isBlank() || previewPngBytes.isEmpty()) return
        _logoOperationError.value = null
        viewModelScope.launch {
            val result = try {
                pointTableLogoImageStore.prepareCandidate(tournamentId, selectedUri)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                PointTableLogoImageStoreResult.Failed
            }
            when (result) {
                is PointTableLogoImageStoreResult.Preserved -> {
                    val candidate = PointTableLogoCandidate(
                        tournamentId = tournamentId,
                        localRelativePath = result.localRelativePath,
                        displayUri = result.displayUri,
                    )
                    _logoEditorState.value = PointTableLogoEditorState(
                        tournamentId = tournamentId,
                        designKey = designKey,
                        previewPngBytes = previewPngBytes.copyOf(),
                        logoDisplayUri = result.displayUri,
                        previousLogoPath = _pointTableDetails.value.organizationLogoPath,
                        candidate = candidate,
                        initialPlacement = pointTableDetailsRepository
                            .getPointTableLogoPlacement(tournamentId, designKey),
                    )
                }
                PointTableLogoImageStoreResult.Failed -> {
                    _logoOperationError.value = "Unable to load the selected logo."
                }
            }
        }
    }

    fun openOrganizationLogoEditor(
        tournamentId: String,
        designKey: String,
        previewPngBytes: ByteArray,
    ) {
        if (loadedTournamentId != tournamentId || designKey.isBlank() || previewPngBytes.isEmpty()) return
        val logoPath = _pointTableDetails.value.organizationLogoPath ?: run {
            _logoOperationError.value = "No saved logo is available."
            return
        }
        val displayUri = pointTableLogoImageStore.displayUriOrNull(logoPath) ?: run {
            _logoOperationError.value = "The saved logo is unavailable."
            return
        }
        _logoOperationError.value = null
        viewModelScope.launch {
            _logoEditorState.value = PointTableLogoEditorState(
                tournamentId = tournamentId,
                designKey = designKey,
                previewPngBytes = previewPngBytes.copyOf(),
                logoDisplayUri = displayUri,
                previousLogoPath = logoPath,
                candidate = null,
                initialPlacement = pointTableDetailsRepository
                    .getPointTableLogoPlacement(tournamentId, designKey),
            )
        }
    }

    fun cancelOrganizationLogoEditor() {
        val candidate = _logoEditorState.value?.candidate
        _logoEditorState.value = null
        _logoOperationError.value = null
        if (candidate != null) {
            viewModelScope.launch { pointTableLogoImageStore.discardCandidate(candidate) }
        }
    }

    fun saveOrganizationLogoPlacement(geometry: PointTableLogoPlacementGeometry) {
        val editor = _logoEditorState.value ?: return
        if (_logoSaveInProgress.value) return
        _logoSaveInProgress.value = true
        _logoOperationError.value = null
        viewModelScope.launch {
            try {
                val placement = PointTableLogoPlacement(
                    tournamentId = editor.tournamentId,
                    designKey = editor.designKey,
                    centerXRatio = geometry.centerXRatio,
                    centerYRatio = geometry.centerYRatio,
                    widthRatio = geometry.widthRatio,
                )
                if (!placement.isValid()) {
                    _logoOperationError.value = "The logo placement is invalid."
                    return@launch
                }
                if (editor.candidate != null) {
                    val committed = when (
                        val result = pointTableLogoImageStore.commitCandidate(editor.candidate)
                    ) {
                        is PointTableLogoImageStoreResult.Preserved -> result
                        PointTableLogoImageStoreResult.Failed -> {
                            _logoOperationError.value = "Unable to save the selected logo."
                            return@launch
                        }
                    }
                    val persisted = pointTableDetailsRepository.savePointTableLogoConfiguration(
                        tournamentId = editor.tournamentId,
                        organizationLogoPath = committed.localRelativePath,
                        placement = placement,
                        clearExistingPlacements = true,
                    )
                    if (!persisted) {
                        pointTableLogoImageStore.deleteLogo(committed.localRelativePath)
                        _logoOperationError.value = "Unable to save the selected logo."
                        return@launch
                    }
                    editor.previousLogoPath?.let { pointTableLogoImageStore.deleteLogo(it) }
                    _pointTableDetails.value = _pointTableDetails.value.copy(
                        organizationLogoPath = committed.localRelativePath,
                    )
                    _organizationLogoDisplayUri.value = committed.displayUri
                } else {
                    pointTableDetailsRepository.savePointTableLogoPlacement(placement)
                }
                _logoEditorState.value = null
                refreshSupportedSelectionIfActive(editor.tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _logoOperationError.value = "Unable to save the logo placement."
            } finally {
                _logoSaveInProgress.value = false
            }
        }
    }

    fun removeOrganizationLogo(tournamentId: String) {
        if (loadedTournamentId != tournamentId) return
        val previousPath = _pointTableDetails.value.organizationLogoPath
        _logoOperationError.value = null
        viewModelScope.launch {
            val removed = try {
                pointTableDetailsRepository.removePointTableLogoConfiguration(tournamentId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                false
            }
            if (!removed) {
                _logoOperationError.value = "Unable to remove the logo."
                return@launch
            }
            _pointTableDetails.value = _pointTableDetails.value.copy(organizationLogoPath = null)
            _organizationLogoDisplayUri.value = null
            pointTableLogoImageStore.cleanup(tournamentId)
            previousPath?.let { pointTableLogoImageStore.deleteLogo(it) }
            refreshSupportedSelectionIfActive(tournamentId)
        }
    }

    fun select(
        tournamentId: String,
        result: DownloadResultSelection,
        design: DownloadResultDesignType,
    ) {
        _selectionContext.value = _selectionContext.value.copy(
            tournamentId = tournamentId,
            result = result,
            design = design,
        )
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
                    DownloadResultDesignType.IMAGE -> renderImagePreview(tournamentId, request)
                    DownloadResultDesignType.FREE_DESIGN ->
                        renderFreeDesignPreview(tournamentId, request)
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
        if (_selectionContext.value.freeDesignTemplateId == templateId) return

        _selectionContext.value = _selectionContext.value.copy(
            tournamentId = tournamentId,
            freeDesignTemplateId = templateId,
        )
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
                    val logoRenderData = when (design) {
                        DownloadResultDesignType.IMAGE -> resolveLogoRenderData(
                            tournamentId = tournamentId,
                            design = design,
                        )
                        DownloadResultDesignType.FREE_DESIGN -> resolveLogoRenderData(
                            tournamentId = tournamentId,
                            design = design,
                        )
                        DownloadResultDesignType.MY_DESIGN -> null
                    }
                    try {
                        when (design) {
                            DownloadResultDesignType.IMAGE ->
                                resultDownloadCoordinator.executeImage(
                                    request = request,
                                    displayDate = pointTableDetails.value.date,
                                    logoRenderData = logoRenderData,
                                    onSaving = {
                                        _downloadState.value = DownloadResultDownloadState.Saving
                                    },
                                )
                            DownloadResultDesignType.FREE_DESIGN ->
                                freeDesignResultDownloadCoordinator.executeWithLogo(
                                    request = request,
                                    logoRenderData = logoRenderData,
                                    templateId = _selectionContext.value.freeDesignTemplateId,
                                    onSaving = {
                                        _downloadState.value = DownloadResultDownloadState.Saving
                                    },
                                    displayDate = pointTableDetails.value.date,
                                    organizationName = pointTableDetails.value.organizationName.trim(),
                                )
                            DownloadResultDesignType.MY_DESIGN ->
                                customDesignId?.let { id ->
                                    customDesignResultDownloadCoordinator.execute(
                                        customDesignId = id,
                                        request = request,
                                        onSaving = {
                                            _downloadState.value = DownloadResultDownloadState.Saving
                                        },
                                    )
                                } ?: ResultDownloadExecutionResult.Failure(
                                    com.hoggamers.rankforge.data.export.ResultDownloadFailure.INVALID_CONTEXT,
                                )
                        }
                    } finally {
                        logoRenderData?.bitmap?.let { bitmap ->
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
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

    private suspend fun renderImagePreview(
        tournamentId: String,
        request: ResultDownloadRequest,
    ): ByteArray? {
        val logoRenderData = resolveLogoRenderData(
            tournamentId = tournamentId,
            design = DownloadResultDesignType.IMAGE,
        )
        return try {
            withContext(Dispatchers.Default) {
            val builder = ResultExportModelBuilder()
            val renderer = ResultPngRenderer()
            when (request) {
                is ResultDownloadRequest.CurrentMatch ->
                    when (val result = builder.buildMatch(request.input)) {
                        is MatchResultExportModelBuildResult.Success ->
                            (renderer.render(
                                result.model,
                                pointTableDetails.value.date,
                                logoRenderData,
                            ) as? ResultPngRenderResult.Success)?.pngBytes
                        is MatchResultExportModelBuildResult.Failure -> null
                    }
                is ResultDownloadRequest.WholeTournament ->
                    when (val result = builder.buildTournament(request.input)) {
                        is TournamentResultExportModelBuildResult.Success ->
                            (renderer.render(
                                result.model,
                                pointTableDetails.value.date,
                                logoRenderData,
                            ) as? ResultPngRenderResult.Success)?.pngBytes
                        is TournamentResultExportModelBuildResult.Failure -> null
                    }
            }
            }
        } finally {
            logoRenderData?.bitmap?.let { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private suspend fun renderFreeDesignPreview(
        tournamentId: String,
        request: ResultDownloadRequest,
    ): ByteArray? = withContext(Dispatchers.Default) {
            val template = FreeDesignTemplateRegistry.findById(
                _selectionContext.value.freeDesignTemplateId,
            ) ?: return@withContext null
            val logoRenderData = resolveLogoRenderData(
                tournamentId = tournamentId,
                design = DownloadResultDesignType.FREE_DESIGN,
            )
            val builder = ResultExportModelBuilder()
            try {
                val bitmap = when (request) {
                    is ResultDownloadRequest.CurrentMatch ->
                        when (val result = builder.buildMatch(request.input)) {
                            is MatchResultExportModelBuildResult.Success ->
                                (freeDesignBitmapComposer.compose(
                                    result.model,
                                    template,
                                    pointTableDetails.value.date,
                                    pointTableDetails.value.organizationName.trim(),
                                    logoRenderData,
                                ) as? FreeDesignBitmapComposeResult.Success)?.bitmap
                            is MatchResultExportModelBuildResult.Failure -> null
                        }
                    is ResultDownloadRequest.WholeTournament ->
                        when (val result = builder.buildTournament(request.input)) {
                            is TournamentResultExportModelBuildResult.Success ->
                                (freeDesignBitmapComposer.compose(
                                    result.model,
                                    template,
                                    pointTableDetails.value.date,
                                    pointTableDetails.value.organizationName.trim(),
                                    logoRenderData,
                                ) as? FreeDesignBitmapComposeResult.Success)?.bitmap
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
            } finally {
                logoRenderData?.bitmap?.let { bitmap ->
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }
        }

    private suspend fun resolveLogoRenderData(
        tournamentId: String,
        design: DownloadResultDesignType,
    ): PointTableLogoRenderData? {
        val designKey = pointTableLogoDesignKey(
            design = design,
            freeDesignTemplateId = _selectionContext.value.freeDesignTemplateId,
        ) ?: return null
        return pointTableLogoRenderResolver.resolve(
            tournamentId = tournamentId,
            designKey = designKey,
            organizationLogoPath = _pointTableDetails.value.organizationLogoPath,
        )
    }

    private fun refreshSupportedSelectionIfActive(tournamentId: String) {
        if (
            selectedTournamentId == tournamentId &&
            (
                selectedDesign == DownloadResultDesignType.IMAGE ||
                    selectedDesign == DownloadResultDesignType.FREE_DESIGN
                )
        ) {
            select(tournamentId, selectedResult, selectedDesign)
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
    onOpenCustomDesignSetup: (String, ResultDownloadScope, String) -> Unit = { _, _, _ -> },
    onFreeDesignSettingsClick: () -> Unit = {},
    viewModel: DownloadResultViewModel = hiltViewModel(),
) {
    LaunchedEffect(tournamentId) {
        viewModel.load(tournamentId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val pointTableDetails by viewModel.pointTableDetails.collectAsStateWithLifecycle()
    val organizationLogoDisplayUri by viewModel.organizationLogoDisplayUri.collectAsStateWithLifecycle()
    val logoEditorState by viewModel.logoEditorState.collectAsStateWithLifecycle()
    val logoOperationError by viewModel.logoOperationError.collectAsStateWithLifecycle()
    val logoSaveInProgress by viewModel.logoSaveInProgress.collectAsStateWithLifecycle()
    val hasSavedCustomDesign by viewModel.hasSavedCustomDesign.collectAsStateWithLifecycle()
    val selectionContext by viewModel.selectionContext.collectAsStateWithLifecycle()
    ResultShareEventEffect(shareEvents = viewModel.shareEvents)
    val lifecycleOwner = LocalLifecycleOwner.current
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
        onResult = viewModel::onDestinationResult,
    )
    var pendingCustomDesignSelection by remember {
        mutableStateOf<DownloadResultSelection?>(null)
    }
    var pendingLogoPickerRequest by remember {
        mutableStateOf<PointTableLogoPickerRequest?>(null)
    }
    val customDesignImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { selectedUri ->
            val selection = pendingCustomDesignSelection
            pendingCustomDesignSelection = null
            val matchId = (selection as? DownloadResultSelection.Match)?.matchId ?: sourceMatchId
            if (selectedUri != null && selection != null && matchId != null) {
                onOpenCustomDesignSetup(
                    matchId,
                    selection.exportScope,
                    selectedUri.toString(),
                )
            }
        },
    )
    val organizationLogoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { selectedUri ->
            val request = pendingLogoPickerRequest
            pendingLogoPickerRequest = null
            if (selectedUri != null && request != null) {
                viewModel.prepareOrganizationLogoCandidate(
                    tournamentId = tournamentId,
                    designKey = request.designKey,
                    previewPngBytes = request.previewPngBytes,
                    selectedUri = selectedUri.toString(),
                )
            }
        },
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
        selectionContext = selectionContext.takeIf { it.tournamentId == tournamentId },
        previewState = previewState,
        downloadState = downloadState,
        pointTableDetails = pointTableDetails,
        organizationLogoDisplayUri = organizationLogoDisplayUri,
        logoEditorState = logoEditorState,
        logoOperationError = logoOperationError,
        logoSaveInProgress = logoSaveInProgress,
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
            if (pendingCustomDesignSelection == null && matchId != null) {
                pendingCustomDesignSelection = selection
                try {
                    customDesignImagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                } catch (_: Exception) {
                    pendingCustomDesignSelection = null
                }
            }
        },
        onFreeDesignSettingsClick = onFreeDesignSettingsClick,
        onPointTableDetailsApply = { details ->
            viewModel.savePointTableDetails(tournamentId, details)
        },
        onOrganizationLogoPick = { designKey, previewPngBytes ->
            pendingLogoPickerRequest = PointTableLogoPickerRequest(designKey, previewPngBytes)
            organizationLogoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onOrganizationLogoEdit = { designKey, previewPngBytes ->
            viewModel.openOrganizationLogoEditor(tournamentId, designKey, previewPngBytes)
        },
        onOrganizationLogoRemove = { viewModel.removeOrganizationLogo(tournamentId) },
        onOrganizationLogoCancel = viewModel::cancelOrganizationLogoEditor,
        onOrganizationLogoSave = viewModel::saveOrganizationLogoPlacement,
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
    selectionContext: DownloadResultSelectionContext? = null,
    selectedFreeDesignTemplateId: String = FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
    previewState: DownloadResultPreviewState = DownloadResultPreviewState.Idle,
    downloadState: DownloadResultDownloadState = DownloadResultDownloadState.Idle,
    hasSavedCustomDesign: Boolean = false,
    pointTableDetails: PointTableDetailsUiState = PointTableDetailsUiState(),
    organizationLogoDisplayUri: String? = null,
    logoEditorState: PointTableLogoEditorState? = null,
    logoOperationError: String? = null,
    logoSaveInProgress: Boolean = false,
    onResultSelected: (DownloadResultSelection, DownloadResultDesignType) -> Unit = { _, _ -> },
    onDesignSelected: (DownloadResultSelection, DownloadResultDesignType) -> Unit = { _, _ -> },
    onFreeDesignTemplateSelected: (String) -> Unit = {},
    onFreeDesignSettingsClick: () -> Unit = {},
    onPointTableDetailsApply: (PointTableDetailsUiState) -> Unit = {},
    onOrganizationLogoPick: (String, ByteArray) -> Unit = { _, _ -> },
    onOrganizationLogoEdit: (String, ByteArray) -> Unit = { _, _ -> },
    onOrganizationLogoRemove: () -> Unit = {},
    onOrganizationLogoCancel: () -> Unit = {},
    onOrganizationLogoSave: (PointTableLogoPlacementGeometry) -> Unit = {},
    onImportYourDesign: (DownloadResultSelection) -> Unit = {},
    onDeleteSavedCustomDesign: () -> Unit = {},
    onDownload: (DownloadResultSelection, DownloadResultDesignType) -> Unit = { _, _ -> },
) {
    var localSelectedResult by remember {
        mutableStateOf(initialResult)
    }
    var localSelectedDesign by remember { mutableStateOf(initialDesign) }
    var localSelectedTemplateId by remember(selectedFreeDesignTemplateId) {
        mutableStateOf(selectedFreeDesignTemplateId)
    }
    val selectedResult = selectionContext?.result ?: localSelectedResult
    val selectedDesign = selectionContext?.design ?: localSelectedDesign
    val selectedTemplateId = selectionContext?.freeDesignTemplateId ?: localSelectedTemplateId
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showPointTableDetailsDialog by remember { mutableStateOf(false) }
    var pointTableDetailsDraft by remember { mutableStateOf(PointTableDetailsUiState()) }
    var logoEditorWasOpen by remember { mutableStateOf(false) }
    val orderedMatches = remember(matches) { matches.sortedBy { it.matchNumber } }

    LaunchedEffect(logoEditorState) {
        if (logoEditorState != null) {
            showPointTableDetailsDialog = false
            logoEditorWasOpen = true
        } else if (logoEditorWasOpen) {
            showPointTableDetailsDialog = true
            logoEditorWasOpen = false
        }
    }

    LaunchedEffect(initialResult, initialDesign) {
        if (selectionContext == null) {
            onResultSelected(initialResult, initialDesign)
            onDesignSelected(initialResult, initialDesign)
        }
    }

    if (logoEditorState != null) {
        PointTableLogoPlacementEditor(
            state = logoEditorState,
            onCancel = {
                onOrganizationLogoCancel()
            },
            onSave = { geometry ->
                onOrganizationLogoSave(geometry)
            },
            isSaving = logoSaveInProgress,
            errorMessage = logoOperationError,
        )
        return
    }

    val openPointTableDetails = {
        pointTableDetailsDraft = pointTableDetails
        showPointTableDetailsDialog = true
        onFreeDesignSettingsClick()
    }

    LaunchedEffect(orderedMatches) {
        val selectedMatch = selectedResult as? DownloadResultSelection.Match
        if (selectedMatch != null && orderedMatches.none { it.matchId == selectedMatch.matchId }) {
            if (selectionContext == null) {
                localSelectedResult = DownloadResultSelection.Overall
            }
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
                    if (selectionContext == null) localSelectedResult = selection
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
                        if (selectionContext == null) localSelectedResult = selection
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
                        if (selectionContext == null) localSelectedDesign = design
                        onDesignSelected(selectedResult, design)
                    },
                    testTag = DOWNLOAD_RESULT_DESIGN_IMAGE_OPTION_TEST_TAG,
                )
                PointIqSelectionChip(
                    label = "Free Design",
                    selected = selectedDesign == DownloadResultDesignType.FREE_DESIGN,
                    onClick = {
                        val design = DownloadResultDesignType.FREE_DESIGN
                        if (selectionContext == null) localSelectedDesign = design
                        onDesignSelected(selectedResult, design)
                    },
                    testTag = DOWNLOAD_RESULT_DESIGN_FREE_OPTION_TEST_TAG,
                )
                PointIqSelectionChip(
                    label = "My Design",
                    selected = selectedDesign == DownloadResultDesignType.MY_DESIGN,
                    onClick = {
                        val design = DownloadResultDesignType.MY_DESIGN
                        if (selectionContext == null) localSelectedDesign = design
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
                            showSettingsControl = selectedDesign == DownloadResultDesignType.IMAGE ||
                                selectedDesign == DownloadResultDesignType.FREE_DESIGN,
                            onSettingsClick = openPointTableDetails,
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
                                if (selectionContext == null) localSelectedTemplateId = option.id
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

    if (showPointTableDetailsDialog) {
        PointTableDetailsDialog(
            value = pointTableDetailsDraft,
            onOrganizationNameChange = { organizationName ->
                pointTableDetailsDraft = pointTableDetailsDraft.copy(
                    organizationName = organizationName,
                )
            },
            onDateChange = { date ->
                pointTableDetailsDraft = pointTableDetailsDraft.copy(date = date)
            },
            organizationLogoDisplayUri = organizationLogoDisplayUri,
            logoOperationError = logoOperationError,
            onOrganizationLogoPick = {
                val designKey = pointTableLogoDesignKey(selectedDesign, selectedTemplateId)
                val previewBytes = (previewState as? DownloadResultPreviewState.ResultImage)?.pngBytes
                if (designKey != null && previewBytes != null) {
                    onOrganizationLogoPick(designKey, previewBytes)
                }
            },
            onOrganizationLogoEdit = {
                val designKey = pointTableLogoDesignKey(selectedDesign, selectedTemplateId)
                val previewBytes = (previewState as? DownloadResultPreviewState.ResultImage)?.pngBytes
                if (designKey != null && previewBytes != null) {
                    onOrganizationLogoEdit(designKey, previewBytes)
                }
            },
            onOrganizationLogoRemove = {
                onOrganizationLogoRemove()
            },
            onDismissRequest = { showPointTableDetailsDialog = false },
            onApply = {
                onPointTableDetailsApply(
                    pointTableDetailsDraft.copy(
                        organizationName = pointTableDetailsDraft.organizationName.trim(),
                    ),
                )
                showPointTableDetailsDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointTableDetailsDialog(
    value: PointTableDetailsUiState,
    onOrganizationNameChange: (String) -> Unit,
    onDateChange: (LocalDate?) -> Unit,
    organizationLogoDisplayUri: String?,
    logoOperationError: String?,
    onOrganizationLogoPick: () -> Unit,
    onOrganizationLogoEdit: () -> Unit,
    onOrganizationLogoRemove: () -> Unit,
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        val dialogShape = RoundedCornerShape(12.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    border = BorderStroke(1.dp, PointTableDetailsDialogBorder),
                    shape = dialogShape,
                ),
            shape = dialogShape,
            color = PointTableDetailsDialogSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "Point Table Details",
                    color = PointTableDetailsDialogTitle,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(16.dp))
                PointIqUnderlineTextField(
                    value = value.organizationName,
                    placeholder = stringResource(R.string.organisation_name_label),
                    fieldDescription = stringResource(R.string.organisation_name_label),
                    onValueChange = onOrganizationNameChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (organizationLogoDisplayUri == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClick = onOrganizationLogoPick,
                            ),
                    ) {
                        PointIqUnderlineTextField(
                            value = "",
                            placeholder = "Organisation Logo",
                            fieldDescription = "Organisation Logo",
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            enabled = false,
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClick = onOrganizationLogoPick,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.noun_upload_3927_vector),
                                contentDescription = "Upload logo",
                                tint = PointTableDetailsDialogAction,
                                modifier = Modifier
                                    .offset(y = (-2).dp)
                                    .size(24.dp),
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .drawBehind {
                                val strokeWidth = 1.dp.toPx()
                                val bottomY = size.height - 9.dp.toPx() - strokeWidth / 2f
                                drawLine(
                                    color = PointTableDetailsDialogFieldInactive,
                                    start = Offset(0f, bottomY),
                                    end = Offset(size.width, bottomY),
                                    strokeWidth = strokeWidth,
                                )
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(28.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                PointTableLogoThumbnail(displayUri = organizationLogoDisplayUri)
                            }
                            TextButton(
                                onClick = onOrganizationLogoEdit,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 4.dp,
                                    vertical = 0.dp,
                                ),
                            ) { Text("Edit") }
                            TextButton(
                                onClick = onOrganizationLogoPick,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 4.dp,
                                    vertical = 0.dp,
                                ),
                            ) { Text("Replace") }
                            TextButton(
                                onClick = onOrganizationLogoRemove,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 4.dp,
                                    vertical = 0.dp,
                                ),
                            ) { Text("Remove") }
                        }
                    }
                }
                if (logoOperationError != null) {
                    Text(
                        text = logoOperationError,
                        color = Color(0xFFFFB4AB),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            role = Role.Button,
                            onClick = { showDatePicker = true },
                        ),
                ) {
                    PointIqUnderlineTextField(
                        value = value.date?.format(PointTableDetailsDateFormatter).orEmpty(),
                        placeholder = "Date",
                        fieldDescription = "Date",
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        enabled = false,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp,
                        ),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PointTableDetailsDialogSecondaryAction,
                        ),
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onApply,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp,
                        ),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = PointTableDetailsDialogAction,
                        ),
                    ) {
                        Text(
                            text = "Apply",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.date?.toPointTableUtcMillis(),
        )
        val datePickerColors = DatePickerDefaults.colors(
            containerColor = PointTableDetailsDialogSurface,
            titleContentColor = PointTableDetailsDialogTitle,
            headlineContentColor = PointTableDetailsDialogTitle,
            weekdayContentColor = PointTableDetailsDialogBody,
            subheadContentColor = PointTableDetailsDialogBody,
            navigationContentColor = PointTableDetailsDialogBody,
            yearContentColor = PointTableDetailsDialogTitle,
            disabledYearContentColor = PointTableDetailsDialogFieldInactive,
            currentYearContentColor = PointTableDetailsDialogAction,
            selectedYearContentColor = PointTableDetailsDialogTitle,
            disabledSelectedYearContentColor = PointTableDetailsDialogFieldInactive,
            selectedYearContainerColor = PointTableDetailsDialogBorder,
            disabledSelectedYearContainerColor = PointTableDetailsDialogBorder.copy(alpha = 0.4f),
            dayContentColor = PointTableDetailsDialogTitle,
            disabledDayContentColor = PointTableDetailsDialogFieldInactive,
            selectedDayContentColor = PointTableDetailsDialogTitle,
            disabledSelectedDayContentColor = PointTableDetailsDialogFieldInactive,
            selectedDayContainerColor = PointTableDetailsDialogBorder,
            disabledSelectedDayContainerColor = PointTableDetailsDialogBorder.copy(alpha = 0.4f),
            todayContentColor = PointTableDetailsDialogAction,
            todayDateBorderColor = PointTableDetailsDialogAction,
            dayInSelectionRangeContainerColor = PointTableDetailsDialogBorder.copy(alpha = 0.2f),
            dayInSelectionRangeContentColor = PointTableDetailsDialogTitle,
            dividerColor = PointTableDetailsDialogBorder.copy(alpha = 0.35f),
        )
        val datePickerShape = RoundedCornerShape(12.dp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                            onDateChange(selectedDateMillis.toPointTableLocalDate())
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(
                        text = "Select date",
                        color = PointTableDetailsDialogAction,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                Row {
                    if (value.date != null) {
                        TextButton(
                            onClick = {
                                onDateChange(null)
                                showDatePicker = false
                            },
                        ) {
                            Text(
                                text = "Clear",
                                color = PointTableDetailsDialogSecondaryAction,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(
                            text = "Cancel",
                            color = PointTableDetailsDialogSecondaryAction,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            modifier = Modifier.border(
                BorderStroke(1.dp, PointTableDetailsDialogBorder),
                datePickerShape,
            ),
            shape = datePickerShape,
            tonalElevation = 0.dp,
            colors = datePickerColors,
        ) {
            DatePicker(
                state = datePickerState,
                colors = datePickerColors,
            )
        }
    }
}

private fun LocalDate.toPointTableUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPointTableLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun DownloadResultBitmapPreview(
    pngBytes: ByteArray,
    modifier: Modifier,
    showDeleteControl: Boolean = false,
    onDeleteClick: () -> Unit = {},
    showSettingsControl: Boolean = false,
    onSettingsClick: () -> Unit = {},
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, pngBytes) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
        }
    }
    val previewBitmap = bitmap ?: return
    // This bitmap is UI-owned. Compose's BitmapPainter may still draw during disposal, so it
    // must remain unrecycled until it is no longer reachable by the composition.
    if (showDeleteControl || showSettingsControl) {
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
                if (showDeleteControl) {
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
                if (showSettingsControl) {
                    val settingsShape = RoundedCornerShape(6.dp)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .zIndex(1f)
                            .size(30.dp)
                            .clip(settingsShape)
                            .background(DownloadResultUnselectedChipBackground)
                            .border(
                                width = 1.dp,
                                color = DownloadResultUnselectedChipText.copy(alpha = 0.65f),
                                shape = settingsShape,
                            )
                            .clickable(onClick = onSettingsClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Free design settings",
                            tint = Color(0xFFF2F2F2),
                            modifier = Modifier.size(17.dp),
                        )
                    }
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
