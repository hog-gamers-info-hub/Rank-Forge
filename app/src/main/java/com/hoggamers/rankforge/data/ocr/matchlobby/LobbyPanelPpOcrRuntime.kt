package com.hoggamers.rankforge.data.ocr.matchlobby

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.hoggamers.rankforge.BuildConfig
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.model.OCRRunResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val LOBBY_PP_RAW_LOG_TAG = "LOBBY_PP_RAW"

data class LobbyPanelPpOcrRecognition(
    val fragments: List<LobbyPanelPpFragment>,
)

interface LobbyPanelPpOcrRuntime {
    suspend fun prewarm()

    suspend fun recognize(
        panelBitmap: Bitmap,
        screenshotIndex: Int,
    ): LobbyPanelPpOcrRecognition
}

internal interface LobbyPanelPpEngine<T> {
    suspend fun recognize(input: T): OCRRunResult
}

internal class LobbyPanelPpEngineSession<T>(
    private val createEngine: suspend () -> LobbyPanelPpEngine<T>,
) {
    private val mutex = Mutex()
    private var engine: LobbyPanelPpEngine<T>? = null

    suspend fun prewarm() = mutex.withLock {
        if (engine == null) {
            engine = createEngine()
        }
    }

    suspend fun recognize(input: T): OCRRunResult = mutex.withLock {
        val currentEngine = engine ?: createEngine().also { engine = it }
        currentEngine.recognize(input)
    }
}

private class AndroidLobbyPanelPpEngine(
    private val paddleOcr: PaddleOCR,
) : LobbyPanelPpEngine<Bitmap> {
    override suspend fun recognize(input: Bitmap): OCRRunResult = paddleOcr.recognize(input)
}

@Singleton
class AndroidLobbyPanelPpOcrRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LobbyPanelPpOcrRuntime {
    private val engineSession = LobbyPanelPpEngineSession<Bitmap> {
        AndroidLobbyPanelPpEngine(PaddleOCR.create(context))
    }

    override suspend fun prewarm() {
        try {
            engineSession.prewarm()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Prewarming is an optimization; recognition retains lazy creation.
        }
    }

    override suspend fun recognize(
        panelBitmap: Bitmap,
        screenshotIndex: Int,
    ): LobbyPanelPpOcrRecognition {
        require(!panelBitmap.isRecycled && panelBitmap.width > 0 && panelBitmap.height > 0) {
            "Whole-panel PP-OCR input must be a valid bitmap."
        }
        val result = engineSession.recognize(panelBitmap)
        logRawPpOcrResult(result)
        val recognition = LobbyPanelPpOcrRecognition(
            fragments = result.results.mapIndexedNotNull { index, item ->
                val points = item.box.points
                val left = points.minOf { it.x }.toInt()
                val top = points.minOf { it.y }.toInt()
                val right = points.maxOf { it.x }.toInt()
                val bottom = points.maxOf { it.y }.toInt()
                if (right <= left || bottom <= top) {
                    null
                } else {
                    LobbyPanelPpFragment(
                        text = item.text,
                        confidence = item.confidence,
                        boundingBox = RawOcrBoundingBox(left, top, right, bottom),
                        readingOrderIndex = index,
                    )
                }
            },
        )
        return recognition
    }

    private fun logRawPpOcrResult(result: OCRRunResult) {
        if (!BuildConfig.DEBUG) return
        try {
            result.results.forEach { item ->
                val points = item.box.points
                val left = points.minOfOrNull { it.x }
                val top = points.minOfOrNull { it.y }
                val right = points.maxOfOrNull { it.x }
                val bottom = points.maxOfOrNull { it.y }
                val escapedText = item.text.replace("\r", "\\r").replace("\n", "\\n")
                Log.d(
                    LOBBY_PP_RAW_LOG_TAG,
                    "text=$escapedText left=$left top=$top right=$right bottom=$bottom",
                )
            }
        } catch (_: RuntimeException) {
            // Diagnostics must never affect the OCR result or production control flow.
        }
    }
}

internal object NoOpLobbyPanelPpOcrRuntime : LobbyPanelPpOcrRuntime {
    override suspend fun prewarm() = Unit

    override suspend fun recognize(
        panelBitmap: Bitmap,
        screenshotIndex: Int,
    ): LobbyPanelPpOcrRecognition = LobbyPanelPpOcrRecognition(emptyList())
}
