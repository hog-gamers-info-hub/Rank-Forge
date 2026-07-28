package com.hoggamers.rankforge.domain.sync

import com.hoggamers.rankforge.data.cloud.toRevisionConflict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RevisionConflictTest {
    @Test
    fun sameBaseAndCloudRevisionHasNoConflict() {
        val state = LocalRevisionState(localRevision = 4, baseCloudRevision = CloudRevision(4))

        assertNull(state.detectDivergence(CloudRevision(4)))
    }

    @Test
    fun newerCloudRevisionDetectsLocalCloudDivergence() {
        val state = LocalRevisionState(localRevision = 5, baseCloudRevision = CloudRevision(4))

        assertEquals(
            RevisionConflict.LocalCloudDivergence(CloudRevision(4), 5, CloudRevision(6)),
            state.detectDivergence(CloudRevision(6)),
        )
    }

    @Test
    fun missingRevisionHasNoWriteExpectationAndUsesStableFailureMetadata() {
        assertNull(LocalRevisionState.Missing.expectedRevisionForWrite())
        assertEquals("MISSING_REVISION", RevisionConflict.MissingRevision.queueFailureCategory())
    }

    @Test
    fun staleRpcResponseMapsToNonDestructiveStaleWriteConflict() {
        assertEquals(
            RevisionConflict.StaleWrite(CloudRevision(3), CloudRevision(4)),
            com.hoggamers.rankforge.data.cloud.RevisionWriteResponse("stale_write", 4)
                .toRevisionConflict(3),
        )
    }
}
