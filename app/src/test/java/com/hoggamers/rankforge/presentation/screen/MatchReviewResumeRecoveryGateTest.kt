package com.hoggamers.rankforge.presentation.screen

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchReviewResumeRecoveryGateTest {
    @Test
    fun initialAttachWhileAlreadyResumedDoesNotConsumeRecovery() {
        val gate = MatchReviewResumeRecoveryGate()

        assertEquals(
            MatchReviewResumeRecoveryAction.NONE,
            gate.onLifecycleEvent(Lifecycle.Event.ON_RESUME),
        )
    }

    @Test
    fun observerReattachmentWhileAlreadyResumedDoesNotConsumeRecovery() {
        val reattachedGate = MatchReviewResumeRecoveryGate()

        assertEquals(
            MatchReviewResumeRecoveryAction.NONE,
            reattachedGate.onLifecycleEvent(Lifecycle.Event.ON_RESUME),
        )
    }

    @Test
    fun freshOcrReadyTransitionWithoutLifecycleEventDoesNotConsumeRecovery() {
        val gate = MatchReviewResumeRecoveryGate()

        assertEquals(
            MatchReviewResumeRecoveryAction.NONE,
            gate.onLifecycleEvent(Lifecycle.Event.ON_CREATE),
        )
        assertEquals(
            MatchReviewResumeRecoveryAction.NONE,
            gate.onLifecycleEvent(Lifecycle.Event.ON_RESUME),
        )
    }

    @Test
    fun genuinePauseAndResumeConsumesRecoveryOnce() {
        val gate = MatchReviewResumeRecoveryGate()

        assertEquals(
            MatchReviewResumeRecoveryAction.ARMED,
            gate.onLifecycleEvent(Lifecycle.Event.ON_PAUSE),
        )
        assertEquals(
            MatchReviewResumeRecoveryAction.CONSUMED,
            gate.onLifecycleEvent(Lifecycle.Event.ON_RESUME),
        )
        assertEquals(
            MatchReviewResumeRecoveryAction.NONE,
            gate.onLifecycleEvent(Lifecycle.Event.ON_RESUME),
        )
    }

    @Test
    fun stopThenResumeConsumesDedicatedSnapshotRecoveryOnce() {
        val gate = MatchReviewResumeRecoveryGate()

        assertEquals(
            MatchReviewResumeRecoveryAction.ARMED,
            gate.onLifecycleEvent(Lifecycle.Event.ON_STOP),
        )
        assertEquals(
            MatchReviewResumeRecoveryAction.CONSUMED,
            gate.onLifecycleEvent(Lifecycle.Event.ON_RESUME),
        )
        assertEquals(
            MatchReviewResumeRecoveryAction.NONE,
            gate.onLifecycleEvent(Lifecycle.Event.ON_RESUME),
        )
    }

    @Test
    fun recompositionsAfterResumeDoNotCreateAdditionalRecovery() {
        val gate = MatchReviewResumeRecoveryGate()

        gate.onLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertEquals(
            MatchReviewResumeRecoveryAction.CONSUMED,
            gate.onLifecycleEvent(Lifecycle.Event.ON_RESUME),
        )
        repeat(3) {
            assertEquals(
                MatchReviewResumeRecoveryAction.NONE,
                gate.onLifecycleEvent(Lifecycle.Event.ON_CREATE),
            )
        }
    }
}
