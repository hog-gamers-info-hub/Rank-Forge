package com.hoggamers.rankforge.data.cloud

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseCustomDesignTemplateCloudDataSourceTest {
    private val ownerId = "a1000000-0000-0000-0000-000000000001"

    @Test
    fun insertUsesOnlyInsertPayloadAndPreservesExactJsonKeys() = runTest {
        var captured: CustomDesignTemplateCloudPayload? = null
        val dataSource = dataSource(insert = { captured = it })
        val payload = payload(ownerId)

        assertEquals(
            CustomDesignTemplateCloudInsertResult.Inserted,
            dataSource.insert(payload, ownerId),
        )
        assertEquals(payload, captured)
        assertEquals(setOf("teamName", "win", "totalKills", "positionPoints", "totalPoints"), captured!!.labelsJson.keys)
        assertEquals(setOf("TEAM_NAME", "WIN", "TOTAL_KILLS", "POSITION_POINTS", "TOTAL_POINTS"), captured!!.columnsJson.keys)
        assertEquals((1..12).map(Int::toString).toSet(), captured!!.rowsJson.keys)
    }

    @Test
    fun insertRejectsMissingSessionAndOwnerMismatchBeforeWrite() = runTest {
        var writes = 0
        val payload = payload(ownerId)
        assertEquals(
            CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION,
            (dataSource(currentUser = null, insert = { writes++ }).insert(payload, ownerId)
                as CustomDesignTemplateCloudInsertResult.Failed).failure,
        )
        assertEquals(
            CustomDesignTemplateCloudFailure.AUTHORIZATION,
            (dataSource(currentUser = "other", insert = { writes++ }).insert(payload, ownerId)
                as CustomDesignTemplateCloudInsertResult.Failed).failure,
        )
        assertEquals(0, writes)
    }

    @Test
    fun readByIdUsesExplicitIdAndRechecksOwner() = runTest {
        var requestedId: String? = null
        val designId = "a2000000-0000-0000-0000-000000000001"
        val dataSource = dataSource(
            read = { id ->
                requestedId = id
                listOf(payload(ownerId).copy(id = id, imagePath = "users/$ownerId/custom-designs/$id/original.png"))
            },
        )

        val result = dataSource.readById(designId, ownerId)

        assertEquals(designId, requestedId)
        assertEquals(designId, (result as CustomDesignTemplateCloudReadResult.Success).payload.id)
    }

    @Test
    fun readByIdTreatsEmptyResultAsNotFoundAndRejectsInvalidOwnerBeforeRead() = runTest {
        var reads = 0
        val dataSource = dataSource(read = { reads += 1; emptyList() })

        assertEquals(
            CustomDesignTemplateCloudReadResult.NotFound,
            dataSource.readById("a2000000-0000-0000-0000-000000000001", ownerId),
        )
        assertEquals(
            CustomDesignTemplateCloudFailure.AUTHORIZATION,
            (dataSource(read = { reads += 1; emptyList() }).readById(
                "a2000000-0000-0000-0000-000000000001",
                "b1000000-0000-0000-0000-000000000001",
            ) as CustomDesignTemplateCloudReadResult.Failed).failure,
        )
        assertEquals(1, reads)
    }

    @Test
    fun readByIdPropagatesCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        try {
            dataSource(read = { throw cancellation }).readById(
                "a2000000-0000-0000-0000-000000000001",
                ownerId,
            )
            throw AssertionError("Expected cancellation")
        } catch (thrown: CancellationException) {
            assertTrue(thrown === cancellation)
        }
    }

    @Test
    fun findOwnedCustomDesignIdReturnsNoneForNoVisibleRows() = runTest {
        assertEquals(
            CustomDesignOwnedIdResult.None,
            dataSource(readOwnedIds = { emptyList() }).findOwnedCustomDesignId(ownerId),
        )
    }

    @Test
    fun findOwnedCustomDesignIdReturnsFoundForOneVisibleRowAndAmbiguousForMany() = runTest {
        val firstId = "a2000000-0000-0000-0000-000000000001"
        val secondId = "a2000000-0000-0000-0000-000000000002"

        assertEquals(
            CustomDesignOwnedIdResult.Found(firstId),
            dataSource(readOwnedIds = { listOf(firstId) }).findOwnedCustomDesignId(ownerId),
        )
        assertEquals(
            CustomDesignOwnedIdResult.Ambiguous,
            dataSource(readOwnedIds = { listOf(secondId, firstId) }).findOwnedCustomDesignId(ownerId),
        )
    }

    @Test
    fun findOwnedCustomDesignIdRejectsMissingAuthAndOwnerMismatch() = runTest {
        val designId = "a2000000-0000-0000-0000-000000000001"
        assertEquals(
            CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION,
            (dataSource(currentUser = null, readOwnedIds = { listOf(designId) })
                .findOwnedCustomDesignId(ownerId) as CustomDesignOwnedIdResult.Failed).failure,
        )
        assertEquals(
            CustomDesignTemplateCloudFailure.AUTHORIZATION,
            (dataSource(currentUser = "b1000000-0000-0000-0000-000000000001", readOwnedIds = { listOf(designId) })
                .findOwnedCustomDesignId(ownerId) as CustomDesignOwnedIdResult.Failed).failure,
        )
    }

    @Test
    fun findOwnedCustomDesignIdPropagatesCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        try {
            dataSource(readOwnedIds = { throw cancellation }).findOwnedCustomDesignId(ownerId)
            throw AssertionError("Expected cancellation")
        } catch (thrown: CancellationException) {
            assertTrue(thrown === cancellation)
        }
    }

    @Test
    fun deleteByIdUsesOnlyExplicitIdAfterOwnerValidation() = runTest {
        var deletedId: String? = null
        val dataSource = dataSource(delete = { deletedId = it })

        assertEquals(
            CustomDesignTemplateCloudDeleteResult.Deleted,
            dataSource.deleteById(
                "a2000000-0000-0000-0000-000000000001",
                ownerId,
            ),
        )
        assertEquals("a2000000-0000-0000-0000-000000000001", deletedId)
    }

    @Test
    fun deleteByIdRejectsMissingSessionInvalidIdsAndOwnerMismatchBeforeDelete() = runTest {
        var deletes = 0
        val designId = "a2000000-0000-0000-0000-000000000001"
        assertEquals(
            CustomDesignTemplateCloudFailure.MISSING_AUTH_SESSION,
            (dataSource(currentUser = null, delete = { deletes++ }).deleteById(designId, ownerId)
                as CustomDesignTemplateCloudDeleteResult.Failed).failure,
        )
        assertEquals(
            CustomDesignTemplateCloudFailure.VALIDATION,
            (dataSource(delete = { deletes++ }).deleteById("invalid", ownerId)
                as CustomDesignTemplateCloudDeleteResult.Failed).failure,
        )
        assertEquals(
            CustomDesignTemplateCloudFailure.AUTHORIZATION,
            (dataSource(currentUser = "b1000000-0000-0000-0000-000000000001", delete = { deletes++ })
                .deleteById(designId, ownerId) as CustomDesignTemplateCloudDeleteResult.Failed).failure,
        )
        assertEquals(0, deletes)
    }

    @Test
    fun deleteByIdPropagatesCancellationAndMapsBackendFailure() = runTest {
        val cancellation = CancellationException("cancelled")
        try {
            dataSource(delete = { throw cancellation }).deleteById(
                "a2000000-0000-0000-0000-000000000001",
                ownerId,
            )
            throw AssertionError("Expected cancellation")
        } catch (thrown: CancellationException) {
            assertTrue(thrown === cancellation)
        }
        assertEquals(
            CustomDesignTemplateCloudFailure.DELETE_FAILED,
            (dataSource(delete = { error("backend failure") }).deleteById(
                "a2000000-0000-0000-0000-000000000001",
                ownerId,
            ) as CustomDesignTemplateCloudDeleteResult.Failed).failure,
        )
    }

    private fun dataSource(
        currentUser: String? = ownerId,
        read: suspend (String) -> List<CustomDesignTemplateCloudPayload> = { emptyList() },
        readOwnedIds: suspend () -> List<String> = { emptyList() },
        insert: suspend (CustomDesignTemplateCloudPayload) -> Unit = {},
        delete: suspend (String) -> Unit = {},
    ) = SupabaseCustomDesignTemplateCloudDataSource(
        isConfigured = { true },
        currentUserId = { currentUser },
        insertPayload = insert,
        readPayload = read,
        readOwnedIds = readOwnedIds,
        deletePayload = delete,
    )

    private fun payload(userId: String) = CustomDesignTemplateCloudPayload(
        id = "a2000000-0000-0000-0000-000000000001",
        userId = userId,
        imagePath = "users/$userId/custom-designs/a2000000-0000-0000-0000-000000000001/original.png",
        imageSha256 = "a".repeat(64),
        imageByteSize = 12,
        imageExtension = "png",
        imageMimeType = "image/png",
        sourceWidth = 1080,
        sourceHeight = 1350,
        labelsJson = buildJsonObject {
            put("teamName", "TEAM NAME")
            put("win", "WIN")
            put("totalKills", "ELIM.")
            put("positionPoints", "POS.")
            put("totalPoints", "TOTAL")
        },
        columnsJson = buildJsonObject {
            put("TEAM_NAME", 700)
            put("WIN", 200)
            put("TOTAL_KILLS", 600)
            put("POSITION_POINTS", 400)
            put("TOTAL_POINTS", 900)
        },
        rowsJson = buildJsonObject {
            (1..12).forEach { put(it.toString(), it * 100) }
        },
    )
}
