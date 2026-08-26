package com.hoggamers.rankforge.data.ocr.matchresult

import android.content.Context
import android.graphics.Bitmap
import com.hoggamers.rankforge.data.ocr.PaddleRawOcrGeometryMapper
import com.hoggamers.rankforge.domain.ocr.screenshot.MatchResultScreenshotRole
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.model.OCRRunResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Minimal engine boundary used to keep the PP model reusable and testable. */
interface MatchResultPositionPaddleOcrEngine {
    suspend fun recognize(bitmap: Bitmap): OCRRunResult
}

interface MatchResultPositionPaddleOcrEngineProvider {
    suspend fun getOrCreate(): MatchResultPositionPaddleOcrEngine
}

@Singleton
class AndroidMatchResultPositionPaddleOcrEngineProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MatchResultPositionPaddleOcrEngineProvider {
    private val mutex = Mutex()
    private var engine: MatchResultPositionPaddleOcrEngine? = null

    override suspend fun getOrCreate(): MatchResultPositionPaddleOcrEngine = mutex.withLock {
        engine ?: AndroidMatchResultPositionPaddleOcrEngine(PaddleOCR.create(context)).also {
            engine = it
        }
    }
}

private class AndroidMatchResultPositionPaddleOcrEngine(
    private val paddleOcr: PaddleOCR,
) : MatchResultPositionPaddleOcrEngine {
    private val mutex = Mutex()

    override suspend fun recognize(bitmap: Bitmap): OCRRunResult = mutex.withLock {
        paddleOcr.recognize(bitmap)
    }
}

@Singleton
class AndroidMatchResultPositionPaddleOcrRecognizer @Inject constructor(
    private val engineProvider: MatchResultPositionPaddleOcrEngineProvider,
) {
    suspend fun recognize(
        crop: MatchResultPositionBitmapCrop,
        role: MatchResultScreenshotRole,
    ): MatchResultPositionPaddleOcrResult {
        val dimensions = crop.bitmap.safeDimensions()
            ?: return MatchResultPositionPaddleOcrResult.Failed(
                MatchResultPositionPaddleOcrFailure.INVALID_SOURCE,
            )

        val engine = try {
            engineProvider.getOrCreate()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return MatchResultPositionPaddleOcrResult.Failed(
                MatchResultPositionPaddleOcrFailure.OCR_INITIALIZATION_FAILED,
            )
        }

        val runResult = try {
            engine.recognize(crop.bitmap)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return MatchResultPositionPaddleOcrResult.Failed(
                MatchResultPositionPaddleOcrFailure.OCR_RECOGNITION_FAILED,
            )
        }

        return MatchResultPositionPaddleOcrResult.Success(
            evidence = MatchResultPositionPaddleOcrEvidence(
                role = role,
                position = crop.geometry.position,
                column = crop.geometry.column,
                cropWidth = dimensions.first,
                cropHeight = dimensions.second,
                blocks = PaddleRawOcrGeometryMapper.map(
                    results = runResult.results,
                    cropWidth = dimensions.first,
                    cropHeight = dimensions.second,
                ),
            ),
        )
    }
}

private fun Bitmap.safeDimensions(): Pair<Int, Int>? = try {
    if (isRecycled || width <= 0 || height <= 0) null else width to height
} catch (_: Throwable) {
    null
}
