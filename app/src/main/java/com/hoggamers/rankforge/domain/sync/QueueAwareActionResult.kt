package com.hoggamers.rankforge.domain.sync

data class QueueAwareActionResult<T>(
    val primaryResult: T,
    val queueRecordingResult: QueueRecordingResult,
)
