package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.MatchResultExportModelBuildResult
import com.hoggamers.rankforge.domain.export.ResultExportModelBuilder
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import com.hoggamers.rankforge.domain.export.TournamentResultExportModelBuildResult
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FreeDesignResultDownloadCoordinator {
    suspend fun execute(
        request: ResultDownloadRequest,
        onSaving: suspend () -> Unit = {},
    ): ResultDownloadExecutionResult
}

object NoOpFreeDesignResultDownloadCoordinator : FreeDesignResultDownloadCoordinator {
    override suspend fun execute(
        request: ResultDownloadRequest,
        onSaving: suspend () -> Unit,
    ): ResultDownloadExecutionResult = ResultDownloadExecutionResult.Failure(
        ResultDownloadFailure.GENERATION_FAILED,
    )
}

class DefaultFreeDesignResultDownloadCoordinator internal constructor(
    private val modelBuilder: ResultExportModelBuilder,
    private val composeMatch: (MatchResultExportModel, FreeDesignTemplate) -> FreeDesignBitmapComposeResult,
    private val composeTournament: (TournamentResultExportModel, FreeDesignTemplate) -> FreeDesignBitmapComposeResult,
    private val templateProvider: () -> FreeDesignTemplate?,
    private val saveFile: suspend (ByteArray, String, ResultExportFileFormat) -> ResultFileSaveResult,
) : FreeDesignResultDownloadCoordinator {
    @Inject
    constructor(
        bitmapComposer: FreeDesignBitmapComposer,
        resultFileSaver: ResultFileSaver,
    ) : this(
        modelBuilder = ResultExportModelBuilder(),
        composeMatch = bitmapComposer::compose,
        composeTournament = bitmapComposer::compose,
        templateProvider = {
            FreeDesignTemplateRegistry.findById(FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID)
        },
        saveFile = resultFileSaver::save,
    )

    override suspend fun execute(
        request: ResultDownloadRequest,
        onSaving: suspend () -> Unit,
    ): ResultDownloadExecutionResult {
        val generated = try {
            withContext(Dispatchers.Default) {
                generate(request)
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
                    uri = saveResult.uri,
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

    private fun generate(request: ResultDownloadRequest): GeneratedFreeDesignResult? {
        val template = templateProvider() ?: return null
        return when (request) {
            is ResultDownloadRequest.CurrentMatch ->
                when (val buildResult = modelBuilder.buildMatch(request.input)) {
                    is MatchResultExportModelBuildResult.Success -> {
                        val model = buildResult.model
                        composeMatch(model, template).encodedOrNull()?.let { bytes ->
                            GeneratedFreeDesignResult(
                                bytes = bytes,
                                displayName = ResultExportFileName.forMatch(
                                    model,
                                    ResultExportFileFormat.PNG,
                                ),
                            )
                        }
                    }
                    is MatchResultExportModelBuildResult.Failure -> null
                }
            is ResultDownloadRequest.WholeTournament ->
                when (val buildResult = modelBuilder.buildTournament(request.input)) {
                    is TournamentResultExportModelBuildResult.Success -> {
                        val model = buildResult.model
                        composeTournament(model, template).encodedOrNull()?.let { bytes ->
                            GeneratedFreeDesignResult(
                                bytes = bytes,
                                displayName = ResultExportFileName.forTournament(
                                    model,
                                    ResultExportFileFormat.PNG,
                                ),
                            )
                        }
                    }
                    is TournamentResultExportModelBuildResult.Failure -> null
                }
        }
    }

    private fun FreeDesignBitmapComposeResult.encodedOrNull(): ByteArray? {
        val bitmap = (this as? FreeDesignBitmapComposeResult.Success)?.bitmap ?: return null
        return try {
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

    private data class GeneratedFreeDesignResult(
        val bytes: ByteArray,
        val displayName: String,
    )
}
