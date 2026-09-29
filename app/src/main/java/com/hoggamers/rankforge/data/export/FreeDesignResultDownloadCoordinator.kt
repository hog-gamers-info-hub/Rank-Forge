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
import java.time.LocalDate

interface FreeDesignResultDownloadCoordinator {
    suspend fun execute(
        request: ResultDownloadRequest,
        templateId: String = FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
        onSaving: suspend () -> Unit = {},
        displayDate: LocalDate? = null,
        organizationName: String = "",
    ): ResultDownloadExecutionResult

    suspend fun executeWithLogo(
        request: ResultDownloadRequest,
        logoRenderData: PointTableLogoRenderData?,
        templateId: String = FreeDesignTemplateRegistry.DEFAULT_TEMPLATE_ID,
        onSaving: suspend () -> Unit = {},
        displayDate: LocalDate? = null,
        organizationName: String = "",
    ): ResultDownloadExecutionResult = execute(
        request = request,
        templateId = templateId,
        onSaving = onSaving,
        displayDate = displayDate,
        organizationName = organizationName,
    )
}

object NoOpFreeDesignResultDownloadCoordinator : FreeDesignResultDownloadCoordinator {
    override suspend fun execute(
        request: ResultDownloadRequest,
        templateId: String,
        onSaving: suspend () -> Unit,
        displayDate: LocalDate?,
        organizationName: String,
    ): ResultDownloadExecutionResult = ResultDownloadExecutionResult.Failure(
        ResultDownloadFailure.GENERATION_FAILED,
    )
}

class DefaultFreeDesignResultDownloadCoordinator internal constructor(
    private val modelBuilder: ResultExportModelBuilder,
    private val composeMatch: (MatchResultExportModel, FreeDesignTemplate, LocalDate?, String) -> FreeDesignBitmapComposeResult,
    private val composeTournament: (TournamentResultExportModel, FreeDesignTemplate, LocalDate?, String) -> FreeDesignBitmapComposeResult,
    private val templateProvider: (String) -> FreeDesignTemplate?,
    private val saveFile: suspend (ByteArray, String, ResultExportFileFormat) -> ResultFileSaveResult,
    private val composeMatchWithLogo: ((MatchResultExportModel, FreeDesignTemplate, LocalDate?, String, PointTableLogoRenderData?) -> FreeDesignBitmapComposeResult)? = null,
    private val composeTournamentWithLogo: ((TournamentResultExportModel, FreeDesignTemplate, LocalDate?, String, PointTableLogoRenderData?) -> FreeDesignBitmapComposeResult)? = null,
) : FreeDesignResultDownloadCoordinator {
    @Inject
    constructor(
        bitmapComposer: FreeDesignBitmapComposer,
        resultFileSaver: ResultFileSaver,
    ) : this(
        modelBuilder = ResultExportModelBuilder(),
        composeMatch = { model, template, displayDate, organizationName ->
            bitmapComposer.compose(model, template, displayDate, organizationName)
        },
        composeTournament = { model, template, displayDate, organizationName ->
            bitmapComposer.compose(model, template, displayDate, organizationName)
        },
        templateProvider = { templateId ->
            FreeDesignTemplateRegistry.findById(templateId)
        },
        saveFile = resultFileSaver::save,
        composeMatchWithLogo = { model, template, displayDate, organizationName, logoRenderData ->
            bitmapComposer.compose(model, template, displayDate, organizationName, logoRenderData)
        },
        composeTournamentWithLogo = { model, template, displayDate, organizationName, logoRenderData ->
            bitmapComposer.compose(model, template, displayDate, organizationName, logoRenderData)
        },
    )

    override suspend fun execute(
        request: ResultDownloadRequest,
        templateId: String,
        onSaving: suspend () -> Unit,
        displayDate: LocalDate?,
        organizationName: String,
    ): ResultDownloadExecutionResult {
        return executeInternal(
            request = request,
            templateId = templateId,
            onSaving = onSaving,
            displayDate = displayDate,
            organizationName = organizationName,
            logoRenderData = null,
        )
    }

    override suspend fun executeWithLogo(
        request: ResultDownloadRequest,
        logoRenderData: PointTableLogoRenderData?,
        templateId: String,
        onSaving: suspend () -> Unit,
        displayDate: LocalDate?,
        organizationName: String,
    ): ResultDownloadExecutionResult = executeInternal(
        request = request,
        templateId = templateId,
        onSaving = onSaving,
        displayDate = displayDate,
        organizationName = organizationName,
        logoRenderData = logoRenderData,
    )

    private suspend fun executeInternal(
        request: ResultDownloadRequest,
        templateId: String,
        onSaving: suspend () -> Unit,
        displayDate: LocalDate?,
        organizationName: String,
        logoRenderData: PointTableLogoRenderData?,
    ): ResultDownloadExecutionResult {
        val generated = try {
            withContext(Dispatchers.Default) {
                generate(request, templateId, displayDate, organizationName, logoRenderData)
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

    private fun generate(
        request: ResultDownloadRequest,
        templateId: String,
        displayDate: LocalDate?,
        organizationName: String,
        logoRenderData: PointTableLogoRenderData?,
    ): GeneratedFreeDesignResult? {
        val template = templateProvider(templateId) ?: return null
        return when (request) {
            is ResultDownloadRequest.CurrentMatch ->
                when (val buildResult = modelBuilder.buildMatch(request.input)) {
                    is MatchResultExportModelBuildResult.Success -> {
                        val model = buildResult.model
                        composeMatchForRender(
                            model,
                            template,
                            displayDate,
                            organizationName,
                            logoRenderData,
                        ).encodedOrNull()?.let { bytes ->
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
                        composeTournamentForRender(
                            model,
                            template,
                            displayDate,
                            organizationName,
                            logoRenderData,
                        ).encodedOrNull()?.let { bytes ->
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

    private fun composeMatchForRender(
        model: MatchResultExportModel,
        template: FreeDesignTemplate,
        displayDate: LocalDate?,
        organizationName: String,
        logoRenderData: PointTableLogoRenderData?,
    ): FreeDesignBitmapComposeResult = if (logoRenderData != null && composeMatchWithLogo != null) {
        composeMatchWithLogo(model, template, displayDate, organizationName, logoRenderData)
    } else {
        composeMatch(model, template, displayDate, organizationName)
    }

    private fun composeTournamentForRender(
        model: TournamentResultExportModel,
        template: FreeDesignTemplate,
        displayDate: LocalDate?,
        organizationName: String,
        logoRenderData: PointTableLogoRenderData?,
    ): FreeDesignBitmapComposeResult = if (logoRenderData != null && composeTournamentWithLogo != null) {
        composeTournamentWithLogo(model, template, displayDate, organizationName, logoRenderData)
    } else {
        composeTournament(model, template, displayDate, organizationName)
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
