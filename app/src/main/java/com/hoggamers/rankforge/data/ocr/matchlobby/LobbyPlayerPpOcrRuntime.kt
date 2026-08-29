package com.hoggamers.rankforge.data.ocr.matchlobby

import android.content.Context
import android.graphics.Bitmap
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPpPlayerTextRegion
import com.hoggamers.rankforge.domain.ocr.matchlobby.LobbyPlayerOcrTextFragment
import com.paddle.ocr.PaddleOCR
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LobbyPlayerPpOcrRecognition(
    val rawText: String,
    val fragments: List<LobbyPlayerOcrTextFragment>,
    val regions: List<LobbyPpPlayerTextRegion> = emptyList(),
)

interface LobbyPlayerPpOcrRuntime {
    suspend fun recognize(bitmap: Bitmap): LobbyPlayerPpOcrRecognition
}

@Singleton
class AndroidLobbyPlayerPpOcrRuntime @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LobbyPlayerPpOcrRuntime {

    private val mutex = Mutex()
    private var paddleOcr: PaddleOCR? = null

    override suspend fun recognize(
        bitmap: Bitmap,
    ): LobbyPlayerPpOcrRecognition = mutex.withLock {
        val engine = paddleOcr ?: PaddleOCR.create(context).also {
            paddleOcr = it
        }

        val result = engine.recognize(bitmap)

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