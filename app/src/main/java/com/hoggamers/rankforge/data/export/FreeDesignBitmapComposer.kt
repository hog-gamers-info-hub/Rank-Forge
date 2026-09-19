package com.hoggamers.rankforge.data.export

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.hoggamers.rankforge.domain.export.MatchResultExportModel
import com.hoggamers.rankforge.domain.export.TournamentResultExportModel
import java.io.IOException

enum class FreeDesignBitmapComposeFailure {
    ASSET_NOT_FOUND,
    IMAGE_DECODE_FAILED,
    DIMENSION_MISMATCH,
    BITMAP_CREATION_FAILED,
    RENDER_FAILED,
}

sealed interface FreeDesignBitmapComposeResult {
    data class Success(val bitmap: Bitmap) : FreeDesignBitmapComposeResult

    data class Failure(
        val reason: FreeDesignBitmapComposeFailure,
    ) : FreeDesignBitmapComposeResult
}

class FreeDesignBitmapComposer(
    private val assetManager: AssetManager,
    private val renderer: FreeDesignCanvasRenderer = FreeDesignCanvasRenderer(),
) {
    fun compose(
        model: MatchResultExportModel,
        template: FreeDesignTemplate,
    ): FreeDesignBitmapComposeResult = compose(template) { canvas ->
        renderer.render(canvas, model, template)
    }

    fun compose(
        model: TournamentResultExportModel,
        template: FreeDesignTemplate,
    ): FreeDesignBitmapComposeResult = compose(template) { canvas ->
        renderer.render(canvas, model, template)
    }

    private fun compose(
        template: FreeDesignTemplate,
        render: (Canvas) -> FreeDesignCanvasRenderResult,
    ): FreeDesignBitmapComposeResult {
        val decodedSource = when (val decoded = decodeAsset(template.assetPath)) {
            AssetDecodeResult.NotFound -> return FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.ASSET_NOT_FOUND,
            )
            AssetDecodeResult.DecodeFailed -> return FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.IMAGE_DECODE_FAILED,
            )
            is AssetDecodeResult.Success -> decoded.bitmap
        }

        if (
            decodedSource.width != template.sourceWidth ||
            decodedSource.height != template.sourceHeight
        ) {
            decodedSource.recycle()
            return FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.DIMENSION_MISMATCH,
            )
        }

        val composedBitmap = try {
            Bitmap.createBitmap(
                template.sourceWidth,
                template.sourceHeight,
                Bitmap.Config.ARGB_8888,
            )
        } catch (_: RuntimeException) {
            decodedSource.recycle()
            return FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.BITMAP_CREATION_FAILED,
            )
        } catch (_: OutOfMemoryError) {
            decodedSource.recycle()
            return FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.BITMAP_CREATION_FAILED,
            )
        }

        return try {
            val canvas = Canvas(composedBitmap)
            canvas.drawBitmap(decodedSource, 0f, 0f, null)
            when (render(canvas)) {
                FreeDesignCanvasRenderResult.Success ->
                    FreeDesignBitmapComposeResult.Success(composedBitmap)
                is FreeDesignCanvasRenderResult.Failure -> {
                    composedBitmap.recycle()
                    FreeDesignBitmapComposeResult.Failure(
                        FreeDesignBitmapComposeFailure.RENDER_FAILED,
                    )
                }
            }
        } catch (_: RuntimeException) {
            composedBitmap.recycle()
            FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.RENDER_FAILED,
            )
        } catch (_: OutOfMemoryError) {
            composedBitmap.recycle()
            FreeDesignBitmapComposeResult.Failure(
                FreeDesignBitmapComposeFailure.RENDER_FAILED,
            )
        } finally {
            decodedSource.recycle()
        }
    }

    private fun decodeAsset(assetPath: String): AssetDecodeResult {
        val options = BitmapFactory.Options().apply {
            inScaled = false
        }
        val input = try {
            assetManager.open(assetPath)
        } catch (_: IOException) {
            return AssetDecodeResult.NotFound
        } catch (_: RuntimeException) {
            return AssetDecodeResult.NotFound
        }
        return try {
            val bitmap = input.use { BitmapFactory.decodeStream(it, null, options) }
            if (bitmap == null) AssetDecodeResult.DecodeFailed else AssetDecodeResult.Success(bitmap)
        } catch (_: RuntimeException) {
            AssetDecodeResult.DecodeFailed
        } catch (_: OutOfMemoryError) {
            AssetDecodeResult.DecodeFailed
        }
    }

    private sealed interface AssetDecodeResult {
        data class Success(val bitmap: Bitmap) : AssetDecodeResult

        data object NotFound : AssetDecodeResult

        data object DecodeFailed : AssetDecodeResult
    }
}
