package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseAuthConfig
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

@Serializable
data class CustomDesignTemplateCloudPayload(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("image_path") val imagePath: String,
    @SerialName("image_sha256") val imageSha256: String,
    @SerialName("image_byte_size") val imageByteSize: Long,
    @SerialName("image_extension") val imageExtension: String,
    @SerialName("image_mime_type") val imageMimeType: String,
    @SerialName("source_width") val sourceWidth: Int,
    @SerialName("source_height") val sourceHeight: Int,
    @SerialName("labels_json") val labelsJson: JsonObject,
    @SerialName("columns_json") val columnsJson: JsonObject,
    @SerialName("rows_json") val rowsJson: JsonObject,
    @SerialName("text_colors_json") val textColorsJson: JsonObject? = null,
)

enum class CustomDesignTemplateCloudFailure {
    MISSING_AUTH_SESSION,
    AUTHORIZATION,
    VALIDATION,
    READ_FAILED,
    WRITE_FAILED,
    DELETE_FAILED,
}

sealed interface CustomDesignTemplateCloudInsertResult {
    data object Inserted : CustomDesignTemplateCloudInsertResult
    data class Failed(val failure: CustomDesignTemplateCloudFailure) : CustomDesignTemplateCloudInsertResult
}

sealed interface CustomDesignTemplateCloudReadResult {
    data class Success(val payload: CustomDesignTemplateCloudPayload) : CustomDesignTemplateCloudReadResult
    data object NotFound : CustomDesignTemplateCloudReadResult
    data class Failed(val failure: CustomDesignTemplateCloudFailure) : CustomDesignTemplateCloudReadResult
}

sealed interface CustomDesignOwnedIdResult {
    data object None : CustomDesignOwnedIdResult
    data class Found(val customDesignId: String) : CustomDesignOwnedIdResult
    data object Ambiguous : CustomDesignOwnedIdResult
    data class Failed(val failure: CustomDesignTemplateCloudFailure) : CustomDesignOwnedIdResult
}

sealed interface CustomDesignTemplateCloudDeleteResult {
    data object Deleted : CustomDesignTemplateCloudDeleteResult
    data class Failed(val failure: CustomDesignTemplateCloudFailure) : CustomDesignTemplateCloudDeleteResult
}

interface CustomDesignTemplateCloudDataSource {
    suspend fun insert(
        payload: CustomDesignTemplateCloudPayload,
        expectedOwnerUserId: String,
    ): CustomDesignTemplateCloudInsertResult

    suspend fun readById(
        customDesignId: String,
        expectedOwnerUserId: String,
    ): CustomDesignTemplateCloudReadResult

    suspend fun findOwnedCustomDesignId(
        expectedOwnerUserId: String,
    ): CustomDesignOwnedIdResult = CustomDesignOwnedIdResult.Failed(
        CustomDesignTemplateCloudFailure.READ_FAILED,
    )

    suspend fun deleteById(
        customDesignId: String,
        expectedOwnerUserId: String,
    ): CustomDesignTemplateCloudDeleteResult
}

@Singleton
class SupabaseCustomDesignTemplateCloudDataSource internal constructor(
    private val isConfigured: () -> Boolean,
    private val currentUserId: suspend () -> String?,
    private val insertPayload: suspend (CustomDesignTemplateCloudPayload) -> Unit,
    private val readPayload: suspend (String) -> List<CustomDesignTemplateCloudPayload> = { emptyList() },
    private val readOwnedIds: suspend () -> List<String> = { emptyList() },
    private val deletePayload: suspend (String) -> Unit = {},
) : CustomDesignTemplateCloudDataSource {
    @Inject
    constructor(
        config: SupabaseAuthConfig,
        clientProvider: SupabaseClientProvider,
    ) : this(
        isConfigured = { config.isConfigured },
        currentUserId = {
            clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        },
        insertPayload = { payload ->
            clientProvider.client.from(TABLE_NAME).insert(payload)
        },
        readPayload = { customDesignId ->
            clientProvider.client.from(TABLE_NAME).select {
                filter { eq("id", customDesignId) }
            }.decodeList<CustomDesignTemplateCloudPayload>()
        },
        readOwnedIds = {
            clientProvider.client
                .from(TABLE_NAME)
                .select(customDesignIdColumns())
                .decodeList<CustomDesignTemplateCloudPayloadId>()
                .map { it.id }
        },
        deletePayload = { customDesignId ->
            clientProvider.client
                .from(TABLE_NAME)
                .delete {
                    filter {
                        eq("id", customDesignId)
                    }
                }
        },
    )

    override suspend fun insert(
        payload: CustomDesignTemplateCloudPayload,
        expectedOwnerUserId: String,
    ): CustomDesignTemplateCloudInsertResult {
        if (!isConfigured()) return CustomDesignTemplateCloudInsertResult.Failed(
            CustomDesignTemplateCloudFailure.WRITE_FAILED,
        )
        val ownerId = currentUserId()
            ?: return CustomDesignTemplateCloudInsertResult.Failed(
                CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION,
            )
        if (expectedOwnerUserId.isBlank() || ownerId != expectedOwnerUserId || payload.userId != ownerId) {
            return CustomDesignTemplateCloudInsertResult.Failed(
                CustomDesignTemplateCloudFailure.AUTHORIZATION,
            )
        }
        return try {
            insertPayload(payload)
            CustomDesignTemplateCloudInsertResult.Inserted
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            CustomDesignTemplateCloudInsertResult.Failed(CustomDesignTemplateCloudFailure.WRITE_FAILED)
        }
    }

    override suspend fun readById(
        customDesignId: String,
        expectedOwnerUserId: String,
    ): CustomDesignTemplateCloudReadResult {
        if (!isConfigured()) return CustomDesignTemplateCloudReadResult.Failed(
            CustomDesignTemplateCloudFailure.READ_FAILED,
        )
        val ownerId = currentUserId()
            ?: return CustomDesignTemplateCloudReadResult.Failed(
                CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION,
            )
        if (!isCanonicalCustomDesignUuid(customDesignId) || !isCanonicalCustomDesignUuid(expectedOwnerUserId)) {
            return CustomDesignTemplateCloudReadResult.Failed(
                CustomDesignTemplateCloudFailure.VALIDATION,
            )
        }
        if (ownerId != expectedOwnerUserId) {
            return CustomDesignTemplateCloudReadResult.Failed(
                CustomDesignTemplateCloudFailure.AUTHORIZATION,
            )
        }
        return try {
            val payloads = readPayload(customDesignId)
            val payload = when (payloads.size) {
                0 -> return CustomDesignTemplateCloudReadResult.NotFound
                1 -> payloads.single()
                else -> return CustomDesignTemplateCloudReadResult.Failed(
                    CustomDesignTemplateCloudFailure.READ_FAILED,
                )
            }
            if (payload.userId != expectedOwnerUserId || currentUserId() != expectedOwnerUserId) {
                CustomDesignTemplateCloudReadResult.Failed(CustomDesignTemplateCloudFailure.AUTHORIZATION)
            } else {
                CustomDesignTemplateCloudReadResult.Success(payload)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            CustomDesignTemplateCloudReadResult.Failed(CustomDesignTemplateCloudFailure.READ_FAILED)
        }
    }

    override suspend fun findOwnedCustomDesignId(
        expectedOwnerUserId: String,
    ): CustomDesignOwnedIdResult {
        if (!isConfigured()) return CustomDesignOwnedIdResult.Failed(
            CustomDesignTemplateCloudFailure.READ_FAILED,
        )
        val ownerId = currentUserId()
            ?: return CustomDesignOwnedIdResult.Failed(
                CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION,
            )
        if (!isCanonicalCustomDesignUuid(expectedOwnerUserId)) {
            return CustomDesignOwnedIdResult.Failed(
                CustomDesignTemplateCloudFailure.VALIDATION,
            )
        }
        if (ownerId != expectedOwnerUserId) {
            return CustomDesignOwnedIdResult.Failed(
                CustomDesignTemplateCloudFailure.AUTHORIZATION,
            )
        }
        return try {
            val ids = readOwnedIds()
            if (currentUserId() != expectedOwnerUserId) {
                CustomDesignOwnedIdResult.Failed(CustomDesignTemplateCloudFailure.AUTHORIZATION)
            } else {
                when (ids.size) {
                    0 -> CustomDesignOwnedIdResult.None
                    1 -> ids.single()
                        .takeIf(::isCanonicalCustomDesignUuid)
                        ?.let(CustomDesignOwnedIdResult::Found)
                        ?: CustomDesignOwnedIdResult.Failed(CustomDesignTemplateCloudFailure.READ_FAILED)
                    else -> CustomDesignOwnedIdResult.Ambiguous
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            CustomDesignOwnedIdResult.Failed(CustomDesignTemplateCloudFailure.READ_FAILED)
        }
    }

    override suspend fun deleteById(
        customDesignId: String,
        expectedOwnerUserId: String,
    ): CustomDesignTemplateCloudDeleteResult {
        if (!isConfigured()) return CustomDesignTemplateCloudDeleteResult.Failed(
            CustomDesignTemplateCloudFailure.DELETE_FAILED,
        )
        val ownerId = currentUserId()
            ?: return CustomDesignTemplateCloudDeleteResult.Failed(
                CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION,
            )
        if (!isCanonicalCustomDesignUuid(customDesignId) || !isCanonicalCustomDesignUuid(expectedOwnerUserId)) {
            return CustomDesignTemplateCloudDeleteResult.Failed(
                CustomDesignTemplateCloudFailure.VALIDATION,
            )
        }
        if (ownerId != expectedOwnerUserId) {
            return CustomDesignTemplateCloudDeleteResult.Failed(
                CustomDesignTemplateCloudFailure.AUTHORIZATION,
            )
        }
        return try {
            deletePayload(customDesignId)
            CustomDesignTemplateCloudDeleteResult.Deleted
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            CustomDesignTemplateCloudDeleteResult.Failed(CustomDesignTemplateCloudFailure.DELETE_FAILED)
        }
    }

    companion object {
        const val TABLE_NAME = "custom_design_templates"
    }
}

@Serializable
private data class CustomDesignTemplateCloudPayloadId(
    val id: String,
)

private fun customDesignIdColumns() =
    io.github.jan.supabase.postgrest.query.Columns.list("id")

internal fun isCanonicalCustomDesignUuid(value: String): Boolean =
    runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
