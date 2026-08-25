package com.hoggamers.rankforge.data.ocr.matchlobby

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerDualOcrResult
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrConsensusStatus
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPpPlayerCandidateSelection
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPpPlayerCandidateSelector
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPpPlayerTextRegion
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrEngine
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrEngineEvidence
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrTextFragment
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerNameOcrSource
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOcrDetectionBox
import com.paddle.ocr.PaddleOcrDiagnosticsListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val TEMP_PP_PLAYER_ROW_TAG = "TEMP_PP_PLAYER_ROW"

data class LobbyPlayerPpOcrRecognition(
    val rawText: String,
    val fragments: List<LobbyPlayerOcrTextFragment>,
    val regions: List<LobbyPpPlayerTextRegion> = emptyList(),
)

interface LobbyPlayerPpOcrRuntime {
    suspend fun recognize(
        bitmap: Bitmap,
        diagnosticsListener: PaddleOcrDiagnosticsListener? = null,
    ): LobbyPlayerPpOcrRecognition
}

@Singleton
class AndroidLobbyPlayerPpOcrRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LobbyPlayerPpOcrRuntime {
    private val mutex = Mutex()
    private var paddleOcr: PaddleOCR? = null

    override suspend fun recognize(
        bitmap: Bitmap,
        diagnosticsListener: PaddleOcrDiagnosticsListener?,
    ): LobbyPlayerPpOcrRecognition = mutex.withLock {
        val engine = paddleOcr ?: PaddleOCR.create(context).also {
            paddleOcr = it
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "ppSessionInitialized detModelLoaded=true recModelLoaded=true dictionaryLoaded=true onnxSessionsCreated=true coldLoadTimeMs=${it.coldLoadTimeMs}",
            )
        }
        var detectorBoxCount: Int? = null
        val listener = diagnosticsListener?.let { delegate ->
            object : PaddleOcrDiagnosticsListener by delegate {
                override fun onDetectionComplete(
                    inputWidth: Int,
                    inputHeight: Int,
                    boxes: List<PaddleOcrDetectionBox>,
                ) {
                    detectorBoxCount = boxes.size
                    delegate.onDetectionComplete(inputWidth, inputHeight, boxes)
                }
            }
        }
        val result = engine.recognize(bitmap, listener)
        if (detectorBoxCount == 0) {
            val directResult = engine.diagnosticRecognizeDirect(bitmap, listener)
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "directRecognitionReturned resultCount=${directResult.size}",
            )
        }
        LobbyPlayerPpOcrRecognition(
            rawText = result.results.joinToString(" ") { it.text },
            fragments = result.results.map { resultItem ->
                val points = resultItem.box.points
                LobbyPlayerOcrTextFragment(
                    text = resultItem.text,
                    boundingBox = RawOcrBoundingBox(
                        left = points.minOf { it.x }.toInt(),
                        top = points.minOf { it.y }.toInt(),
                        right = points.maxOf { it.x }.toInt(),
                        bottom = points.maxOf { it.y }.toInt(),
                    ),
                    confidence = resultItem.confidence,
                )
            },
            regions = result.results.mapIndexed { index, resultItem ->
                val points = resultItem.box.points
                LobbyPpPlayerTextRegion(
                    index = index,
                    bounds = RawOcrBoundingBox(
                        left = points.minOf { it.x }.toInt(),
                        top = points.minOf { it.y }.toInt(),
                        right = points.maxOf { it.x }.toInt(),
                        bottom = points.maxOf { it.y }.toInt(),
                    ),
                    text = resultItem.text,
                    confidence = resultItem.confidence,
                )
            },
        )
    }
}

interface LobbyPlayerDualOcrRunner {
    suspend fun run(
        teamSlotNumber: Int,
        row: LobbyPlayerRow,
        rowBounds: LobbyPlayerRowCropBounds,
        slotAnchorSource: LobbySlotAnchorSource,
        slotAnchorY: Double,
        bitmap: Bitmap,
    ): LobbyPlayerDualOcrResult
}

@Singleton
class AndroidLobbyPlayerDualOcrRunner @Inject constructor(
    private val recognizerFactory: MlKitTextRecognizerFactory,
    private val ppRuntime: LobbyPlayerPpOcrRuntime,
) : LobbyPlayerDualOcrRunner {
    override suspend fun run(
        teamSlotNumber: Int,
        row: LobbyPlayerRow,
        rowBounds: LobbyPlayerRowCropBounds,
        slotAnchorSource: LobbySlotAnchorSource,
        slotAnchorY: Double,
        bitmap: Bitmap,
    ): LobbyPlayerDualOcrResult {
        val ppEvidence = try {
            recognizePp(teamSlotNumber, row, bitmap)
        } catch (cancellation: CancellationException) {
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=${row.ordinal + 1} cancelled stage=pp",
            )
            throw cancellation
        } catch (failure: Throwable) {
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=${row.ordinal + 1} failureType=${failure.javaClass.simpleName}",
            )
            LobbyPlayerOcrEngineEvidence(
                engine = LobbyPlayerOcrEngine.PP_OCRV6,
                rawText = "",
                candidateText = null,
                failureType = failure.javaClass.simpleName,
                failureMessage = failure.message,
            )
        }
        val ppCandidate = ppEvidence.candidateText?.takeIf { it.isNotBlank() }
        if (ppCandidate != null) {
            logPhase2dDecision(
                teamSlotNumber = teamSlotNumber,
                rowNumber = row.ordinal + 1,
                ppCandidate = ppCandidate,
                ppUsable = true,
                mlFallbackInvoked = false,
                finalText = ppCandidate,
                selectedSource = LobbyPlayerNameOcrSource.PP_PRIMARY,
                ppFailureType = ppEvidence.failureType,
            )
            return LobbyPlayerDualOcrResult(
                teamSlotNumber = teamSlotNumber,
                row = row,
                rowBounds = rowBounds,
                slotAnchorSource = slotAnchorSource,
                slotAnchorY = slotAnchorY,
                mlEvidence = null,
                ppEvidence = ppEvidence,
                resolvedText = ppCandidate,
                consensusStatus = LobbyPlayerOcrConsensusStatus.PP_ONLY,
                selectedSource = LobbyPlayerNameOcrSource.PP_PRIMARY,
                finalText = ppCandidate,
            )
        }

        Log.w(
            TEMP_PP_PLAYER_ROW_TAG,
            "teamSlot=$teamSlotNumber row=${row.ordinal + 1} mlFallbackInvoked=true",
        )
        val mlEvidence = try {
            recognizeMl(bitmap)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            LobbyPlayerOcrEngineEvidence(
                engine = LobbyPlayerOcrEngine.ML_KIT,
                rawText = "",
                candidateText = null,
                failureType = failure.javaClass.simpleName,
                failureMessage = failure.message,
            )
        }
        val mlCandidate = mlEvidence.candidateText?.takeIf { it.isNotBlank() }
        val source = if (mlCandidate == null) LobbyPlayerNameOcrSource.MISSING else LobbyPlayerNameOcrSource.ML_FALLBACK
        logPhase2dDecision(
            teamSlotNumber = teamSlotNumber,
            rowNumber = row.ordinal + 1,
            ppCandidate = null,
            ppUsable = false,
            mlFallbackInvoked = true,
            mlCandidate = mlCandidate,
            finalText = mlCandidate,
            selectedSource = source,
            ppFailureType = ppEvidence.failureType,
            mlFailureType = mlEvidence.failureType,
        )
        return LobbyPlayerDualOcrResult(
            teamSlotNumber = teamSlotNumber,
            row = row,
            rowBounds = rowBounds,
            slotAnchorSource = slotAnchorSource,
            slotAnchorY = slotAnchorY,
            mlEvidence = mlEvidence,
            ppEvidence = ppEvidence,
            resolvedText = mlCandidate,
            consensusStatus = if (mlCandidate == null) {
                LobbyPlayerOcrConsensusStatus.BOTH_EMPTY
            } else {
                LobbyPlayerOcrConsensusStatus.ML_ONLY
            },
            selectedSource = source,
            finalText = mlCandidate,
        )
    }

    private fun logPhase2dDecision(
        teamSlotNumber: Int,
        rowNumber: Int,
        ppCandidate: String?,
        ppUsable: Boolean,
        mlFallbackInvoked: Boolean,
        mlCandidate: String? = null,
        finalText: String?,
        selectedSource: LobbyPlayerNameOcrSource,
        ppFailureType: String? = null,
        mlFailureType: String? = null,
    ) {
        Log.w(
            TEMP_PP_PLAYER_ROW_TAG,
            "teamSlot=$teamSlotNumber row=$rowNumber ppCandidate=${ppCandidate ?: "—"} ppUsable=$ppUsable mlFallbackInvoked=$mlFallbackInvoked mlCandidate=${mlCandidate ?: "—"} finalText=${finalText ?: "—"} selectedSource=$selectedSource ppFailure=${ppFailureType ?: "—"} mlFailure=${mlFailureType ?: "—"}",
        )
    }

    private suspend fun recognizeMl(bitmap: Bitmap): LobbyPlayerOcrEngineEvidence =
        withContext(Dispatchers.Default) {
            val recognizer = recognizerFactory.create()
            try {
                val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitText()
                text.toMlEvidence()
            } finally {
                recognizer.close()
            }
        }

    private suspend fun recognizePp(
        teamSlotNumber: Int,
        row: LobbyPlayerRow,
        bitmap: Bitmap,
    ): LobbyPlayerOcrEngineEvidence {
        val rowNumber = row.ordinal + 1
        Log.w(
            TEMP_PP_PLAYER_ROW_TAG,
            "teamSlot=$teamSlotNumber row=$rowNumber bitmapWidth=${bitmap.width} bitmapHeight=${bitmap.height} ppInvocationEntered",
        )
        val output = ppRuntime.recognize(
            bitmap = bitmap,
            diagnosticsListener = diagnosticsListener(teamSlotNumber, rowNumber),
        )
        val selection = LobbyPpPlayerCandidateSelector.select(output.regions)
        Log.w(
            TEMP_PP_PLAYER_ROW_TAG,
            "teamSlot=$teamSlotNumber row=$rowNumber rawPpText=${output.rawText} candidateText=${selection.candidateText ?: "—"}",
        )
        logSelection(teamSlotNumber, rowNumber, output.regions, selection)
        return LobbyPlayerOcrEngineEvidence(
            engine = LobbyPlayerOcrEngine.PP_OCRV6,
            rawText = output.rawText,
            candidateText = selection.candidateText,
            fragments = output.fragments,
        )
    }

    private fun logSelection(
        teamSlotNumber: Int,
        rowNumber: Int,
        regions: List<LobbyPpPlayerTextRegion>,
        selection: LobbyPpPlayerCandidateSelection,
    ) {
        if (regions.size <= 1) return
        regions.forEach { region ->
            val bounds = region.bounds ?: return@forEach
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=$rowNumber regionIndex=${region.index} regionBounds=${bounds.left},${bounds.top},${bounds.right},${bounds.bottom} regionText=${region.text} regionConfidence=${region.confidence}",
            )
        }
        Log.w(
            TEMP_PP_PLAYER_ROW_TAG,
            "teamSlot=$teamSlotNumber row=$rowNumber regionCount=${regions.size} selectedRegionIndices=${selection.selectedRegionIndices.joinToString(",")} selectionStatus=${selection.status} selectedCandidate=${selection.candidateText ?: "—"}",
        )
    }

    private fun diagnosticsListener(
        teamSlotNumber: Int,
        rowNumber: Int,
    ): PaddleOcrDiagnosticsListener = object : PaddleOcrDiagnosticsListener {
        override fun onInvocationEntered(bitmapWidth: Int, bitmapHeight: Int) = Unit

        override fun onDetectionComplete(
            inputWidth: Int,
            inputHeight: Int,
            boxes: List<PaddleOcrDetectionBox>,
        ) {
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=$rowNumber detInputWidth=$inputWidth detInputHeight=$inputHeight detBoxCount=${boxes.size}",
            )
            boxes.forEach { box ->
                val left = box.points.minOf { it.x }.toInt()
                val top = box.points.minOf { it.y }.toInt()
                val right = box.points.maxOf { it.x }.toInt()
                val bottom = box.points.maxOf { it.y }.toInt()
                val quad = box.points.joinToString(",") { "${it.x.toInt()}:${it.y.toInt()}" }
                Log.w(
                    TEMP_PP_PLAYER_ROW_TAG,
                    "teamSlot=$teamSlotNumber row=$rowNumber boxIndex=${box.index} bounds=$left,$top,$right,$bottom quad=$quad",
                )
            }
        }

        override fun onDetectionCropPrepared(boxIndex: Int, cropWidth: Int, cropHeight: Int) {
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=$rowNumber boxIndex=$boxIndex croppedRegionWidth=$cropWidth croppedRegionHeight=$cropHeight",
            )
        }

        override fun onRecognitionInvocation(
            cropWidths: List<Int>,
            cropHeights: List<Int>,
            inputWidth: Int,
            inputHeight: Int,
        ) {
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=$rowNumber recInvocationEntered cropWidths=${cropWidths.joinToString(",")} cropHeights=${cropHeights.joinToString(",")}",
            )
        }

        override fun onDecodedText(boxIndex: Int, text: String, confidence: Float) {
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=$rowNumber boxIndex=$boxIndex decodedText=$text confidence=$confidence",
            )
        }

        override fun onDirectRecognitionInvocation(bitmapWidth: Int, bitmapHeight: Int) {
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=$rowNumber directRecInvocationEntered bitmapWidth=$bitmapWidth bitmapHeight=$bitmapHeight",
            )
        }

        override fun onDirectRecognitionInput(inputWidth: Int, inputHeight: Int) {
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=$rowNumber directRecInputWidth=$inputWidth directRecInputHeight=$inputHeight",
            )
        }

        override fun onDirectDecodedText(text: String, confidence: Float) {
            Log.w(
                TEMP_PP_PLAYER_ROW_TAG,
                "teamSlot=$teamSlotNumber row=$rowNumber directDecodedText=$text confidence=$confidence",
            )
        }
    }
}

internal object LobbyPlayerOcrCandidateExtractor {
    fun fromMlOutput(output: com.hoggamers.rankforge.domain.ocr.extraction.RawOcrEngineOutput): LobbyPlayerOcrEngineEvidence {
        val blocks = output.blocks
        val lineFragments = blocks.flatMap { block ->
            block.lines.map { line ->
                LobbyPlayerOcrTextFragment(
                    text = line.text,
                    boundingBox = line.geometry?.boundingBox,
                    confidence = (line.confidence as? com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence.Available)?.value,
                )
            }
        }.filter { it.text.isNotBlank() }
        val candidate = fromFragments(lineFragments)
            ?: fromFragments(
                blocks.map { block ->
                    LobbyPlayerOcrTextFragment(block.text, block.geometry?.boundingBox)
                },
            )
        return LobbyPlayerOcrEngineEvidence(
            engine = LobbyPlayerOcrEngine.ML_KIT,
            rawText = output.fullText,
            candidateText = candidate,
            blocks = blocks,
            fragments = lineFragments,
        )
    }

    fun fromFragments(fragments: List<LobbyPlayerOcrTextFragment>): String? = fragments
        .asSequence()
        .filter { it.text.isNotBlank() }
        .sortedWith(
            compareBy<LobbyPlayerOcrTextFragment> { it.boundingBox?.top ?: Int.MAX_VALUE }
                .thenBy { it.boundingBox?.left ?: Int.MAX_VALUE },
        )
        .map { it.text.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
}

private fun Text.toMlEvidence(): LobbyPlayerOcrEngineEvidence =
    LobbyPlayerOcrCandidateExtractor.fromMlOutput(
        com.hoggamers.rankforge.domain.ocr.extraction.RawOcrEngineOutput(
            fullText = text,
            blocks = toRawOcrBlocks(),
        ),
    )

private suspend fun Task<Text>.awaitText(): Text = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
}

object NoOpLobbyPlayerDualOcrRunner : LobbyPlayerDualOcrRunner {
    override suspend fun run(
        teamSlotNumber: Int,
        row: LobbyPlayerRow,
        rowBounds: LobbyPlayerRowCropBounds,
        slotAnchorSource: LobbySlotAnchorSource,
        slotAnchorY: Double,
        bitmap: Bitmap,
    ): LobbyPlayerDualOcrResult = LobbyPlayerDualOcrResult(
        teamSlotNumber = teamSlotNumber,
        row = row,
        rowBounds = rowBounds,
        slotAnchorSource = slotAnchorSource,
        slotAnchorY = slotAnchorY,
        mlEvidence = null,
        ppEvidence = LobbyPlayerOcrEngineEvidence(LobbyPlayerOcrEngine.PP_OCRV6, "", null),
        resolvedText = null,
        consensusStatus = com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrConsensusStatus.BOTH_EMPTY,
        similarityScore = null,
    )
}
