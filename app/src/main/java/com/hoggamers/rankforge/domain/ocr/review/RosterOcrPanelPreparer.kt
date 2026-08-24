package com.hoggamers.rankforge.domain.ocr.review

import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage

@JvmInline
value class RosterOcrLocalRelativePath(
    val value: String,
)

data class RosterOcrScreenshotSource(
    val tournamentId: String,
    val rosterScreenshotIndex: Int,
    val screenshotPosition: RosterScreenshotPosition,
    val localRelativePath: RosterOcrLocalRelativePath,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val cropLeft: Double,
    val cropTop: Double,
    val cropRight: Double,
    val cropBottom: Double,
)

sealed interface RosterOcrSourceProviderResult {
    data class Loaded(
        val sources: List<RosterOcrScreenshotSource>,
    ) : RosterOcrSourceProviderResult

    data object InvalidTournamentContext : RosterOcrSourceProviderResult
    data class MismatchedTournamentContext(
        val screenshotIndex: Int,
    ) : RosterOcrSourceProviderResult
    data object IncompleteScreenshotSet : RosterOcrSourceProviderResult

    data class DuplicateScreenshotPositions(
        val screenshotIndices: List<Int>,
    ) : RosterOcrSourceProviderResult

    data class UnsupportedScreenshotPosition(
        val screenshotIndex: Int,
    ) : RosterOcrSourceProviderResult

    data class MissingCropMetadata(
        val screenshotIndex: Int,
    ) : RosterOcrSourceProviderResult

    data object LoadingFailure : RosterOcrSourceProviderResult
}

interface RosterOcrSourceProvider {
    /** Legacy compatibility entry point; production callers must use owner-bound load. */
    suspend fun load(tournamentId: String): RosterOcrSourceProviderResult =
        RosterOcrSourceProviderResult.LoadingFailure

    suspend fun load(
        tournamentId: String,
        expectedOwnerUserId: String,
    ): RosterOcrSourceProviderResult
}

enum class RosterOcrPanelPreparationFailure {
    MISSING_LOCAL_ORIGINAL,
    UNREADABLE_OR_DECODE_FAILURE,
    INVALID_CROP,
    UNSAFE_DIMENSIONS,
    CROP_FAILURE,
    UNKNOWN,
}

interface RosterOcrPreparedPanel {
    val croppedPanelImage: OcrPreprocessingImage
    val croppedPanelInput: CroppedRosterPanelInput

    fun release()
}

sealed interface RosterOcrPanelPreparationResult {
    data class Prepared(
        val panel: RosterOcrPreparedPanel,
    ) : RosterOcrPanelPreparationResult

    data class Failed(
        val failure: RosterOcrPanelPreparationFailure,
    ) : RosterOcrPanelPreparationResult
}

interface RosterOcrPanelPreparer {
    suspend fun prepare(source: RosterOcrScreenshotSource): RosterOcrPanelPreparationResult
}
