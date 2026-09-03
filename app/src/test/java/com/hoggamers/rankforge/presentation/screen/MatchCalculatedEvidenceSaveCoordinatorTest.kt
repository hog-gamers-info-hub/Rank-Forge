package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.local.MatchCalculatedEvidenceRepository
import com.hoggamers.rankforge.data.local.LobbyCalculatedEvidence
import com.hoggamers.rankforge.data.local.LobbyTeamCalculatedEvidence
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MatchCalculatedEvidenceSaveCoordinatorTest {
    @Test
    fun validEvidenceSaveOutlivesCallerCancellationAndCanBeReadBack() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schedulerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val scheduler = ScreenshotReconciliationScheduler(schedulerScope, testOnly = true)
        val repository = ControlledEvidenceRepository()
        val control = repository.addControl()
        val statuses = mutableListOf<MatchCalculatedEvidenceSaveStatus>()
        val coordinator = coordinator(scheduler, repository, statuses)
        val evidence = MatchCalculatedEvidence()

        val callerJob = launch {
            coordinator.schedule(
                generation = 1L,
                tournamentId = TOURNAMENT_ID,
                matchId = MATCH_ID,
                evidence = evidence,
                isCurrentGeneration = { it == 1L },
            )
            awaitCancellation()
        }
        runCurrent()
        assertTrue(control.started.isCompleted)

        callerJob.cancel()
        control.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(evidence, repository.read("owner-id", TOURNAMENT_ID, MATCH_ID))
        assertEquals(
            listOf(
                MatchCalculatedEvidenceSaveStatus.SAVING,
                MatchCalculatedEvidenceSaveStatus.SAVED,
            ),
            statuses,
        )
        assertEquals("owner-id", repository.lastOwnerId)
        schedulerScope.cancel()
    }

    @Test
    fun repositoryFalseResultBecomesFailedStatus() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = ScreenshotReconciliationScheduler(schedulerScope, testOnly = true)
        val repository = ControlledEvidenceRepository()
        repository.addControl(result = false).release.complete(Unit)
        val statuses = mutableListOf<MatchCalculatedEvidenceSaveStatus>()

        coordinator(scheduler, repository, statuses).schedule(
            generation = 1L,
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            evidence = MatchCalculatedEvidence(),
            isCurrentGeneration = { true },
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                MatchCalculatedEvidenceSaveStatus.SAVING,
                MatchCalculatedEvidenceSaveStatus.FAILED,
            ),
            statuses,
        )
        schedulerScope.cancel()
    }

    @Test
    fun repositoryExceptionBecomesFailedStatus() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = ScreenshotReconciliationScheduler(schedulerScope, testOnly = true)
        val repository = ControlledEvidenceRepository()
        repository.addControl(failure = IllegalStateException("save failed")).release.complete(Unit)
        val statuses = mutableListOf<MatchCalculatedEvidenceSaveStatus>()

        coordinator(scheduler, repository, statuses).schedule(
            generation = 1L,
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            evidence = MatchCalculatedEvidence(),
            isCurrentGeneration = { true },
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                MatchCalculatedEvidenceSaveStatus.SAVING,
                MatchCalculatedEvidenceSaveStatus.FAILED,
            ),
            statuses,
        )
        schedulerScope.cancel()
    }

    @Test
    fun laterSaveWaitsForStartedWriteAndSuccessfulLaterSaveReplacesIt() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schedulerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val scheduler = ScreenshotReconciliationScheduler(schedulerScope, testOnly = true)
        val repository = ControlledEvidenceRepository()
        val firstControl = repository.addControl()
        val secondControl = repository.addControl()
        val coordinator = coordinator(scheduler, repository, mutableListOf())
        val firstEvidence = evidenceWithTeamName("first")
        val secondEvidence = evidenceWithTeamName("second")

        coordinator.schedule(1L, TOURNAMENT_ID, MATCH_ID, firstEvidence) { true }
        runCurrent()
        assertTrue(firstControl.started.isCompleted)

        coordinator.schedule(2L, TOURNAMENT_ID, MATCH_ID, secondEvidence) { true }
        runCurrent()
        assertFalse(secondControl.started.isCompleted)

        firstControl.release.complete(Unit)
        runCurrent()
        assertTrue(secondControl.started.isCompleted)

        secondControl.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(secondEvidence, repository.read("owner-id", TOURNAMENT_ID, MATCH_ID))
        schedulerScope.cancel()
    }

    @Test
    fun clearWaitsForStartedSaveAndLeavesRepositoryEmpty() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schedulerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val scheduler = ScreenshotReconciliationScheduler(schedulerScope, testOnly = true)
        val repository = ControlledEvidenceRepository()
        val saveControl = repository.addControl()
        var completed: Boolean? = null
        val coordinator = coordinator(scheduler, repository, mutableListOf())

        coordinator.schedule(1L, TOURNAMENT_ID, MATCH_ID, evidenceWithTeamName("saved")) { true }
        runCurrent()
        assertTrue(saveControl.started.isCompleted)

        coordinator.clear(
            generation = 2L,
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            isCurrentGeneration = { true },
            onCompleted = { completed = it },
        )
        runCurrent()
        assertFalse(repository.deleteCalled)

        saveControl.release.complete(Unit)
        advanceUntilIdle()

        assertTrue(completed == true)
        assertNull(repository.read("owner-id", TOURNAMENT_ID, MATCH_ID))
        assertTrue(repository.deleteCalled)
        schedulerScope.cancel()
    }

    @Test
    fun staleQueuedSaveCannotResurrectEvidenceAfterClear() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val schedulerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val scheduler = ScreenshotReconciliationScheduler(schedulerScope, testOnly = true)
        val repository = ControlledEvidenceRepository().also {
            it.seed(evidenceWithTeamName("before-clear"))
        }
        val activeSaveControl = repository.addControl()
        val staleSaveControl = repository.addControl()
        var currentGeneration = 1L
        var completed: Boolean? = null
        val coordinator = coordinator(scheduler, repository, mutableListOf())

        coordinator.schedule(1L, TOURNAMENT_ID, MATCH_ID, evidenceWithTeamName("active")) {
            it == currentGeneration
        }
        runCurrent()
        assertTrue(activeSaveControl.started.isCompleted)

        currentGeneration = 2L
        coordinator.schedule(2L, TOURNAMENT_ID, MATCH_ID, evidenceWithTeamName("stale")) {
            it == currentGeneration
        }
        currentGeneration = 3L
        coordinator.clear(
            generation = 3L,
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            isCurrentGeneration = { it == currentGeneration },
            onCompleted = { completed = it },
        )

        activeSaveControl.release.complete(Unit)
        advanceUntilIdle()

        assertFalse(staleSaveControl.started.isCompleted)
        assertTrue(completed == true)
        assertNull(repository.read("owner-id", TOURNAMENT_ID, MATCH_ID))
        schedulerScope.cancel()
    }

    @Test
    fun freshSaveAfterClearCreatesNewEvidence() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = ScreenshotReconciliationScheduler(schedulerScope, testOnly = true)
        val repository = ControlledEvidenceRepository().also {
            it.seed(evidenceWithTeamName("before-clear"))
        }
        var currentGeneration = 1L
        var completed: Boolean? = null
        val coordinator = coordinator(scheduler, repository, mutableListOf())

        coordinator.clear(
            generation = 1L,
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            isCurrentGeneration = { it == currentGeneration },
            onCompleted = { completed = it },
        )
        advanceUntilIdle()
        assertTrue(completed == true)

        currentGeneration = 2L
        val replacement = evidenceWithTeamName("after-clear")
        coordinator.schedule(2L, TOURNAMENT_ID, MATCH_ID, replacement) {
            it == currentGeneration
        }
        advanceUntilIdle()

        assertEquals(replacement, repository.read("owner-id", TOURNAMENT_ID, MATCH_ID))
        schedulerScope.cancel()
    }

    @Test
    fun failedClearKeepsExistingEvidence() = runTest {
        val schedulerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val scheduler = ScreenshotReconciliationScheduler(schedulerScope, testOnly = true)
        val repository = ControlledEvidenceRepository().also {
            it.seed(evidenceWithTeamName("keep"))
            it.deleteResult = false
        }
        var completed: Boolean? = null

        coordinator(scheduler, repository, mutableListOf()).clear(
            generation = 1L,
            tournamentId = TOURNAMENT_ID,
            matchId = MATCH_ID,
            isCurrentGeneration = { true },
            onCompleted = { completed = it },
        )
        advanceUntilIdle()

        assertFalse(completed == true)
        assertEquals(evidenceWithTeamName("keep"), repository.read("owner-id", TOURNAMENT_ID, MATCH_ID))
        schedulerScope.cancel()
    }

    private fun coordinator(
        scheduler: ScreenshotReconciliationScheduler,
        repository: ControlledEvidenceRepository,
        statuses: MutableList<MatchCalculatedEvidenceSaveStatus>,
    ) = MatchCalculatedEvidenceSaveCoordinator(
        scheduler = scheduler,
        ownerProvider = FixedOwnerProvider,
        repository = repository,
        onStatusChanged = { _, status -> statuses += status },
    )

    private fun evidenceWithTeamName(teamName: String): MatchCalculatedEvidence = MatchCalculatedEvidence(
        lobby = LobbyCalculatedEvidence(
            teams = listOf(
                LobbyTeamCalculatedEvidence(
                    slotNumber = 1,
                    teamName = teamName,
                    sourceScreenshotIndex = 0,
                    cropLeft = 0.0,
                    cropTop = 0.0,
                    cropRight = 1.0,
                    cropBottom = 1.0,
                    playerNames = listOf(null, null, null, null),
                ),
            ),
        ),
    )

    private class ControlledEvidenceRepository : MatchCalculatedEvidenceRepository {
        private val controls = mutableListOf<SaveControl>()
        private var saveCount = 0
        private var savedEvidence: MatchCalculatedEvidence? = null
        var deleteResult: Boolean = true
        var deleteCalled: Boolean = false
            private set
        var lastOwnerId: String? = null
            private set

        fun seed(evidence: MatchCalculatedEvidence) {
            savedEvidence = evidence
        }

        fun addControl(
            result: Boolean = true,
            failure: Throwable? = null,
        ): SaveControl = SaveControl(
            result = result,
            failure = failure,
        ).also(controls::add)

        override suspend fun save(
            ownerUserId: String,
            tournamentId: String,
            matchId: String,
            evidence: MatchCalculatedEvidence,
        ): Boolean {
            lastOwnerId = ownerUserId
            val control = controls.getOrNull(saveCount++) ?: SaveControl.completed()
            control.started.complete(Unit)
            control.release.await()
            control.failure?.let { throw it }
            if (control.result) savedEvidence = evidence
            return control.result
        }

        override suspend fun read(
            ownerUserId: String,
            tournamentId: String,
            matchId: String,
        ): MatchCalculatedEvidence? = savedEvidence

        override suspend fun delete(ownerUserId: String, tournamentId: String, matchId: String): Boolean {
            deleteCalled = true
            lastOwnerId = ownerUserId
            if (!deleteResult) return false
            val hadEvidence = savedEvidence != null
            savedEvidence = null
            return hadEvidence
        }
    }

    private data class SaveControl(
        val result: Boolean = true,
        val failure: Throwable? = null,
        val started: CompletableDeferred<Unit> = CompletableDeferred(),
        val release: CompletableDeferred<Unit> = CompletableDeferred(),
    ) {
        companion object {
            fun completed(): SaveControl = SaveControl(
                release = CompletableDeferred<Unit>().apply { complete(Unit) },
            )
        }
    }

    private object FixedOwnerProvider : ScreenshotOwnerProvider {
        override suspend fun currentOwnerUserId(): String = "owner-id"
    }

    private companion object {
        const val TOURNAMENT_ID = "tournament-id"
        const val MATCH_ID = "match-id"
    }
}
