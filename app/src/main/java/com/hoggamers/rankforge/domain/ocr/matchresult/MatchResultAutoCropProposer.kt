package com.hoggamers.rankforge.domain.ocr.matchresult

import java.io.File

fun interface MatchResultAutoCropProposer {
    suspend fun propose(localFile: File): MatchResultAutoCropResult
}
