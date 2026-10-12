package com.hoggamers.rankforge.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupRotationV4SyncJournalPayloadTest {
    @Test
    fun canonicalPayloadHashIsStableForTheExactFrozenText() {
        val payload = "{\"p_expected_revision\":4,\"p_request_id\":\"request\"}"

        val first = GroupRotationV4SyncJournalPayload.sha256(payload)
        val second = GroupRotationV4SyncJournalPayload.sha256(payload)

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun changingFrozenPayloadTextChangesItsHash() {
        val first = GroupRotationV4SyncJournalPayload.sha256("{\"revision\":4}")
        val second = GroupRotationV4SyncJournalPayload.sha256("{\"revision\":5}")

        assertNotEquals(first, second)
    }

    @Test
    fun onlyJsonObjectsAreAcceptedAsV4ParameterPayloads() {
        assertTrue(GroupRotationV4SyncJournalPayload.isJsonObject("{\"value\":1}"))
        assertFalse(GroupRotationV4SyncJournalPayload.isJsonObject("[]"))
        assertFalse(GroupRotationV4SyncJournalPayload.isJsonObject("not-json"))
    }
}
