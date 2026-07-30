package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class MatchOcrReviewViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<MatchOcrReviewUiState>(MatchOcrReviewUiState.Loading)
    val uiState: StateFlow<MatchOcrReviewUiState> = _uiState.asStateFlow()

    private var loadedMatchKey: String? = null

    fun load(tournamentId: String, matchId: String) {
        val matchKey = "$tournamentId:$matchId"
        if (loadedMatchKey == matchKey) return
        loadedMatchKey = matchKey

        _uiState.update {
            MatchOcrReviewUiState.Empty(
                tournamentId = tournamentId,
                matchId = matchId,
            )
        }
    }

    fun onPlacementChanged(rowIndex: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onPlacementChanged(draft, rowIndex, value)
        }
    }

    fun onKillsChanged(rowIndex: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onKillsChanged(draft, rowIndex, value)
        }
    }

    fun onAssignedTeamSlotChanged(rowIndex: Int, value: String) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onAssignedTeamSlotChanged(draft, rowIndex, value)
        }
    }

    fun onResetRowCorrection(rowIndex: Int) {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onResetRowCorrection(draft, rowIndex)
        }
    }

    fun onResetAllCorrections() {
        updateCorrectionDraft { draft ->
            MatchOcrReviewCorrectionDraftReducer.onResetAllCorrections(draft)
        }
    }

    private fun updateCorrectionDraft(
        transform: (MatchOcrReviewCorrectionDraft) -> MatchOcrReviewCorrectionDraft,
    ) {
        _uiState.update { state ->
            if (state is MatchOcrReviewUiState.Ready) {
                val currentDraft = state.correctionDraft
                    ?: MatchOcrReviewCorrectionDraftReducer.createInitialDraft(state.rows)
                state.copy(correctionDraft = transform(currentDraft))
            } else {
                state
            }
        }
    }
}
