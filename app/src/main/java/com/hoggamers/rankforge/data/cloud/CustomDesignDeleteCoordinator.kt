package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.presentation.screen.LocalImageCleanupResult
import com.hoggamers.rankforge.presentation.screen.LocalImagePreserver
import io.github.jan.supabase.auth.auth
import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class CustomDesignDeleteFailure {
    VALIDATION,
    MISSING_AUTH_SESSION,
    AUTHORIZATION,
    READ_FAILED,
    DATABASE_DELETE,
    STORAGE_DELETE,
    LOCAL_CLEANUP,
}

sealed interface CustomDesignDeleteResult {
    data object Success : CustomDesignDeleteResult
    data class Failed(val failure: CustomDesignDeleteFailure) : CustomDesignDeleteResult
}

fun interface CustomDesignDeleteAction {
    suspend fun delete(customDesignId: String): CustomDesignDeleteResult
}

@Singleton
class CustomDesignDeleteCoordinator internal constructor(
    private val currentUserId: suspend () -> String?,
    private val cloudDataSource: CustomDesignTemplateCloudDataSource,
    private val storageUploader: CustomDesignStorageUploader,
    private val localImagePreserver: LocalImagePreserver,
) : CustomDesignDeleteAction {
    @Inject
    constructor(
        clientProvider: SupabaseClientProvider,
        cloudDataSource: CustomDesignTemplateCloudDataSource,
        storageUploader: CustomDesignStorageUploader,
        localImagePreserver: LocalImagePreserver,
    ) : this(
        currentUserId = {
            clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        },
        cloudDataSource = cloudDataSource,
        storageUploader = storageUploader,
        localImagePreserver = localImagePreserver,
    )

    override suspend fun delete(customDesignId: String): CustomDesignDeleteResult {
        if (!isCanonicalCustomDesignUuid(customDesignId)) {
            return CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.VALIDATION)
        }
        val ownerId = currentUserId()
            ?.takeIf { isCanonicalCustomDesignUuid(it) }
            ?: return CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.MISSING_AUTH_SESSION)

        return when (val read = cloudDataSource.readById(customDesignId, ownerId)) {
            is CustomDesignTemplateCloudReadResult.Success -> deleteVerified(ownerId, customDesignId, read.payload)
            CustomDesignTemplateCloudReadResult.NotFound -> deleteMissing(ownerId, customDesignId)
            is CustomDesignTemplateCloudReadResult.Failed ->
                CustomDesignDeleteResult.Failed(read.failure.toDeleteReadFailure())
        }
    }

    private suspend fun deleteVerified(
        ownerId: String,
        customDesignId: String,
        payload: CustomDesignTemplateCloudPayload,
    ): CustomDesignDeleteResult {
        val verified = CustomDesignTemplateValidator.validate(payload, customDesignId, ownerId)
            ?: return CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.VALIDATION)
        if (currentUserId() != ownerId) {
            return CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.AUTHORIZATION)
        }

        var databaseDeleted = false
        return try {
            when (val deleted = cloudDataSource.deleteById(customDesignId, ownerId)) {
                CustomDesignTemplateCloudDeleteResult.Deleted -> databaseDeleted = true
                is CustomDesignTemplateCloudDeleteResult.Failed ->
                    return CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.DATABASE_DELETE)
            }
            if (currentUserId() != ownerId) {
                return CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.AUTHORIZATION)
            }
            when (val deleted = storageUploader.delete(ownerId, customDesignId, verified.imageExtension)) {
                CustomDesignStorageDeleteResult.Deleted -> Unit
                is CustomDesignStorageDeleteResult.Failed ->
                    return CustomDesignDeleteResult.Failed(deleted.failure.toDeleteFailure())
            }
            if (currentUserId() != ownerId) {
                return CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.AUTHORIZATION)
            }
            when (localImagePreserver.cleanupCustomDesign(ownerId, customDesignId)) {
                LocalImageCleanupResult.Cleaned -> CustomDesignDeleteResult.Success
                LocalImageCleanupResult.Failed ->
                    CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.LOCAL_CLEANUP)
            }
        } catch (cancellation: CancellationException) {
            if (databaseDeleted) {
                withContext(NonCancellable) {
                    bestEffortCleanup(ownerId, customDesignId, verified.imageExtension)
                }
            }
            throw cancellation
        }
    }

    private suspend fun deleteMissing(
        ownerId: String,
        customDesignId: String,
    ): CustomDesignDeleteResult {
        CUSTOM_DESIGN_EXTENSIONS.forEach { extension ->
            when (val deleted = storageUploader.delete(ownerId, customDesignId, extension)) {
                CustomDesignStorageDeleteResult.Deleted -> Unit
                is CustomDesignStorageDeleteResult.Failed ->
                    return CustomDesignDeleteResult.Failed(deleted.failure.toDeleteFailure())
            }
        }
        return when (localImagePreserver.cleanupCustomDesign(ownerId, customDesignId)) {
            LocalImageCleanupResult.Cleaned -> CustomDesignDeleteResult.Success
            LocalImageCleanupResult.Failed -> CustomDesignDeleteResult.Failed(CustomDesignDeleteFailure.LOCAL_CLEANUP)
        }
    }

    private suspend fun bestEffortCleanup(
        ownerId: String,
        customDesignId: String,
        extension: String,
    ) {
        try {
            storageUploader.delete(ownerId, customDesignId, extension)
        } catch (_: Throwable) {
            // Compensation is best effort; the original cancellation remains authoritative.
        }
        try {
            localImagePreserver.cleanupCustomDesign(ownerId, customDesignId)
        } catch (_: Throwable) {
            // Compensation is best effort; the original cancellation remains authoritative.
        }
    }

    private companion object {
        val CUSTOM_DESIGN_EXTENSIONS = listOf("png", "jpg", "webp")
    }
}

private fun CustomDesignTemplateCloudFailure.toDeleteReadFailure() = when (this) {
    CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION -> CustomDesignDeleteFailure.MISSING_AUTH_SESSION
    CustomDesignTemplateCloudFailure.AUTHORIZATION -> CustomDesignDeleteFailure.AUTHORIZATION
    CustomDesignTemplateCloudFailure.VALIDATION -> CustomDesignDeleteFailure.VALIDATION
    CustomDesignTemplateCloudFailure.READ_FAILED,
    CustomDesignTemplateCloudFailure.WRITE_FAILED,
    CustomDesignTemplateCloudFailure.DELETE_FAILED,
    -> CustomDesignDeleteFailure.READ_FAILED
}

private fun CustomDesignStorageFailure.toDeleteFailure() = when (this) {
    CustomDesignStorageFailure.MISSING_AUTH_SESSION -> CustomDesignDeleteFailure.MISSING_AUTH_SESSION
    CustomDesignStorageFailure.AUTHORIZATION -> CustomDesignDeleteFailure.AUTHORIZATION
    else -> CustomDesignDeleteFailure.STORAGE_DELETE
}
