package com.hoggamers.rankforge.domain.ocr.review

import com.hoggamers.rankforge.domain.matching.ScoreboardRowPlayerEvidenceCollector
import com.hoggamers.rankforge.domain.matching.ScoreboardTeamIdentificationEvaluation
import com.hoggamers.rankforge.domain.matching.ScoreboardTeamIdentificationEvaluator
import com.hoggamers.rankforge.domain.matching.TeamCandidateRosterInput
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrTextExtractor
import com.hoggamers.rankforge.domain.ocr.parsing.KillParser
import com.hoggamers.rankforge.domain.ocr.parsing.KillParsingInput
import com.hoggamers.rankforge.domain.ocr.parsing.KillParsingResult
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParser
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParsingInput
import com.hoggamers.rankforge.domain.ocr.parsing.PlacementParsingResult
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParser
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParsingInput
import com.hoggamers.rankforge.domain.ocr.parsing.PlayerNameParsingResult
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrImagePreprocessor
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingInput
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingResult
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import com.hoggamers.rankforge.domain.tournament.TeamSlot
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.flow.first

interface MatchOcrPreparedSource {
    val image: OcrPreprocessingImage

    fun release()
}

sealed interface MatchOcrSourceProviderResult {
    data class Loaded(
        val source: MatchOcrPreparedSource,
    ) : MatchOcrSourceProviderResult

    data object InvalidContext : MatchOcrSourceProviderResult
    data object MetadataNotFound : MatchOcrSourceProviderResult
    data object TournamentMismatch : MatchOcrSourceProviderResult
    data object LocalFileMissing : MatchOcrSourceProviderResult
    data object UnreadableImage : MatchOcrSourceProviderResult
    data object UnsafeImage : MatchOcrSourceProviderResult
    data object LoadingFailure : MatchOcrSourceProviderResult
}

interface MatchOcrSourceProvider {
    suspend fun load(
        tournamentId: String,
        matchId: String,
    ): MatchOcrSourceProviderResult
}

data class ProcessMatchOcrEvidence(
    val preprocessingResult: OcrPreprocessingResult,
    val extractionResults: List<RawOcrExtractionResult>,
    val placementResult: PlacementParsingResult?,
    val playerNameResult: PlayerNameParsingResult?,
    val killResult: KillParsingResult?,
    val reviewResult: OcrFailureAnalysisResult,
    val teamIdentification: ScoreboardTeamIdentificationEvaluation?,
)

sealed interface ProcessMatchOcrFailure {
    data object InvalidContext : ProcessMatchOcrFailure

    data class SourceLoading(
        val result: MatchOcrSourceProviderResult,
    ) : ProcessMatchOcrFailure

    data object MissingBaselineCandidate : ProcessMatchOcrFailure
    data object UnexpectedPreprocessing : ProcessMatchOcrFailure
    data object UnexpectedExtraction : ProcessMatchOcrFailure
    data object UnexpectedPlacementParsing : ProcessMatchOcrFailure
    data object UnexpectedPlayerNameParsing : ProcessMatchOcrFailure
    data object UnexpectedKillParsing : ProcessMatchOcrFailure
    data object UnexpectedReviewAnalysis : ProcessMatchOcrFailure
    data object RosterLoadingFailure : ProcessMatchOcrFailure
    data object UnexpectedTeamIdentification : ProcessMatchOcrFailure
}

sealed interface ProcessMatchOcrResult {
    data class Success(
        val evidence: ProcessMatchOcrEvidence,
    ) : ProcessMatchOcrResult

    data class Failed(
        val failure: ProcessMatchOcrFailure,
    ) : ProcessMatchOcrResult
}

fun interface MatchOcrProcessor {
    suspend operator fun invoke(
        tournamentId: String,
        matchId: String,
    ): ProcessMatchOcrResult
}

class ProcessMatchOcrUseCase @Inject constructor(
    private val sourceProvider: MatchOcrSourceProvider,
    private val preprocessor: OcrImagePreprocessor,
    private val extractor: RawOcrTextExtractor,
    private val placementParser: PlacementParser,
    private val playerNameParser: PlayerNameParser,
    private val killParser: KillParser,
    private val failureAnalyzer: OcrFailureAnalyzer,
    private val observeRoster: ObserveRosterByTournamentUseCase,
) : MatchOcrProcessor {

    override suspend operator fun invoke(
        tournamentId: String,
        matchId: String,
    ): ProcessMatchOcrResult {
        if (tournamentId.isBlank() || matchId.isBlank()) {
            return ProcessMatchOcrResult.Failed(ProcessMatchOcrFailure.InvalidContext)
        }

        val loaded = try {
            sourceProvider.load(tournamentId, matchId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.SourceLoading(
                    MatchOcrSourceProviderResult.LoadingFailure,
                ),
            )
        }

        val source = when (loaded) {
            is MatchOcrSourceProviderResult.Loaded -> loaded.source
            else -> {
                return ProcessMatchOcrResult.Failed(
                    ProcessMatchOcrFailure.SourceLoading(loaded),
                )
            }
        }

        val preprocessingResult = try {
            preprocessor.preprocess(
                OcrPreprocessingInput(source.image),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.UnexpectedPreprocessing,
            )
        } finally {
            runCatching { source.release() }
        }

        if (preprocessingResult is OcrPreprocessingResult.Failed) {
            val reviewResult = try {
                failureAnalyzer.analyze(
                    OcrFailureAnalysisInput(
                        preprocessingResult = preprocessingResult,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return ProcessMatchOcrResult.Failed(
                    ProcessMatchOcrFailure.UnexpectedReviewAnalysis,
                )
            }

            return ProcessMatchOcrResult.Success(
                ProcessMatchOcrEvidence(
                    preprocessingResult = preprocessingResult,
                    extractionResults = emptyList(),
                    placementResult = null,
                    playerNameResult = null,
                    killResult = null,
                    reviewResult = reviewResult,
                    teamIdentification = null,
                ),
            )
        }

        val candidates = (preprocessingResult as OcrPreprocessingResult.Candidates).candidates

        /*
         * v0.13.0 Internal Alpha policy:
         *
         * Candidate order 0 is the only approved Match OCR candidate.
         * Scaled and contrast-enhanced retry candidates remain isolated and are
         * not selected, merged, compared, or scored during this version.
         */
        val baselineCandidate = candidates.singleOrNull {
            it.order == INTERNAL_ALPHA_BASELINE_CANDIDATE_ORDER
        } ?: return ProcessMatchOcrResult.Failed(
            ProcessMatchOcrFailure.MissingBaselineCandidate,
        )

        val extractionResults = try {
            extractor.extract(
                RawOcrExtractionInput(
                    candidates = listOf(baselineCandidate),
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.UnexpectedExtraction,
            )
        }

        if (extractionResults.size != 1) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.UnexpectedExtraction,
            )
        }

        val extraction = extractionResults.single()

        if (extraction !is RawOcrExtractionResult.Extracted) {
            val reviewResult = try {
                failureAnalyzer.analyze(
                    OcrFailureAnalysisInput(
                        preprocessingResult = preprocessingResult,
                        extractionResults = extractionResults,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return ProcessMatchOcrResult.Failed(
                    ProcessMatchOcrFailure.UnexpectedReviewAnalysis,
                )
            }

            return ProcessMatchOcrResult.Success(
                ProcessMatchOcrEvidence(
                    preprocessingResult = preprocessingResult,
                    extractionResults = extractionResults,
                    placementResult = null,
                    playerNameResult = null,
                    killResult = null,
                    reviewResult = reviewResult,
                    teamIdentification = null,
                ),
            )
        }

        val placementResult = try {
            placementParser.parse(
                PlacementParsingInput(
                    extractions = extractionResults,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.UnexpectedPlacementParsing,
            )
        }

        val playerNameResult = try {
            playerNameParser.parse(
                PlayerNameParsingInput(
                    extractions = extractionResults,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.UnexpectedPlayerNameParsing,
            )
        }

        val killResult = try {
            killParser.parse(
                KillParsingInput(
                    extractions = extractionResults,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.UnexpectedKillParsing,
            )
        }

        val reviewResult = try {
            failureAnalyzer.analyze(
                OcrFailureAnalysisInput(
                    preprocessingResult = preprocessingResult,
                    extractionResults = extractionResults,
                    placementResult = placementResult,
                    playerNameResult = playerNameResult,
                    killResult = killResult,
                ),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.UnexpectedReviewAnalysis,
            )
        }

        val rosterBySlot = try {
            observeRoster(tournamentId).first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.RosterLoadingFailure,
            )
        }

        val candidateTeams = TeamSlot.SLOT_NUMBERS.map { teamSlot ->
            TeamCandidateRosterInput(
                teamSlot = teamSlot,
                rosterPlayerNames = rosterBySlot[teamSlot]
                    .orEmpty()
                    .map { player -> player.displayName },
            )
        }

        val teamIdentification = try {
            ScoreboardTeamIdentificationEvaluator.evaluate(
                rowEvidence = ScoreboardRowPlayerEvidenceCollector().collect(extraction),
                candidateTeams = candidateTeams,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return ProcessMatchOcrResult.Failed(
                ProcessMatchOcrFailure.UnexpectedTeamIdentification,
            )
        }

        return ProcessMatchOcrResult.Success(
            ProcessMatchOcrEvidence(
                preprocessingResult = preprocessingResult,
                extractionResults = extractionResults,
                placementResult = placementResult,
                playerNameResult = playerNameResult,
                killResult = killResult,
                reviewResult = reviewResult,
                teamIdentification = teamIdentification,
            ),
        )
    }

    private companion object {
        const val INTERNAL_ALPHA_BASELINE_CANDIDATE_ORDER = 0
    }
}