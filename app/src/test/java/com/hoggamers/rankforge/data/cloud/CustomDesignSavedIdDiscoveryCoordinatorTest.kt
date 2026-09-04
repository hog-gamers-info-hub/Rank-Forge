package com.hoggamers.rankforge.data.cloud

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomDesignSavedIdDiscoveryCoordinatorTest {
    private val ownerId = "a1000000-0000-0000-0000-000000000001"
    private val otherOwnerId = "b1000000-0000-0000-0000-000000000001"
    private val designId = "a2000000-0000-0000-0000-000000000001"

    @Test
    fun foundIdIsReturnedWhenOwnerDoesNotChange() = runTest {
        val result = coordinator(
            currentUser = { ownerId },
            discovery = { CustomDesignOwnedIdResult.Found(designId) },
        ).find()

        assertEquals(
            CustomDesignSavedIdDiscoveryResult.Found(designId),
            result,
        )
    }

    @Test
    fun ownerSwitchBeforeFoundResultReturnsAuthorizationFailure() = runTest {
        var currentUser = ownerId
        val result = coordinator(
            currentUser = { currentUser },
            discovery = {
                currentUser = otherOwnerId
                CustomDesignOwnedIdResult.Found(designId)
            },
        ).find()

        assertEquals(
            CustomDesignSavedIdDiscoveryResult.Failed(CustomDesignTemplateCloudFailure.AUTHORIZATION),
            result,
        )
    }

    private fun coordinator(
        currentUser: suspend () -> String?,
        discovery: suspend () -> CustomDesignOwnedIdResult,
    ) = CustomDesignSavedIdDiscoveryCoordinator(
        currentUserId = currentUser,
        cloudDataSource = object : CustomDesignTemplateCloudDataSource {
            override suspend fun insert(
                payload: CustomDesignTemplateCloudPayload,
                expectedOwnerUserId: String,
            ) = CustomDesignTemplateCloudInsertResult.Inserted

            override suspend fun readById(
                customDesignId: String,
                expectedOwnerUserId: String,
            ) = CustomDesignTemplateCloudReadResult.NotFound

            override suspend fun findOwnedCustomDesignId(
                expectedOwnerUserId: String,
            ) = discovery()

            override suspend fun deleteById(
                customDesignId: String,
                expectedOwnerUserId: String,
            ) = CustomDesignTemplateCloudDeleteResult.Deleted
        },
    )
}
