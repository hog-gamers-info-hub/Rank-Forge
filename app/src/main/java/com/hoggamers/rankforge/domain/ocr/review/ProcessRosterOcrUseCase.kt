package com.hoggamers.rankforge.domain.ocr.review

import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import java.util.concurrent.CancellationException
import javax.inject.Inject

data class ProcessRosterOcrEvidence(
    val rawExtractions: List<RosterRawOcrExtractionResult>,
    val parsedCandidates: RosterCandidateParseResult,
    val associatedCandidates: RosterSlotAssociationResult,
    val validation: RosterOcrValidationResult,
)

sealed interface ProcessRosterOcrFailure {
    data object InvalidTournamentContext : ProcessRosterOcrFailure

    data class SourceLoading(
        val failure: RosterOcrSourceProviderResult,
    ) : ProcessRosterOcrFailure

    data class PanelPreparation(
        val screenshotIndex: Int,
        val failure: RosterOcrPanelPreparationFailure,
    ) : ProcessRosterOcrFailure

    data class UnexpectedExtraction(
        val screenshotIndex: Int,
    ) : ProcessRosterOcrFailure

    data class UnexpectedPanelRelease(
        val screenshotIndex: Int,
    ) : ProcessRosterOcrFailure

    data class UnexpectedExtractionAndPanelRelease(
        val screenshotIndex: Int,
    ) : ProcessRosterOcrFailure

    data object UnexpectedParser : ProcessRosterOcrFailure
    data object UnexpectedAssociation : ProcessRosterOcrFailure
    data object UnexpectedValidation : ProcessRosterOcrFailure
}

sealed interface ProcessRosterOcrResult {
    data class Success(
        val evidence: ProcessRosterOcrEvidence,
    ) : ProcessRosterOcrResult

    data class Failed(
        val failure: ProcessRosterOcrFailure,
    ) : ProcessRosterOcrResult
}

class ProcessRosterOcrUseCase @Inject constructor(
    private val sourceProvider: RosterOcrSourceProvider,
    private val panelPreparer: RosterOcrPanelPreparer,
    private val extractor: RosterRawOcrExtractor,
    private val parser: RosterCandidateParser,
    private val associator: RosterSlotAssociator,
    private val validator: RosterOcrValidator,
) {
    suspend operator fun invoke(tournamentId: String): ProcessRosterOcrResult {
        if (tournamentId.isBlank()) {
            return ProcessRosterOcrResult.Failed(ProcessRosterOcrFailure.InvalidTournamentContext)
        }

        val loaded = try {
            sourceProvider.load(tournamentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessRosterOcrResult.Failed(
                ProcessRosterOcrFailure.SourceLoading(RosterOcrSourceProviderResult.LoadingFailure),
            )
        }

        val orderedSources = when (loaded) {
            RosterOcrSourceProviderResult.InvalidTournamentContext ->
                return ProcessRosterOcrResult.Failed(ProcessRosterOcrFailure.InvalidTournamentContext)
            is RosterOcrSourceProviderResult.Loaded -> when (val validation = validateSources(loaded.sources, tournamentId)) {
                is SourceValidation.Valid -> validation.sources
                is SourceValidation.Invalid -> return ProcessRosterOcrResult.Failed(
                    ProcessRosterOcrFailure.SourceLoading(validation.failure),
                )
            }
            else -> return ProcessRosterOcrResult.Failed(ProcessRosterOcrFailure.SourceLoading(loaded))
        }

        val rawExtractions = buildList {
            orderedSources.forEach { source ->
                val prepared = try {
                    panelPreparer.prepare(source)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    return ProcessRosterOcrResult.Failed(
                        ProcessRosterOcrFailure.PanelPreparation(
                            source.rosterScreenshotIndex,
                            RosterOcrPanelPreparationFailure.UNKNOWN,
                        ),
                    )
                }

                val panel = when (prepared) {
                    is RosterOcrPanelPreparationResult.Failed -> return ProcessRosterOcrResult.Failed(
                        ProcessRosterOcrFailure.PanelPreparation(
                            source.rosterScreenshotIndex,
                            prepared.failure,
                        ),
                    )
                    is RosterOcrPanelPreparationResult.Prepared -> prepared.panel
                }

                val extractionOutcome = try {
                    ExtractionOutcome.Succeeded(
                        extractor.extract(
                            RosterRawOcrExtractionInput(
                                croppedPanelImage = panel.croppedPanelImage,
                                croppedPanelInput = panel.croppedPanelInput,
                            ),
                        )
                    )
                } catch (cancellation: CancellationException) {
                    ExtractionOutcome.Cancelled(cancellation)
                } catch (_: Throwable) {
                    ExtractionOutcome.Failed
                }

                val releaseOutcome = try {
                    panel.release()
                    ReleaseOutcome.Succeeded
                } catch (cancellation: CancellationException) {
                    ReleaseOutcome.Cancelled(cancellation)
                } catch (_: Throwable) {
                    ReleaseOutcome.Failed
                }

                when {
                    extractionOutcome is ExtractionOutcome.Cancelled ->
                        throw extractionOutcome.exception
                    releaseOutcome is ReleaseOutcome.Cancelled ->
                        throw releaseOutcome.exception
                    extractionOutcome is ExtractionOutcome.Failed &&
                        releaseOutcome is ReleaseOutcome.Failed ->
                        return ProcessRosterOcrResult.Failed(
                            ProcessRosterOcrFailure.UnexpectedExtractionAndPanelRelease(
                                source.rosterScreenshotIndex,
                            ),
                        )
                    extractionOutcome is ExtractionOutcome.Failed ->
                        return ProcessRosterOcrResult.Failed(
                            ProcessRosterOcrFailure.UnexpectedExtraction(source.rosterScreenshotIndex),
                        )
                    releaseOutcome is ReleaseOutcome.Failed ->
                        return ProcessRosterOcrResult.Failed(
                            ProcessRosterOcrFailure.UnexpectedPanelRelease(source.rosterScreenshotIndex),
                        )
                    else -> addAll((extractionOutcome as ExtractionOutcome.Succeeded).results)
                }
            }
        }

        val parsedCandidates = try {
            parser.parse(RosterCandidateParseInput(rawExtractions.toList()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessRosterOcrResult.Failed(ProcessRosterOcrFailure.UnexpectedParser)
        }

        val associatedCandidates = try {
            associator.associate(RosterSlotAssociationInput(parsedCandidates))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessRosterOcrResult.Failed(ProcessRosterOcrFailure.UnexpectedAssociation)
        }

        val validation = try {
            validator.validate(RosterOcrValidationInput(associatedCandidates))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessRosterOcrResult.Failed(ProcessRosterOcrFailure.UnexpectedValidation)
        }

        return ProcessRosterOcrResult.Success(
            ProcessRosterOcrEvidence(
                rawExtractions = rawExtractions.toList(),
                parsedCandidates = parsedCandidates,
                associatedCandidates = associatedCandidates,
                validation = validation,
            ),
        )
    }

    private fun validateSources(
        sources: List<RosterOcrScreenshotSource>,
        tournamentId: String,
    ): SourceValidation {
        val mismatched = sources.firstOrNull { it.tournamentId != tournamentId }
        if (mismatched != null) {
            return SourceValidation.Invalid(
                RosterOcrSourceProviderResult.MismatchedTournamentContext(
                    mismatched.rosterScreenshotIndex,
                ),
            )
        }

        val unsupported = sources.firstOrNull {
            RosterScreenshotPosition.fromIndex(it.rosterScreenshotIndex) == null
        }
        if (unsupported != null) {
            return SourceValidation.Invalid(
                RosterOcrSourceProviderResult.UnsupportedScreenshotPosition(
                    unsupported.rosterScreenshotIndex,
                ),
            )
        }

        val duplicateIndices = sources
            .groupingBy { it.rosterScreenshotIndex }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        if (duplicateIndices.isNotEmpty()) {
            return SourceValidation.Invalid(
                RosterOcrSourceProviderResult.DuplicateScreenshotPositions(duplicateIndices),
            )
        }

        val expectedIndices = RosterScreenshotPosition.entries.map { it.index }
        if (sources.size != expectedIndices.size ||
            sources.map { it.rosterScreenshotIndex }.toSet() != expectedIndices.toSet() ||
            sources.any { it.screenshotPosition.index != it.rosterScreenshotIndex }
        ) {
            return SourceValidation.Invalid(RosterOcrSourceProviderResult.IncompleteScreenshotSet)
        }

        return SourceValidation.Valid(sources.sortedBy { it.rosterScreenshotIndex })
    }

    private sealed interface SourceValidation {
        data class Valid(
            val sources: List<RosterOcrScreenshotSource>,
        ) : SourceValidation

        data class Invalid(
            val failure: RosterOcrSourceProviderResult,
        ) : SourceValidation
    }

    private sealed interface ExtractionOutcome {
        data class Succeeded(
            val results: List<RosterRawOcrExtractionResult>,
        ) : ExtractionOutcome

        data class Cancelled(
            val exception: CancellationException,
        ) : ExtractionOutcome

        data object Failed : ExtractionOutcome
    }

    private sealed interface ReleaseOutcome {
        data object Succeeded : ReleaseOutcome

        data class Cancelled(
            val exception: CancellationException,
        ) : ReleaseOutcome

        data object Failed : ReleaseOutcome
    }
}
