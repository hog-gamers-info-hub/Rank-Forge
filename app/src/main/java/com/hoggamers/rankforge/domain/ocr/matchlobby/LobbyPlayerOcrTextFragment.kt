package com.hoggamers.rankforge.domain.ocr.matchlobby

import com.hoggamers.rankforge.domain.ocr.extraction.RawOcrBoundingBox

data class LobbyPlayerOcrTextFragment(
    val text: String,
    val boundingBox: RawOcrBoundingBox? = null,
    val confidence: Float? = null,
)
