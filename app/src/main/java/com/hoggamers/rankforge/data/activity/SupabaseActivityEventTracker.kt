package com.hoggamers.rankforge.data.activity

import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ActivityEventInsert(
    @SerialName("event_type") val eventType: String,
)

@Singleton
class SupabaseActivityEventTracker @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : ActivityEventTracker {
    override suspend fun record(event: ActivityEventType) {
        clientProvider.client
            .from(TABLE_NAME)
            .insert(ActivityEventInsert(eventType = event.wireName))
    }

    private companion object {
        const val TABLE_NAME = "activity_events"
    }
}
