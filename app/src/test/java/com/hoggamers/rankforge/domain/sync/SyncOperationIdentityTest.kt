package com.hoggamers.rankforge.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SyncOperationIdentityTest {
    @Test fun sameLogicalOperationProducesTheSameStableIdentity() {
        val first = SyncOperationIdentity.from(SyncQueueOperationType.TOURNAMENT_UPLOAD, "tournament-id")
        val second = SyncOperationIdentity.from(SyncQueueOperationType.TOURNAMENT_UPLOAD, "tournament-id")

        assertEquals(first, second)
        assertEquals(first.stableKey, second.stableKey)
    }

    @Test fun differentOperationTypesAndTournamentIdsProduceDifferentIdentities() {
        val tournamentId = "tournament-id"
        val upload = SyncOperationIdentity.from(SyncQueueOperationType.TOURNAMENT_UPLOAD, tournamentId)
        SyncQueueOperationType.entries
            .filterNot { it == SyncQueueOperationType.TOURNAMENT_UPLOAD }
            .forEach { operationType ->
                assertNotEquals(upload.stableKey, SyncOperationIdentity.from(operationType, tournamentId).stableKey)
            }
        assertNotEquals(upload.stableKey, SyncOperationIdentity.from(SyncQueueOperationType.TOURNAMENT_UPLOAD, "other-tournament").stableKey)
    }

    @Test fun rosterReplacementHasItsOwnTournamentScopedIdentity() {
        val rosterReplacement = SyncOperationIdentity.from(
            SyncQueueOperationType.ROSTER_REPLACEMENT,
            "tournament-id",
        )

        assertNotEquals(
            rosterReplacement.stableKey,
            SyncOperationIdentity.from(SyncQueueOperationType.TOURNAMENT_UPLOAD, "tournament-id").stableKey,
        )
        assertEquals(
            rosterReplacement.stableKey,
            SyncOperationIdentity.from(SyncQueueOperationType.ROSTER_REPLACEMENT, "tournament-id").stableKey,
        )
    }
}
