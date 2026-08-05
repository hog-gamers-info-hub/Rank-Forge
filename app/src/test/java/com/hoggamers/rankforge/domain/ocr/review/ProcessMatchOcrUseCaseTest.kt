package com.hoggamers.rankforge.domain.ocr.review

import com.hoggamers.rankforge.data.tournament.InMemoryTournamentRepository
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrTextExtractor
import com.hoggamers.rankforge.domain.ocr.layout.OcrPixelRect
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
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCandidate
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingCrop
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingFailure
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingInput
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingResult
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingStep
import com.hoggamers.rankforge.domain.tournament.ObserveRosterByTournamentUseCase
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProcessMatchOcrUseCaseTest {

    @Test
    fun blankContextStopsBeforeSourceLoading() = runTest {
        val sourceProvider = FakeSourceProvider(
            result = MatchOcrSourceProviderResult.MetadataNotFound,
        )
        val preprocessor = FakePreprocessor(
            result = successfulPreprocessing(),
        )
        val extractor = FakeExtractor()

        val result = useCase(
            sourceProvider = sourceProvider,
            preprocessor = preprocessor,
            extractor = extractor,
        )(
            tournamentId = " ",
            matchId = MATCH_ID,
        )

        assertEquals(
            ProcessMatchOcrFailure.InvalidContext,
            result.failure(),
        )
        assertEquals(0, sourceProvider.calls)
        assertEquals(0, preprocessor.calls)
        assertEquals(0, extractor.calls)
    }

    @Test
    fun sourceProviderFailureRemainsControlled() = runTest {
        val sourceProvider = FakeSourceProvider(
            result = MatchOcrSourceProviderResult.LocalFileMissing,
        )

        val result = useCase(
            sourceProvider = sourceProvider,
        )(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        assertEquals(
            ProcessMatchOcrFailure.SourceLoading(
                MatchOcrSourceProviderResult.LocalFileMissing,
            ),
            result.failure(),
        )
        assertEquals(1, sourceProvider.calls)
    }

    @Test
    fun internalAlphaUsesOnlyCandidateOrderZero() = runTest {
        val source = FakePreparedSource()
        val sourceProvider = FakeSourceProvider(
            result = MatchOcrSourceProviderResult.Loaded(source),
        )
        val preprocessor = FakePreprocessor(
            result = successfulPreprocessing(),
        )
        val extractor = FakeExtractor(
            resultFactory = { input ->
                listOf(
                    RawOcrExtractionResult.Extracted(
                        sourceCandidate = input.candidates.single(),
                        fullText = "",
                        blocks = emptyList(),
                    ),
                )
            },
        )
        val placementParser = FakePlacementParser()
        val playerNameParser = FakePlayerNameParser()
        val killParser = FakeKillParser()
        val analyzer = FakeFailureAnalyzer()

        val result = useCase(
            sourceProvider = sourceProvider,
            preprocessor = preprocessor,
            extractor = extractor,
            placementParser = placementParser,
            playerNameParser = playerNameParser,
            killParser = killParser,
            analyzer = analyzer,
        )(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        val evidence = result.success()

        assertEquals(1, sourceProvider.calls)
        assertEquals(1, preprocessor.calls)
        assertEquals(1, extractor.calls)

        assertEquals(
            listOf(0),
            extractor.lastInput
                ?.candidates
                ?.map { it.order },
        )

        assertEquals(
            0,
            evidence.extractionResults
                .single()
                .sourceCandidate
                .order,
        )

        assertEquals(1, placementParser.calls)
        assertEquals(1, playerNameParser.calls)
        assertEquals(1, killParser.calls)
        assertEquals(1, analyzer.calls)

        assertEquals(1, source.releaseCount)

        assertEquals(
            12,
            evidence.teamIdentification?.rows?.size,
        )
    }

    @Test
    fun preprocessingFailureSurfacesHumanReviewEvidenceWithoutExtraction() = runTest {
        val source = FakePreparedSource()
        val sourceProvider = FakeSourceProvider(
            result = MatchOcrSourceProviderResult.Loaded(source),
        )
        val preprocessingFailure = OcrPreprocessingResult.Failed(
            OcrPreprocessingFailure.UNSUPPORTED_LAYOUT,
        )
        val preprocessor = FakePreprocessor(
            result = preprocessingFailure,
        )
        val extractor = FakeExtractor()
        val analyzer = FakeFailureAnalyzer()

        val result = useCase(
            sourceProvider = sourceProvider,
            preprocessor = preprocessor,
            extractor = extractor,
            analyzer = analyzer,
        )(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        val evidence = result.success()

        assertEquals(
            preprocessingFailure,
            evidence.preprocessingResult,
        )
        assertTrue(
            evidence.extractionResults.isEmpty(),
        )
        assertNull(evidence.placementResult)
        assertNull(evidence.playerNameResult)
        assertNull(evidence.killResult)
        assertNull(evidence.teamIdentification)

        assertEquals(0, extractor.calls)
        assertEquals(1, analyzer.calls)
        assertEquals(1, source.releaseCount)
    }

    @Test
    fun emptyRawOcrStopsBeforeParsersAndReturnsReviewEvidence() = runTest {
        val source = FakePreparedSource()
        val sourceProvider = FakeSourceProvider(
            result = MatchOcrSourceProviderResult.Loaded(source),
        )
        val preprocessor = FakePreprocessor(
            result = successfulPreprocessing(),
        )
        val extractor = FakeExtractor(
            resultFactory = { input ->
                listOf(
                    RawOcrExtractionResult.Empty(
                        sourceCandidate = input.candidates.single(),
                    ),
                )
            },
        )
        val placementParser = FakePlacementParser()
        val playerNameParser = FakePlayerNameParser()
        val killParser = FakeKillParser()
        val analyzer = FakeFailureAnalyzer()

        val result = useCase(
            sourceProvider = sourceProvider,
            preprocessor = preprocessor,
            extractor = extractor,
            placementParser = placementParser,
            playerNameParser = playerNameParser,
            killParser = killParser,
            analyzer = analyzer,
        )(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        val evidence = result.success()

        assertEquals(1, extractor.calls)

        assertEquals(
            listOf(0),
            extractor.lastInput
                ?.candidates
                ?.map { it.order },
        )

        assertEquals(0, placementParser.calls)
        assertEquals(0, playerNameParser.calls)
        assertEquals(0, killParser.calls)
        assertEquals(1, analyzer.calls)

        assertNull(evidence.placementResult)
        assertNull(evidence.playerNameResult)
        assertNull(evidence.killResult)
        assertNull(evidence.teamIdentification)

        assertEquals(1, source.releaseCount)
    }

    @Test
    fun missingBaselineCandidateFailsWithoutTryingAnotherCandidate() = runTest {
        val source = FakePreparedSource()
        val sourceProvider = FakeSourceProvider(
            result = MatchOcrSourceProviderResult.Loaded(source),
        )
        val preprocessor = FakePreprocessor(
            result = OcrPreprocessingResult.Candidates(
                candidates = listOf(
                    candidate(
                        order = 1,
                        steps = listOf(
                            OcrPreprocessingStep.CROP,
                            OcrPreprocessingStep.SCALE,
                        ),
                        scaleFactor = 1.5,
                    ),
                    candidate(
                        order = 2,
                        steps = listOf(
                            OcrPreprocessingStep.CROP,
                            OcrPreprocessingStep.CONTRAST_ADJUSTMENT,
                        ),
                    ),
                ),
            ),
        )
        val extractor = FakeExtractor()

        val result = useCase(
            sourceProvider = sourceProvider,
            preprocessor = preprocessor,
            extractor = extractor,
        )(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        assertEquals(
            ProcessMatchOcrFailure.MissingBaselineCandidate,
            result.failure(),
        )

        assertEquals(0, extractor.calls)
        assertEquals(1, source.releaseCount)
    }

    @Test
    fun extractionExceptionReturnsControlledFailure() = runTest {
        val source = FakePreparedSource()

        val result = useCase(
            sourceProvider = FakeSourceProvider(
                MatchOcrSourceProviderResult.Loaded(source),
            ),
            preprocessor = FakePreprocessor(
                successfulPreprocessing(),
            ),
            extractor = FakeExtractor(
                throwUnexpected = true,
            ),
        )(
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
        )

        assertEquals(
            ProcessMatchOcrFailure.UnexpectedExtraction,
            result.failure(),
        )

        assertEquals(1, source.releaseCount)
    }

    @Test
    fun cancellationFromSourceProviderPropagates() = runTest {
        val provider = FakeSourceProvider(
            result = MatchOcrSourceProviderResult.MetadataNotFound,
            throwCancellation = true,
        )

        assertCancellation {
            useCase(
                sourceProvider = provider,
            )(
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
            )
        }
    }

    private fun useCase(
        sourceProvider: MatchOcrSourceProvider =
            FakeSourceProvider(
                MatchOcrSourceProviderResult.MetadataNotFound,
            ),
        preprocessor: OcrImagePreprocessor =
            FakePreprocessor(
                successfulPreprocessing(),
            ),
        extractor: RawOcrTextExtractor =
            FakeExtractor(),
        placementParser: PlacementParser =
            FakePlacementParser(),
        playerNameParser: PlayerNameParser =
            FakePlayerNameParser(),
        killParser: KillParser =
            FakeKillParser(),
        analyzer: OcrFailureAnalyzer =
            FakeFailureAnalyzer(),
    ): ProcessMatchOcrUseCase {
        val repository =
            InMemoryTournamentRepository()

        return ProcessMatchOcrUseCase(
            sourceProvider = sourceProvider,
            preprocessor = preprocessor,
            extractor = extractor,
            placementParser = placementParser,
            playerNameParser = playerNameParser,
            killParser = killParser,
            failureAnalyzer = analyzer,
            observeRoster =
                ObserveRosterByTournamentUseCase(repository),
        )
    }

    private fun successfulPreprocessing():
        OcrPreprocessingResult.Candidates =
        OcrPreprocessingResult.Candidates(
            candidates = listOf(
                candidate(
                    order = 0,
                    steps = listOf(
                        OcrPreprocessingStep.CROP,
                    ),
                ),
                candidate(
                    order = 1,
                    steps = listOf(
                        OcrPreprocessingStep.CROP,
                        OcrPreprocessingStep.SCALE,
                    ),
                    scaleFactor = 1.5,
                ),
                candidate(
                    order = 2,
                    steps = listOf(
                        OcrPreprocessingStep.CROP,
                        OcrPreprocessingStep.CONTRAST_ADJUSTMENT,
                    ),
                ),
            ),
        )

    private fun candidate(
        order: Int,
        steps: List<OcrPreprocessingStep>,
        scaleFactor: Double? = null,
    ): OcrPreprocessingCandidate =
        OcrPreprocessingCandidate(
            order = order,
            crop = OcrPreprocessingCrop.OVERALL_SCOREBOARD,
            cropRect = OcrPixelRect(
                x = 0,
                y = 0,
                width = 1600,
                height = 720,
            ),
            image = FakeImage(),
            appliedSteps = steps,
            scaleFactor = scaleFactor,
        )

    private suspend fun assertCancellation(
        block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            Unit
        }
    }

    private fun ProcessMatchOcrResult.success():
        ProcessMatchOcrEvidence =
        when (this) {
            is ProcessMatchOcrResult.Success ->
                evidence

            is ProcessMatchOcrResult.Failed -> {
                fail(
                    "Unexpected failure: ${failure::class.simpleName}",
                )
                error("unreachable")
            }
        }

    private fun ProcessMatchOcrResult.failure():
        ProcessMatchOcrFailure =
        when (this) {
            is ProcessMatchOcrResult.Failed ->
                failure

            is ProcessMatchOcrResult.Success -> {
                fail("Expected controlled failure")
                error("unreachable")
            }
        }

    private class FakeSourceProvider(
        private val result: MatchOcrSourceProviderResult,
        private val throwCancellation: Boolean = false,
    ) : MatchOcrSourceProvider {
        var calls = 0

        override suspend fun load(
            tournamentId: String,
            matchId: String,
        ): MatchOcrSourceProviderResult {
            calls += 1

            if (throwCancellation) {
                throw CancellationException(
                    "test cancellation",
                )
            }

            return result
        }
    }

    private class FakePreparedSource :
        MatchOcrPreparedSource {
        override val image: OcrPreprocessingImage =
            FakeImage()

        var releaseCount = 0

        override fun release() {
            releaseCount += 1
        }
    }

    private data class FakeImage(
        override val width: Int = 1600,
        override val height: Int = 720,
    ) : OcrPreprocessingImage

    private class FakePreprocessor(
        private val result: OcrPreprocessingResult,
    ) : OcrImagePreprocessor {
        var calls = 0

        override suspend fun preprocess(
            input: OcrPreprocessingInput,
        ): OcrPreprocessingResult {
            calls += 1
            return result
        }
    }

    private class FakeExtractor(
        private val resultFactory: (
            RawOcrExtractionInput,
        ) -> List<RawOcrExtractionResult> = {
            emptyList()
        },
        private val throwUnexpected: Boolean = false,
    ) : RawOcrTextExtractor {
        var calls = 0
        var lastInput: RawOcrExtractionInput? = null

        override suspend fun extract(
            input: RawOcrExtractionInput,
        ): List<RawOcrExtractionResult> {
            calls += 1
            lastInput = input

            if (throwUnexpected) {
                error("test extraction failure")
            }

            return resultFactory(input)
        }
    }

    private class FakePlacementParser :
        PlacementParser {
        var calls = 0

        override fun parse(
            input: PlacementParsingInput,
        ): PlacementParsingResult {
            calls += 1
            return PlacementParsingResult(
                rows = emptyList(),
            )
        }
    }

    private class FakePlayerNameParser :
        PlayerNameParser {
        var calls = 0

        override fun parse(
            input: PlayerNameParsingInput,
        ): PlayerNameParsingResult {
            calls += 1
            return PlayerNameParsingResult(
                rows = emptyList(),
            )
        }
    }

    private class FakeKillParser :
        KillParser {
        var calls = 0

        override fun parse(
            input: KillParsingInput,
        ): KillParsingResult {
            calls += 1
            return KillParsingResult(
                rows = emptyList(),
            )
        }
    }

    private class FakeFailureAnalyzer :
        OcrFailureAnalyzer {
        var calls = 0
        var lastInput: OcrFailureAnalysisInput? = null

        override fun analyze(
            input: OcrFailureAnalysisInput,
        ): OcrFailureAnalysisResult {
            calls += 1
            lastInput = input

            return OcrFailureAnalysisResult(
                rows = emptyList(),
            )
        }
    }

    private companion object {
        const val TOURNAMENT_ID =
            "synthetic-tournament"

        const val MATCH_ID =
            "synthetic-match"
    }
}