package com.hoggamers.rankforge.data.ocr.matchlobby

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.data.ocr.MlKitTextRecognizerFactory
import com.hoggamers.rankforge.data.ocr.toRawOcrBlocks
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrEngineOutput
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrFragment
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRow
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropBounds
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowCropGeometryCalculator
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerRowMapper
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorEvidence
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorResolver
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbySlotAnchorSource
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class LobbyPlayerRowCropPreview(
    val row: LobbyPlayerRow,
    val boundsInTeamCrop: LobbyPlayerRowCropBounds,
    val slotAnchorSource: LobbySlotAnchorSource,
    val slotAnchorY: Double,
    val structuralEvidence: String?,
)

sealed interface LobbyPlayerRowCropGenerationResult {
    data class Generated(
        val rows: List<LobbyPlayerRowCropPreview>,
    ) : LobbyPlayerRowCropGenerationResult {
        init {
            require(rows.map { it.row } == LobbyPlayerRow.entries.toList())
        }
    }

    data object NotAvailable : LobbyPlayerRowCropGenerationResult
}

interface LobbyPlayerRowCropPipeline {
    suspend fun generate(
        authoritativeTeamSlotNumber: Int,
        teamCropImage: MatchLobbyTeamCropPreviewImage,
    ): LobbyPlayerRowCropGenerationResult
}

@Singleton
class AndroidLobbyPlayerRowCropPipeline @Inject constructor(
    private val recognizerFactory: MlKitTextRecognizerFactory,
    private val ppRuntime: LobbyPlayerPpOcrRuntime,
) : LobbyPlayerRowCropPipeline {
    override suspend fun generate(
        authoritativeTeamSlotNumber: Int,
        teamCropImage: MatchLobbyTeamCropPreviewImage,
    ): LobbyPlayerRowCropGenerationResult {
        val source = (teamCropImage as? AndroidMatchLobbyTeamCropPreviewImage)?.bitmap
            ?.takeIf { !it.isRecycled && it.width > 0 && it.height > 0 }
            ?: return LobbyPlayerRowCropGenerationResult.NotAvailable

        val structure = try {
            recognizeStructure(source)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            RawOcrEngineOutput(fullText = "", blocks = emptyList())
        }
        val playerOcr = try {
            ppRuntime.recognize(source)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            LobbyPlayerPpOcrRecognition(fragments = emptyList())
        }
        val mlEvidence = structure.findSlotEvidence(source.width, source.height)
        val ppEvidence = if (mlEvidence == null) {
            LobbyPlayerPpEvidenceMapper.findSlotEvidence(playerOcr, source.width, source.height)
        } else {
            null
        }
        val anchor = LobbySlotAnchorResolver().resolve(
            authoritativeTeamSlotNumber = authoritativeTeamSlotNumber,
            teamCropWidth = source.width,
            teamCropHeight = source.height,
            mlKitEvidence = mlEvidence,
            ppOcrEvidence = ppEvidence,
        ) ?: return LobbyPlayerRowCropGenerationResult.NotAvailable
        val geometry = LobbyPlayerRowCropGeometryCalculator.calculate(
            teamCropWidth = source.width,
            teamCropHeight = source.height,
            slotAnchorY = anchor.anchorY,
        ) ?: return LobbyPlayerRowCropGenerationResult.NotAvailable
        val selectedSlotBox = anchor.selectedEvidence?.boundingBox
        val mapping = LobbyPlayerRowMapper.map(
            rowBands = geometry.bands,
            fragments = LobbyPlayerPpEvidenceMapper.playerFragments(playerOcr),
            selectedSlotBoundingBox = selectedSlotBox,
            slotGutterRight = geometry.playerAreaLeft,
        )

        val rows = LobbyPlayerRow.entries.map { row ->
            val bounds = geometry.boundsFor(row)
            LobbyPlayerRowCropPreview(
                row = row,
                boundsInTeamCrop = bounds,
                slotAnchorSource = anchor.source,
                slotAnchorY = anchor.anchorY,
                structuralEvidence = mapping.row(row).structuralText,
            )
        }
        return LobbyPlayerRowCropGenerationResult.Generated(rows)
    }

    private suspend fun recognizeStructure(bitmap: Bitmap): RawOcrEngineOutput = withContext(Dispatchers.Default) {
        val recognizer = recognizerFactory.create()
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitText()
            RawOcrEngineOutput(fullText = text.text, blocks = text.toRawOcrBlocks())
        } finally {
            recognizer.close()
        }
    }

    private fun RawOcrEngineOutput.findSlotEvidence(width: Int, height: Int): LobbySlotAnchorEvidence? =
        blocks.asSequence()
            .flatMap { block ->
                sequenceOf(block.text to block.geometry?.boundingBox) + block.lines.asSequence().flatMap { line ->
                    sequenceOf(line.text to line.geometry?.boundingBox) + line.elements.asSequence().map {
                        it.text to it.geometry?.boundingBox
                    }
                }
            }
            .mapNotNull { (text, box) ->
                val number = text.trim().toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
                if (box == null || box.left < 0 || box.top < 0 || box.right > width || box.bottom > height ||
                    box.right <= box.left || box.bottom <= box.top ||
                    (box.left + box.right) / 2.0 > width * SLOT_GUTTER_FRACTION
                ) return@mapNotNull null
                LobbySlotAnchorEvidence(
                    rawText = text,
                    detectedSlotNumber = number,
                    boundingBox = box,
                )
            }
            .firstOrNull()

    private companion object {
        const val SLOT_GUTTER_FRACTION = 0.15
    }
}

internal object LobbyPlayerPpEvidenceMapper {
    fun playerFragments(recognition: LobbyPlayerPpOcrRecognition): List<LobbyPlayerOcrFragment> =
        recognition.fragments.map { fragment ->
            LobbyPlayerOcrFragment(
                rawText = fragment.text,
                boundingBox = fragment.boundingBox,
                isSlotNumberEvidence = fragment.text.trim().toIntOrNull()?.let { it in 1..12 } == true,
            )
        }

    fun findSlotEvidence(
        recognition: LobbyPlayerPpOcrRecognition,
        width: Int,
        height: Int,
    ): LobbySlotAnchorEvidence? = recognition.fragments.asSequence()
        .mapNotNull { result ->
            val number = result.text.trim().toIntOrNull()?.takeIf { it in 1..12 } ?: return@mapNotNull null
            val box = result.boundingBox ?: return@mapNotNull null
            if (box.left < 0 || box.top < 0 || box.right > width || box.bottom > height ||
                box.right <= box.left || box.bottom <= box.top ||
                (box.left + box.right) / 2.0 > width * 0.15
            ) return@mapNotNull null
            LobbySlotAnchorEvidence(rawText = result.text, detectedSlotNumber = number, boundingBox = box)
        }
        .firstOrNull()
}

object NoOpLobbyPlayerRowCropPipeline : LobbyPlayerRowCropPipeline {
    override suspend fun generate(
        authoritativeTeamSlotNumber: Int,
        teamCropImage: MatchLobbyTeamCropPreviewImage,
    ): LobbyPlayerRowCropGenerationResult = LobbyPlayerRowCropGenerationResult.NotAvailable
}

private suspend fun Task<Text>.awaitText(): Text = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
