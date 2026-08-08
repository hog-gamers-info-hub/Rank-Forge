package com.hoggamers.rankforge.data.ocr

import android.graphics.Point
import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBlock
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrConfidence
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrElement
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrGeometry
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrLine
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrPoint
import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrSymbol

internal fun Text.toRawOcrBlocks(): List<RawOcrBlock> = textBlocks.map { block ->
    RawOcrBlock(
        text = block.text,
        geometry = geometry(block.boundingBox, block.cornerPoints),
        recognizedLanguage = block.recognizedLanguage,
        confidence = RawOcrConfidence.Unavailable,
        lines = block.lines.map { line ->
            RawOcrLine(
                text = line.text,
                geometry = geometry(line.boundingBox, line.cornerPoints),
                recognizedLanguage = line.recognizedLanguage,
                confidence = RawOcrConfidence.Unavailable,
                elements = line.elements.map { element ->
                    RawOcrElement(
                        text = element.text,
                        geometry = geometry(element.boundingBox, element.cornerPoints),
                        recognizedLanguage = element.recognizedLanguage,
                        confidence = RawOcrConfidence.Unavailable,
                        symbols = element.symbols.map { symbol ->
                            RawOcrSymbol(
                                text = symbol.text,
                                geometry = geometry(symbol.boundingBox, symbol.cornerPoints),
                                recognizedLanguage = symbol.recognizedLanguage,
                                confidence = RawOcrConfidence.Unavailable,
                            )
                        },
                    )
                },
            )
        },
    )
}

private fun geometry(
    boundingBox: Rect?,
    cornerPoints: Array<Point>?,
): RawOcrGeometry? =
    if (boundingBox == null && cornerPoints == null) {
        null
    } else {
        RawOcrGeometry(
            boundingBox = boundingBox?.let { RawOcrBoundingBox(it.left, it.top, it.right, it.bottom) },
            cornerPoints = cornerPoints?.map { RawOcrPoint(it.x, it.y) },
        )
    }
