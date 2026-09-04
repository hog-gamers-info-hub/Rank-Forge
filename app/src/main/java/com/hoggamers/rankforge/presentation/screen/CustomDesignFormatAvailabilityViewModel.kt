package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryAction
import com.hoggamers.rankforge.data.cloud.CustomDesignSavedIdDiscoveryResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CustomDesignFormatAvailabilityStatus {
    LOADING,
    NONE,
    FOUND,
    UNAVAILABLE,
}

data class CustomDesignFormatAvailabilityUiState(
    val status: CustomDesignFormatAvailabilityStatus = CustomDesignFormatAvailabilityStatus.LOADING,
    val customDesignId: String? = null,
)

@HiltViewModel
class CustomDesignFormatAvailabilityViewModel @Inject constructor(
    private val discoveryAction: CustomDesignSavedIdDiscoveryAction,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomDesignFormatAvailabilityUiState())
    val uiState: StateFlow<CustomDesignFormatAvailabilityUiState> = _uiState.asStateFlow()

    private var refreshGeneration = 0L
    private var refreshJob: Job? = null

    fun refresh() {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        _uiState.value = CustomDesignFormatAvailabilityUiState(
            status = CustomDesignFormatAvailabilityStatus.LOADING,
        )
        refreshJob = viewModelScope.launch {
            try {
                val result = discoveryAction.find()
                if (generation != refreshGeneration) return@launch
                _uiState.value = result.toUiState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (generation == refreshGeneration) {
                    _uiState.value = CustomDesignFormatAvailabilityUiState(
                        status = CustomDesignFormatAvailabilityStatus.UNAVAILABLE,
                    )
                }
            }
        }
    }
}

private fun CustomDesignSavedIdDiscoveryResult.toUiState(): CustomDesignFormatAvailabilityUiState = when (this) {
    CustomDesignSavedIdDiscoveryResult.None -> CustomDesignFormatAvailabilityUiState(
        status = CustomDesignFormatAvailabilityStatus.NONE,
    )
    is CustomDesignSavedIdDiscoveryResult.Found -> customDesignId
        .takeIf(String::isNotBlank)
        ?.let {
            CustomDesignFormatAvailabilityUiState(
                status = CustomDesignFormatAvailabilityStatus.FOUND,
                customDesignId = it,
            )
        }
        ?: CustomDesignFormatAvailabilityUiState(
            status = CustomDesignFormatAvailabilityStatus.UNAVAILABLE,
        )
    CustomDesignSavedIdDiscoveryResult.Ambiguous,
    is CustomDesignSavedIdDiscoveryResult.Failed,
    -> CustomDesignFormatAvailabilityUiState(
        status = CustomDesignFormatAvailabilityStatus.UNAVAILABLE,
    )
}
