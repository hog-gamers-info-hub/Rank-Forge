package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.data.auth.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

sealed interface CustomDesignSavedIdDiscoveryResult {
    data object None : CustomDesignSavedIdDiscoveryResult
    data class Found(val customDesignId: String) : CustomDesignSavedIdDiscoveryResult
    data object Ambiguous : CustomDesignSavedIdDiscoveryResult
    data class Failed(val failure: CustomDesignTemplateCloudFailure) : CustomDesignSavedIdDiscoveryResult
}

fun interface CustomDesignSavedIdDiscoveryAction {
    suspend fun find(): CustomDesignSavedIdDiscoveryResult
}

@Singleton
class CustomDesignSavedIdDiscoveryCoordinator internal constructor(
    private val currentUserId: suspend () -> String?,
    private val cloudDataSource: CustomDesignTemplateCloudDataSource,
) : CustomDesignSavedIdDiscoveryAction {
    @Inject
    constructor(
        clientProvider: SupabaseClientProvider,
        cloudDataSource: CustomDesignTemplateCloudDataSource,
    ) : this(
        currentUserId = {
            clientProvider.client.auth.currentSessionOrNull()?.user?.id?.takeIf { it.isNotBlank() }
        },
        cloudDataSource = cloudDataSource,
    )

    override suspend fun find(): CustomDesignSavedIdDiscoveryResult {
        val ownerId = currentUserId()
            ?.takeIf(::isCanonicalCustomDesignUuid)
            ?: return CustomDesignSavedIdDiscoveryResult.Failed(
                CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION,
            )
        return try {
            when (val result = cloudDataSource.findOwnedCustomDesignId(ownerId)) {
                CustomDesignOwnedIdResult.None -> CustomDesignSavedIdDiscoveryResult.None
                CustomDesignOwnedIdResult.Ambiguous -> CustomDesignSavedIdDiscoveryResult.Ambiguous
                is CustomDesignOwnedIdResult.Failed ->
                    CustomDesignSavedIdDiscoveryResult.Failed(result.failure)
                is CustomDesignOwnedIdResult.Found -> {
                    if (currentUserId() != ownerId) {
                        CustomDesignSavedIdDiscoveryResult.Failed(
                            CustomDesignTemplateCloudFailure.AUTHORIZATION,
                        )
                    } else {
                        CustomDesignSavedIdDiscoveryResult.Found(result.customDesignId)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }
}
