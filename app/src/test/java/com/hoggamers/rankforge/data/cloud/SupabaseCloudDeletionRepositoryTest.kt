package com.hoggamers.rankforge.data.cloud

import com.hoggamers.rankforge.domain.tournament.CloudDeletionFailureCategory
import com.hoggamers.rankforge.domain.tournament.CloudDeletionStageResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseCloudDeletionRepositoryTest {
    @Test
    fun committedAndReceiptBackedRemoteOutcomesPermitLocalCleanup() {
        assertEquals(
            CloudDeletionStageResult.Success,
            deletionRpcOutcomeToStageResult("DELETED"),
        )
        assertEquals(
            CloudDeletionStageResult.Success,
            deletionRpcOutcomeToStageResult("ALREADY_DELETED"),
        )
    }

    @Test
    fun missingRemoteMatchIsLocalCleanupSafeButAuthorizationFailureIsNot() {
        assertEquals(
            CloudDeletionStageResult.NotFound,
            deletionRpcOutcomeToStageResult("NOT_FOUND"),
        )
        assertEquals(
            CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.AUTHORIZATION),
            deletionRpcOutcomeToStageResult("NOT_FOUND_OR_NOT_OWNER"),
        )
        assertEquals(
            CloudDeletionStageResult.Failed(CloudDeletionFailureCategory.REMOTE),
            deletionRpcOutcomeToStageResult("UNEXPECTED"),
        )
    }
}
