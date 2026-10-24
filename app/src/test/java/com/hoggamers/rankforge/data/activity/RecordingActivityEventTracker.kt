package com.hoggamers.rankforge.data.activity

class RecordingActivityEventTracker : ActivityEventTracker {
    val events = mutableListOf<ActivityEventType>()

    override suspend fun record(event: ActivityEventType) {
        events += event
    }
}
