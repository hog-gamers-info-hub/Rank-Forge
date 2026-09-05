package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreAction
import com.hoggamers.rankforge.data.cloud.CustomDesignRestoreResult
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import com.hoggamers.rankforge.domain.export.ResultExportRow
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface CustomDesignResultDownloadCoordinator {
    suspend fun execute(
        customDesignId: String,
        request: ResultDownloadRequest,
        onSaving: suspend () -> Unit = {},
    ): ResultDownloadExecutionResult
}

object NoOpCustomDesignResultDownloadCoordinator : CustomDesignResultDownloadCoordinator {
    override suspend fun execute(
        customDesignId: String,
        request: ResultDownloadRequest,
        onSaving: suspend () -> Unit,
    ): ResultDownloadExecutionResult = ResultDownloadExecutionResult.Failure(
        ResultDownloadFailure.GENERATION_FAILED,
    )
}

class DefaultCustomDesignResultDownloadCoordinator internal constructor(
    private val restoreAction: CustomDesignRestoreAction,
    private val resolveRows: (ResultDownloadRequest) -> CustomDesignResultRowsResult,
    private val composeBitmap: (
        String,
        List<ResultExportRow>,
        CustomDesignEffectiveGridGeometry,
    ) -> CustomDesignBitmapComposeResult,
    private val saveFile: suspend (ByteArray, String, ResultExportFileFormat) -> ResultFileSaveResult,
) : CustomDesignResultDownloadCoordinator {
    @Inject
    constructor(
        restoreAction: CustomDesignRestoreAction,
        rowsResolver: CustomDesignResultRowsResolver,
        bitmapComposer: CustomDesignBitmapComposer,
        resultFileSaver: ResultFileSaver,
    ) : this(
        restoreAction = restoreAction,
        resolveRows = rowsResolver::resolve,
        composeBitmap = bitmapComposer::compose,
        saveFile = resultFileSaver::save,
    )

    override suspend fun execute(
        customDesignId: String,
        request: ResultDownloadRequest,
        onSaving: suspend () -> Unit,
    ): ResultDownloadExecutionResult {
        val generated = try {
            withContext(Dispatchers.Default) {
                generate(customDesignId, request)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED)
        } ?: return ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED)

        onSaving()
        return try {
            when (val saveResult = saveFile(
                generated.bytes,
                generated.displayName,
                ResultExportFileFormat.PNG,
            )) {
                is ResultFileSaveResult.Success -> ResultDownloadExecutionResult.Saved(
                    format = ResultExportFileFormat.PNG,
                    displayName = saveResult.displayName,
                )
                ResultFileSaveResult.UserSelectedDestinationRequired ->
                    ResultDownloadExecutionResult.UserDestinationRequired(
                        format = ResultExportFileFormat.PNG,
                        displayName = generated.displayName,
                        bytes = generated.bytes,
                    )
                is ResultFileSaveResult.Failure ->
                    ResultDownloadExecutionResult.Failure(ResultDownloadFailure.SAVE_FAILED)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            ResultDownloadExecutionResult.Failure(ResultDownloadFailure.SAVE_FAILED)
        }
    }

    private suspend fun generate(
        customDesignId: String,
        request: ResultDownloadRequest,
    ): GeneratedCustomDesignResult? {
        val design = when (val result = restoreAction.restore(customDesignId)) {
            is CustomDesignRestoreResult.Success -> result.design
            is CustomDesignRestoreResult.Failed -> return null
        }
        val rows = when (val result = resolveRows(request)) {
            is CustomDesignResultRowsResult.Success -> result.rows
            is CustomDesignResultRowsResult.MatchFailure,
            is CustomDesignResultRowsResult.TournamentFailure,
            -> return null
        }
        val bitmap = when (
            val result = composeBitmap(design.localImageReference, rows, design.geometry)
        ) {
            is CustomDesignBitmapComposeResult.Success -> result.bitmap
            is CustomDesignBitmapComposeResult.Failure -> return null
        }
        val bytes = try {
            val output = ByteArrayOutputStream()
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                output.toByteArray().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } finally {
            bitmap.recycle()
        }
        return bytes?.let {
            GeneratedCustomDesignResult(
                bytes = it,
                displayName = request.customDesignDisplayName(),
            )
        }
    }

    private fun ResultDownloadRequest.customDesignDisplayName(): String = when (this) {
        is ResultDownloadRequest.CurrentMatch ->
            "RankForge_${ResultExportFileName.sanitizeTournamentComponent(input.tournament.name)}_" +
                "Match_${input.match.matchNumber}_Result.png"
        is ResultDownloadRequest.WholeTournament ->
            "RankForge_${ResultExportFileName.sanitizeTournamentComponent(input.tournament.name)}_" +
                "Tournament_Result.png"
    }

    private data class GeneratedCustomDesignResult(
        val bytes: ByteArray,
        val displayName: String,
    )
}
