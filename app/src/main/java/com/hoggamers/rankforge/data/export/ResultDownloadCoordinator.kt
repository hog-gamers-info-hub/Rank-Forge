package com.hoggamers.rankforge.data.export

import com.hoggamers.rankforge.domain.export.MatchCsvExportInput
import com.hoggamers.rankforge.domain.export.MatchResultExportModelBuildResult
import com.hoggamers.rankforge.domain.export.ResultExportModelBuilder
import com.hoggamers.rankforge.domain.export.TournamentCsvExportInput
import com.hoggamers.rankforge.domain.export.TournamentResultExportModelBuildResult
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ResultDownloadScope {
    CURRENT_MATCH,
    WHOLE_TOURNAMENT,
}

enum class ResultDownloadFailure {
    INVALID_CONTEXT,
    INVALID_MATCH,
    GENERATION_FAILED,
    SAVE_FAILED,
    DESTINATION_WRITE_FAILED,
    DESTINATION_LAUNCH_FAILED,
}

sealed interface ResultDownloadRequest {
    data class CurrentMatch(
        val input: MatchCsvExportInput,
    ) : ResultDownloadRequest

    data class WholeTournament(
        val input: TournamentCsvExportInput,
    ) : ResultDownloadRequest
}

sealed interface ResultDownloadExecutionResult {
    data class Saved(
        val format: ResultExportFileFormat,
        val displayName: String,
    ) : ResultDownloadExecutionResult

    data class UserDestinationRequired(
        val format: ResultExportFileFormat,
        val displayName: String,
        val bytes: ByteArray,
    ) : ResultDownloadExecutionResult

    data class Failure(
        val reason: ResultDownloadFailure,
    ) : ResultDownloadExecutionResult
}

interface ResultDownloadCoordinator {
    suspend fun execute(
        request: ResultDownloadRequest,
        format: ResultExportFileFormat,
        onSaving: suspend () -> Unit = {},
    ): ResultDownloadExecutionResult
}

object NoOpResultDownloadCoordinator : ResultDownloadCoordinator {
    override suspend fun execute(
        request: ResultDownloadRequest,
        format: ResultExportFileFormat,
        onSaving: suspend () -> Unit,
    ): ResultDownloadExecutionResult = ResultDownloadExecutionResult.Failure(
        ResultDownloadFailure.GENERATION_FAILED,
    )
}

class DefaultResultDownloadCoordinator @Inject constructor(
    private val resultFileSaver: ResultFileSaver,
) : ResultDownloadCoordinator {
    private val modelBuilder = ResultExportModelBuilder()
    private val pdfRenderer = ResultPdfRenderer()
    private val pngRenderer = ResultPngRenderer()

    override suspend fun execute(
        request: ResultDownloadRequest,
        format: ResultExportFileFormat,
        onSaving: suspend () -> Unit,
    ): ResultDownloadExecutionResult {
        val rendered = try {
            withContext(Dispatchers.Default) {
                render(request, format)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: RuntimeException) {
            return ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED)
        }

        if (rendered == null) {
            return ResultDownloadExecutionResult.Failure(ResultDownloadFailure.GENERATION_FAILED)
        }

        onSaving()
        return try {
            when (val saveResult = resultFileSaver.save(rendered.bytes, rendered.displayName, format)) {
                is ResultFileSaveResult.Success -> ResultDownloadExecutionResult.Saved(
                    format = format,
                    displayName = saveResult.displayName,
                )
                ResultFileSaveResult.UserSelectedDestinationRequired ->
                    ResultDownloadExecutionResult.UserDestinationRequired(
                        format = format,
                        displayName = rendered.displayName,
                        bytes = rendered.bytes,
                    )
                is ResultFileSaveResult.Failure ->
                    ResultDownloadExecutionResult.Failure(ResultDownloadFailure.SAVE_FAILED)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: IOException) {
            ResultDownloadExecutionResult.Failure(ResultDownloadFailure.SAVE_FAILED)
        } catch (_: RuntimeException) {
            ResultDownloadExecutionResult.Failure(ResultDownloadFailure.SAVE_FAILED)
        }
    }

    private fun render(
        request: ResultDownloadRequest,
        format: ResultExportFileFormat,
    ): RenderedResult? = when (request) {
        is ResultDownloadRequest.CurrentMatch -> {
            when (val buildResult = modelBuilder.buildMatch(request.input)) {
                is MatchResultExportModelBuildResult.Success -> {
                    val model = buildResult.model
                    val bytes = when (format) {
                        ResultExportFileFormat.PDF ->
                            (pdfRenderer.render(model) as? ResultPdfRenderResult.Success)?.bytes
                        ResultExportFileFormat.PNG ->
                            (pngRenderer.render(model) as? ResultPngRenderResult.Success)?.pngBytes
                    }
                    bytes?.let {
                        RenderedResult(
                            bytes = it,
                            displayName = ResultExportFileName.forMatch(model, format),
                        )
                    }
                }
                is MatchResultExportModelBuildResult.Failure -> null
            }
        }
        is ResultDownloadRequest.WholeTournament -> {
            when (val buildResult = modelBuilder.buildTournament(request.input)) {
                is TournamentResultExportModelBuildResult.Success -> {
                    val model = buildResult.model
                    val bytes = when (format) {
                        ResultExportFileFormat.PDF ->
                            (pdfRenderer.render(model) as? ResultPdfRenderResult.Success)?.bytes
                        ResultExportFileFormat.PNG ->
                            (pngRenderer.render(model) as? ResultPngRenderResult.Success)?.pngBytes
                    }
                    bytes?.let {
                        RenderedResult(
                            bytes = it,
                            displayName = ResultExportFileName.forTournament(model, format),
                        )
                    }
                }
                is TournamentResultExportModelBuildResult.Failure -> null
            }
        }
    }

    private data class RenderedResult(
        val bytes: ByteArray,
        val displayName: String,
    )
}
