package com.hoggamers.rankforge.domain.ocr.review

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRepository
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
import com.hoggamers.rankforge.domain.auth.AuthUser
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionInput
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractionResult
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrFailure
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrExtractor
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionIdentity
import com.hoggamers.rankforge.domain.ocr.extraction.RosterRawOcrRegionType
import com.hoggamers.rankforge.domain.ocr.layout.CroppedRosterPanelInput
import com.hoggamers.rankforge.domain.ocr.layout.RosterScreenshotPosition
import com.hoggamers.rankforge.domain.ocr.layout.RosterVisibleSlotPosition
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParseResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterCandidateParser
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidationStatus
import com.hoggamers.rankforge.domain.ocr.parsing.RosterOcrValidator
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationInput
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociationResult
import com.hoggamers.rankforge.domain.ocr.parsing.RosterSlotAssociator
import com.hoggamers.rankforge.domain.ocr.preprocessing.OcrPreprocessingImage
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class ProcessRosterOcrUseCaseTest {
    @Test
    fun processesAllSourcesInOrderAndPreservesEveryPipelineResult() = runTest {
        val provider = FakeSourceProvider(
            RosterOcrSourceProviderResult.Loaded(listOf(source(3), source(1), source(2))),
        )
        val preparer = FakePanelPreparer()
        val extractions = mapOf(
            1 to listOf(emptyExtraction(1)),
            2 to listOf(
                RosterRawOcrExtractionResult.Failed(RosterRawOcrFailure.RECOGNIZER_FAILED),
            ),
            3 to listOf(emptyExtraction(3)),
        )
        val extractor = FakeExtractor(extractions)
        val parsed = RosterCandidateParseResult(emptyList(), emptyList())
        val associated = RosterSlotAssociationResult(emptyList(), emptyList())
        val validation = RosterOcrValidationResult(
            status = RosterOcrValidationStatus.READY_FOR_REVIEW,
            slotResults = emptyList(),
            globalIssues = emptyList(),
        )
        val parser = FakeParser(parsed)
        val associator = FakeAssociator(associated)
        val validator = FakeValidator(validation)

        val result = useCase(provider, preparer, extractor, parser, associator, validator)(TOURNAMENT_ID)
        val evidence = result.success()

        assertEquals(1, provider.calls)
        assertEquals(listOf(1, 2, 3), preparer.indices)
        assertEquals(listOf(1, 2, 3), extractor.indices)
        assertEquals(extractions.values.flatten(), evidence.rawExtractions)
        assertEquals(1, parser.calls)
        assertEquals(extractions.values.flatten(), parser.input?.extractions)
        assertEquals(parsed, evidence.parsedCandidates)
        assertEquals(1, associator.calls)
        assertEquals(parsed, associator.input?.parsedCandidates)
        assertEquals(associated, evidence.associatedCandidates)
        assertEquals(1, validator.calls)
        assertEquals(associated, validator.input?.associationResult)
        assertEquals(validation, evidence.validation)
        assertEquals(listOf(1, 1, 1), preparer.panels.map { it.releaseCount })
    }

    @Test
    fun blankTournamentIdStopsBeforeSourceLoading() = runTest {
        val provider = FakeSourceProvider(RosterOcrSourceProviderResult.Loaded(sources()))
        val preparer = FakePanelPreparer()
        val extractor = FakeExtractor(emptyMap())

        val result = useCase(provider, preparer, extractor)("  ")

        assertEquals(ProcessRosterOcrFailure.InvalidTournamentContext, result.failure())
        assertEquals(0, provider.calls)
        assertEquals(0, preparer.calls)
        assertEquals(0, extractor.calls)
    }

    @Test
    fun mismatchedTournamentContextReportsIndexBeforePreparation() = runTest {
        val provider = FakeSourceProvider(
            RosterOcrSourceProviderResult.Loaded(
                listOf(source(1), source(2).copy(tournamentId = "foreign-tournament"), source(3)),
            ),
        )
        val preparer = FakePanelPreparer()
        val extractor = FakeExtractor(emptyMap())

        val result = useCase(provider, preparer, extractor)(TOURNAMENT_ID)

        assertEquals(
            ProcessRosterOcrFailure.SourceLoading(
                RosterOcrSourceProviderResult.MismatchedTournamentContext(2),
            ),
            result.failure(),
        )
        assertEquals(1, provider.calls)
        assertEquals(0, preparer.calls)
        assertEquals(0, extractor.calls)
    }

    @Test
    fun incompleteSourceSetStopsBeforePreparation() = runTest {
        val provider = FakeSourceProvider(
            RosterOcrSourceProviderResult.Loaded(listOf(source(1), source(2))),
        )
        val preparer = FakePanelPreparer()

        val result = useCase(provider, preparer, FakeExtractor(emptyMap()))(TOURNAMENT_ID)

        assertEquals(
            ProcessRosterOcrFailure.SourceLoading(RosterOcrSourceProviderResult.IncompleteScreenshotSet),
            result.failure(),
        )
        assertEquals(0, preparer.calls)
    }

    @Test
    fun duplicateScreenshotPositionStopsBeforePreparation() = runTest {
        val provider = FakeSourceProvider(
            RosterOcrSourceProviderResult.Loaded(listOf(source(1), source(1), source(3))),
        )
        val preparer = FakePanelPreparer()

        val result = useCase(provider, preparer, FakeExtractor(emptyMap()))(TOURNAMENT_ID)

        assertEquals(
            ProcessRosterOcrFailure.SourceLoading(
                RosterOcrSourceProviderResult.DuplicateScreenshotPositions(listOf(1)),
            ),
            result.failure(),
        )
        assertEquals(0, preparer.calls)
    }

    @Test
    fun unsupportedScreenshotPositionStopsBeforePreparation() = runTest {
        val unsupported = source(4, RosterScreenshotPosition.ONE)
        val provider = FakeSourceProvider(
            RosterOcrSourceProviderResult.Loaded(listOf(source(1), source(2), unsupported)),
        )
        val preparer = FakePanelPreparer()

        val result = useCase(provider, preparer, FakeExtractor(emptyMap()))(TOURNAMENT_ID)

        assertEquals(
            ProcessRosterOcrFailure.SourceLoading(
                RosterOcrSourceProviderResult.UnsupportedScreenshotPosition(4),
            ),
            result.failure(),
        )
        assertEquals(0, preparer.calls)
    }

    @Test
    fun sourceProviderFailureRemainsControlled() = runTest {
        val provider = FakeSourceProvider(RosterOcrSourceProviderResult.MissingCropMetadata(2))

        val result = useCase(provider, FakePanelPreparer(), FakeExtractor(emptyMap()))(TOURNAMENT_ID)

        assertEquals(
            ProcessRosterOcrFailure.SourceLoading(RosterOcrSourceProviderResult.MissingCropMetadata(2)),
            result.failure(),
        )
    }

    @Test
    fun panelPreparationFailureReportsIndexAndStopsLaterSources() = runTest {
        val provider = FakeSourceProvider(RosterOcrSourceProviderResult.Loaded(sources()))
        val preparer = FakePanelPreparer(failureAt = 2)
        val extractor = FakeExtractor(sources().associate { it.rosterScreenshotIndex to emptyList() })

        val result = useCase(provider, preparer, extractor)(TOURNAMENT_ID)

        assertEquals(
            ProcessRosterOcrFailure.PanelPreparation(
                2,
                RosterOcrPanelPreparationFailure.CROP_FAILURE,
            ),
            result.failure(),
        )
        assertEquals(listOf(1, 2), preparer.indices)
        assertEquals(listOf(1), extractor.indices)
        assertEquals(1, preparer.panels.single().releaseCount)
    }

    @Test
    fun everyTypedPanelPreparationFailureRemainsControlled() = runTest {
        val failures = RosterOcrPanelPreparationFailure.entries

        failures.forEach { failure ->
            val result = useCase(
                FakeSourceProvider(RosterOcrSourceProviderResult.Loaded(sources())),
                FakePanelPreparer(failureAt = 1, failure = failure),
                FakeExtractor(emptyMap()),
            )(TOURNAMENT_ID)

            assertEquals(ProcessRosterOcrFailure.PanelPreparation(1, failure), result.failure())
        }
    }

    @Test
    fun extractionExceptionIsControlledAndPreparedPanelsAreReleased() = runTest {
        val provider = FakeSourceProvider(RosterOcrSourceProviderResult.Loaded(sources()))
        val preparer = FakePanelPreparer()
        val extractor = FakeExtractor(
            results = sources().associate { it.rosterScreenshotIndex to emptyList() },
            throwAt = 2,
        )

        val result = useCase(provider, preparer, extractor)(TOURNAMENT_ID)

        assertEquals(ProcessRosterOcrFailure.UnexpectedExtraction(2), result.failure())
        assertEquals(listOf(1, 2), extractor.indices)
        assertEquals(listOf(1, 1), preparer.panels.map { it.releaseCount })
    }

    @Test
    fun successfulExtractionWithReleaseExceptionReturnsTypedReleaseFailure() = runTest {
        val preparer = FakePanelPreparer(releaseBehavior = FakeReleaseBehavior.UNEXPECTED)
        val result = useCase(
            FakeSourceProvider(RosterOcrSourceProviderResult.Loaded(sources())),
            preparer,
            FakeExtractor(sources().associate { it.rosterScreenshotIndex to emptyList() }),
        )(TOURNAMENT_ID)

        assertEquals(ProcessRosterOcrFailure.UnexpectedPanelRelease(1), result.failure())
        assertEquals(1, preparer.panels.single().releaseCount)
        assertFalse(result.failure().toString().contains("private release failure"))
    }

    @Test
    fun extractionAndReleaseExceptionsReturnCombinedTypedFailure() = runTest {
        val preparer = FakePanelPreparer(releaseBehavior = FakeReleaseBehavior.UNEXPECTED)
        val result = useCase(
            FakeSourceProvider(RosterOcrSourceProviderResult.Loaded(sources())),
            preparer,
            FakeExtractor(
                sources().associate { it.rosterScreenshotIndex to emptyList() },
                throwAt = 1,
            ),
        )(TOURNAMENT_ID)

        assertEquals(ProcessRosterOcrFailure.UnexpectedExtractionAndPanelRelease(1), result.failure())
        assertEquals(1, preparer.panels.single().releaseCount)
        assertFalse(result.failure().toString().contains("private"))
    }

    @Test
    fun extractionCancellationWinsOverUnexpectedReleaseFailure() = runTest {
        val preparer = FakePanelPreparer(releaseBehavior = FakeReleaseBehavior.UNEXPECTED)

        assertCancellation {
            useCase(
                FakeSourceProvider(RosterOcrSourceProviderResult.Loaded(sources())),
                preparer,
                FakeExtractor(emptyMap(), throwCancellationAt = 1),
            )(TOURNAMENT_ID)
        }
        assertEquals(1, preparer.panels.single().releaseCount)
    }

    @Test
    fun releaseCancellationPropagates() = runTest {
        val preparer = FakePanelPreparer(releaseBehavior = FakeReleaseBehavior.CANCELLATION)

        assertCancellation {
            useCase(
                FakeSourceProvider(RosterOcrSourceProviderResult.Loaded(sources())),
                preparer,
                FakeExtractor(sources().associate { it.rosterScreenshotIndex to emptyList() }),
            )(TOURNAMENT_ID)
        }
        assertEquals(1, preparer.panels.single().releaseCount)
    }

    @Test
    fun parserAssociationAndValidationFailuresAreStageSpecific() = runTest {
        val provider = FakeSourceProvider(RosterOcrSourceProviderResult.Loaded(sources()))
        val preparer = FakePanelPreparer()
        val extractor = FakeExtractor(sources().associate { it.rosterScreenshotIndex to emptyList() })

        assertEquals(
            ProcessRosterOcrFailure.UnexpectedParser,
            useCase(provider, preparer, extractor, FakeParser(throwUnexpected = true))(TOURNAMENT_ID).failure(),
        )
        assertEquals(
            ProcessRosterOcrFailure.UnexpectedAssociation,
            useCase(
                provider,
                preparer,
                extractor,
                associator = FakeAssociator(throwUnexpected = true),
            )(TOURNAMENT_ID).failure(),
        )
        assertEquals(
            ProcessRosterOcrFailure.UnexpectedValidation,
            useCase(
                provider,
                preparer,
                extractor,
                validator = FakeValidator(throwUnexpected = true),
            )(TOURNAMENT_ID).failure(),
        )
    }

    @Test
    fun cancellationFromEachPipelineStagePropagates() = runTest {
        val loaded = RosterOcrSourceProviderResult.Loaded(sources())

        assertCancellation {
            useCase(FakeSourceProvider(loaded, throwCancellation = true), FakePanelPreparer(), FakeExtractor(emptyMap()))(
                TOURNAMENT_ID,
            )
        }
        assertCancellation {
            useCase(
                FakeSourceProvider(loaded),
                FakePanelPreparer(throwCancellationAt = 1),
                FakeExtractor(emptyMap()),
            )(TOURNAMENT_ID)
        }
        val extractionCancellationPreparer = FakePanelPreparer()
        assertCancellation {
            useCase(
                FakeSourceProvider(loaded),
                extractionCancellationPreparer,
                FakeExtractor(emptyMap(), throwCancellationAt = 1),
            )(TOURNAMENT_ID)
        }
        assertEquals(1, extractionCancellationPreparer.panels.single().releaseCount)
        assertCancellation {
            useCase(
                FakeSourceProvider(loaded),
                FakePanelPreparer(),
                FakeExtractor(emptyMap()),
                parser = FakeParser(throwCancellation = true),
            )(TOURNAMENT_ID)
        }
        assertCancellation {
            useCase(
                FakeSourceProvider(loaded),
                FakePanelPreparer(),
                FakeExtractor(emptyMap()),
                associator = FakeAssociator(throwCancellation = true),
            )(TOURNAMENT_ID)
        }
        assertCancellation {
            useCase(
                FakeSourceProvider(loaded),
                FakePanelPreparer(),
                FakeExtractor(emptyMap()),
                validator = FakeValidator(throwCancellation = true),
            )(TOURNAMENT_ID)
        }
    }

    private fun useCase(
        provider: FakeSourceProvider,
        preparer: FakePanelPreparer,
        extractor: FakeExtractor,
        parser: FakeParser = FakeParser(),
        associator: FakeAssociator = FakeAssociator(),
        validator: FakeValidator = FakeValidator(),
    ) = ProcessRosterOcrUseCase(
        provider,
        preparer,
        extractor,
        parser,
        associator,
        validator,
        FakeAuthRepository,
    )

    private object FakeAuthRepository : AuthRepository {
        override fun observeAuthState() = flowOf(AuthState.SignedIn(AuthUser("owner-a", "owner-a@example.test")))
        override suspend fun restoreSession() = AuthRestorationResult.NoSavedSession
        override suspend fun signUp(email: String, password: String) = AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun login(email: String, password: String) = AuthOperationResult.Success(AuthSuccessOutcome.SignedIn)
        override suspend fun logout() = AuthOperationResult.Success(AuthSuccessOutcome.SignedOutLocally)
    }

    private suspend fun assertCancellation(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            Unit
        }
    }

    private fun ProcessRosterOcrResult.success(): ProcessRosterOcrEvidence = when (this) {
        is ProcessRosterOcrResult.Success -> evidence
        is ProcessRosterOcrResult.Failed -> {
            fail("Unexpected failure: ${failure::class.simpleName}")
            error("unreachable")
        }
    }

    private fun ProcessRosterOcrResult.failure(): ProcessRosterOcrFailure = when (this) {
        is ProcessRosterOcrResult.Failed -> failure
        is ProcessRosterOcrResult.Success -> {
            fail("Expected a controlled failure")
            error("unreachable")
        }
    }

    private fun sources(): List<RosterOcrScreenshotSource> = (1..3).map(::source)

    private fun source(
        index: Int,
        screenshotPosition: RosterScreenshotPosition = RosterScreenshotPosition.fromIndex(index)
            ?: RosterScreenshotPosition.ONE,
    ) = RosterOcrScreenshotSource(
        tournamentId = TOURNAMENT_ID,
        rosterScreenshotIndex = index,
        screenshotPosition = screenshotPosition,
        localRelativePath = RosterOcrLocalRelativePath("synthetic-$index.jpg"),
        sourceWidth = 1000,
        sourceHeight = 800,
        cropLeft = 0.0,
        cropTop = 0.0,
        cropRight = 1.0,
        cropBottom = 1.0,
    )

    private fun emptyExtraction(index: Int): RosterRawOcrExtractionResult.Empty =
        RosterRawOcrExtractionResult.Empty(
            RosterRawOcrRegionIdentity(
                screenshotPosition = RosterScreenshotPosition.fromIndex(index)!!,
                visibleSlotPosition = RosterVisibleSlotPosition.TOP_LEFT,
                regionType = RosterRawOcrRegionType.PLAYER_ROW,
                playerRowIndex = 1,
            ),
        )

    private class FakeSourceProvider(
        private val result: RosterOcrSourceProviderResult,
        private val throwCancellation: Boolean = false,
    ) : RosterOcrSourceProvider {
        var calls = 0

        override suspend fun load(tournamentId: String): RosterOcrSourceProviderResult {
            calls++
            if (throwCancellation) throw CancellationException()
            return result
        }

        override suspend fun load(
            tournamentId: String,
            expectedOwnerUserId: String,
        ): RosterOcrSourceProviderResult = load(tournamentId)
    }

    private class FakePanelPreparer(
        private val failureAt: Int? = null,
        private val failure: RosterOcrPanelPreparationFailure = RosterOcrPanelPreparationFailure.CROP_FAILURE,
        private val throwCancellationAt: Int? = null,
        private val releaseBehavior: FakeReleaseBehavior = FakeReleaseBehavior.SUCCESS,
    ) : RosterOcrPanelPreparer {
        var calls = 0
        val indices = mutableListOf<Int>()
        val panels = mutableListOf<FakePanel>()

        override suspend fun prepare(source: RosterOcrScreenshotSource): RosterOcrPanelPreparationResult {
            calls++
            indices += source.rosterScreenshotIndex
            if (throwCancellationAt == source.rosterScreenshotIndex) throw CancellationException()
            if (failureAt == source.rosterScreenshotIndex) {
                return RosterOcrPanelPreparationResult.Failed(failure)
            }
            return FakePanel(source, releaseBehavior).also { panels += it }
                .let(RosterOcrPanelPreparationResult::Prepared)
        }
    }

    private class FakeExtractor(
        private val results: Map<Int, List<RosterRawOcrExtractionResult>>,
        private val throwAt: Int? = null,
        private val throwCancellationAt: Int? = null,
    ) : RosterRawOcrExtractor {
        var calls = 0
        val indices = mutableListOf<Int>()

        override suspend fun extract(input: RosterRawOcrExtractionInput): List<RosterRawOcrExtractionResult> {
            calls++
            val index = input.croppedPanelInput.screenshotPosition?.index ?: -1
            indices += index
            if (throwCancellationAt == index) throw CancellationException()
            if (throwAt == index) throw IllegalStateException("synthetic extractor failure")
            return results[index].orEmpty()
        }
    }

    private class FakeParser(
        private val result: RosterCandidateParseResult = RosterCandidateParseResult(emptyList(), emptyList()),
        private val throwUnexpected: Boolean = false,
        private val throwCancellation: Boolean = false,
    ) : RosterCandidateParser {
        var calls = 0
        var input: RosterCandidateParseInput? = null

        override fun parse(input: RosterCandidateParseInput): RosterCandidateParseResult {
            calls++
            this.input = input
            if (throwCancellation) throw CancellationException()
            if (throwUnexpected) throw IllegalStateException("synthetic parser failure")
            return result
        }
    }

    private class FakeAssociator(
        private val result: RosterSlotAssociationResult = RosterSlotAssociationResult(emptyList(), emptyList()),
        private val throwUnexpected: Boolean = false,
        private val throwCancellation: Boolean = false,
    ) : RosterSlotAssociator {
        var calls = 0
        var input: RosterSlotAssociationInput? = null

        override fun associate(input: RosterSlotAssociationInput): RosterSlotAssociationResult {
            calls++
            this.input = input
            if (throwCancellation) throw CancellationException()
            if (throwUnexpected) throw IllegalStateException("synthetic association failure")
            return result
        }
    }

    private class FakeValidator(
        private val result: RosterOcrValidationResult = RosterOcrValidationResult(
            RosterOcrValidationStatus.READY_FOR_REVIEW,
            emptyList(),
            emptyList(),
        ),
        private val throwUnexpected: Boolean = false,
        private val throwCancellation: Boolean = false,
    ) : RosterOcrValidator {
        var calls = 0
        var input: RosterOcrValidationInput? = null

        override fun validate(input: RosterOcrValidationInput): RosterOcrValidationResult {
            calls++
            this.input = input
            if (throwCancellation) throw CancellationException()
            if (throwUnexpected) throw IllegalStateException("synthetic validation failure")
            return result
        }
    }

    private class FakePanel(
        source: RosterOcrScreenshotSource,
        private val releaseBehavior: FakeReleaseBehavior,
    ) : RosterOcrPreparedPanel {
        override val croppedPanelImage: OcrPreprocessingImage = FakeImage
        override val croppedPanelInput = CroppedRosterPanelInput(
            screenshotPosition = source.screenshotPosition,
            isPreparedRosterCrop = true,
            imageWidth = 1000,
            imageHeight = 800,
        )
        var releaseCount = 0

        override fun release() {
            releaseCount++
            when (releaseBehavior) {
                FakeReleaseBehavior.SUCCESS -> Unit
                FakeReleaseBehavior.UNEXPECTED -> throw IllegalStateException("private release failure")
                FakeReleaseBehavior.CANCELLATION -> throw CancellationException()
            }
        }
    }

    private enum class FakeReleaseBehavior {
        SUCCESS,
        UNEXPECTED,
        CANCELLATION,
    }

    private object FakeImage : OcrPreprocessingImage {
        override val width = 1000
        override val height = 800
    }

    private companion object {
        const val TOURNAMENT_ID = "synthetic-tournament"
    }
}
