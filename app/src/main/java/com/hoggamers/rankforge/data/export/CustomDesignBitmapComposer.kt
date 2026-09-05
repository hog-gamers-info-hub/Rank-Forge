package com.hoggamers.rankforge.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import com.hoggamers.rankforge.domain.export.ResultExportRow
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignColumnTextColors
import com.hoggamers.rankforge.domain.ocr.customdesign.CustomDesignEffectiveGridGeometry
import java.io.File
import java.io.FileInputStream

enum class CustomDesignBitmapComposeFailure {
    INVALID_IMAGE_REFERENCE,
    IMAGE_NOT_FOUND,
    IMAGE_DECODE_FAILED,
    DIMENSION_MISMATCH,
    BITMAP_CREATION_FAILED,
    RENDER_FAILED,
}

sealed interface CustomDesignBitmapComposeResult {
    data class Success(val bitmap: Bitmap) : CustomDesignBitmapComposeResult

    data class Failure(
        val reason: CustomDesignBitmapComposeFailure,
    ) : CustomDesignBitmapComposeResult
}

class CustomDesignBitmapComposer(
    private val renderer: CustomDesignCanvasRenderer = CustomDesignCanvasRenderer(),
) {
    fun compose(
        imageReference: String,
        rows: List<ResultExportRow>,
        geometry: CustomDesignEffectiveGridGeometry,
        textColors: CustomDesignColumnTextColors = CustomDesignColumnTextColors.allBlack(),
    ): CustomDesignBitmapComposeResult {
        val sourceFile = validatedSourceFile(imageReference)
            ?: return CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.INVALID_IMAGE_REFERENCE,
            )
        if (!sourceFile.exists() || !sourceFile.isFile || !sourceFile.canRead() || sourceFile.length() <= 0L) {
            return CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.IMAGE_NOT_FOUND,
            )
        }

        val decodedSource = try {
            FileInputStream(sourceFile).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null
        } ?: return CustomDesignBitmapComposeResult.Failure(
            CustomDesignBitmapComposeFailure.IMAGE_DECODE_FAILED,
        )

        if (decodedSource.width != geometry.sourceWidth ||
            decodedSource.height != geometry.sourceHeight
        ) {
            decodedSource.recycle()
            return CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.DIMENSION_MISMATCH,
            )
        }

        val composedBitmap = try {
            Bitmap.createBitmap(
                decodedSource.width,
                decodedSource.height,
                Bitmap.Config.ARGB_8888,
            )
        } catch (_: RuntimeException) {
            decodedSource.recycle()
            return CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.BITMAP_CREATION_FAILED,
            )
        } catch (_: OutOfMemoryError) {
            decodedSource.recycle()
            return CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.BITMAP_CREATION_FAILED,
            )
        }

        return try {
            val canvas = Canvas(composedBitmap)
            canvas.drawBitmap(decodedSource, 0f, 0f, null)
            when (renderer.render(canvas, rows, geometry, textColors)) {
                CustomDesignCanvasRenderResult.Success ->
                    CustomDesignBitmapComposeResult.Success(composedBitmap)
                is CustomDesignCanvasRenderResult.Failure -> {
                    composedBitmap.recycle()
                    CustomDesignBitmapComposeResult.Failure(
                        CustomDesignBitmapComposeFailure.RENDER_FAILED,
                    )
                }
            }
        } catch (_: RuntimeException) {
            composedBitmap.recycle()
            CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.RENDER_FAILED,
            )
        } catch (_: OutOfMemoryError) {
            composedBitmap.recycle()
            CustomDesignBitmapComposeResult.Failure(
                CustomDesignBitmapComposeFailure.RENDER_FAILED,
            )
        } finally {
            decodedSource.recycle()
        }
    }

    private fun validatedSourceFile(imageReference: String): File? {
        if (imageReference.isBlank()) return null
        val uri = try {
            Uri.parse(imageReference)
        } catch (_: RuntimeException) {
            return null
        }
        if (uri.scheme != "file") return null
        val path = uri.path?.takeIf { it.isNotBlank() } ?: return null
        return File(path)
    }
}
