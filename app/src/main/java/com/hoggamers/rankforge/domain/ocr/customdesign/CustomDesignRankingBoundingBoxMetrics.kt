package com.hoggamers.rankforge.domain.ocr.customdesign

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox

fun averageRankingBoundingBoxHeightPx(
    acceptedRankingBoundingBoxes: List<RawOcrBoundingBox>,
): Float? = acceptedRankingBoundingBoxes
    .map { (it.bottom - it.top).toFloat() }
    .filter { it > 0f && it.isFinite() }
    .takeIf { it.isNotEmpty() }
    ?.average()
    ?.toFloat()
