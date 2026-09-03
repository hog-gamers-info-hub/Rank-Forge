package com.hoggamers.rankforge.presentation.screen

import com.hoggamers.rankforge.data.local.MatchCalculatedEvidence
import com.hoggamers.rankforge.data.local.MatchCalculatedEvidenceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class MatchCalculatedEvidenceSaveStatus {
    IDLE,
    SAVING,
    CLEARING,
    SAVED,
    FAILED,
}

internal class MatchCalculatedEvidenceSaveCoordinator(
    private val scheduler: ScreenshotReconciliationScheduler,
    private val ownerProvider: ScreenshotOwnerProvider,
    private val repository: MatchCalculatedEvidenceRepository,
    private val onStatusChanged: (generation: Long, status: MatchCalculatedEvidenceSaveStatus) -> Unit,
) {
    private val writeMutex = Mutex()

    fun schedule(
        generation: Long,
        tournamentId: String,
        matchId: String,
        evidence: MatchCalculatedEvidence,
        isCurrentGeneration: (Long) -> Boolean,
    ): Job {
        onStatusChanged(generation, MatchCalculatedEvidenceSaveStatus.SAVING)
        return scheduler.schedule {
            val ownerUserId = ownerProvider.currentOwnerUserId()
                ?.takeIf { it.isNotBlank() }
            val saved = if (ownerUserId == null) {
                false
            } else {
                try {
                    writeMutex.withLock {
                        if (!isCurrentGeneration(generation)) {
                            false
                        } else {
                            withContext(NonCancellable) {
                                repository.save(
                                    ownerUserId = ownerUserId,
                                    tournamentId = tournamentId,
                                    matchId = matchId,
                                    evidence = evidence,
                                )
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    false
                }
            }
            if (isCurrentGeneration(generation)) {
                onStatusChanged(
                    generation,
                    if (saved) MatchCalculatedEvidenceSaveStatus.SAVED
                    else MatchCalculatedEvidenceSaveStatus.FAILED,
                )
            }
        }
    }

    fun clear(
        generation: Long,
        tournamentId: String,
        matchId: String,
        isCurrentGeneration: (Long) -> Boolean,
        onCompleted: (Boolean) -> Unit,
    ): Job {
        onStatusChanged(generation, MatchCalculatedEvidenceSaveStatus.CLEARING)
        return scheduler.schedule {
            val ownerUserId = ownerProvider.currentOwnerUserId()
                ?.takeIf { it.isNotBlank() }
            val deleted = if (ownerUserId == null) {
                false
            } else {
                try {
                    writeMutex.withLock {
                        if (!isCurrentGeneration(generation)) {
                            false
                        } else {
                            withContext(NonCancellable) {
                                repository.delete(
                                    ownerUserId = ownerUserId,
                                    tournamentId = tournamentId,
                                    matchId = matchId,
                                )
                            }
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    false
                }
            }
            if (isCurrentGeneration(generation)) {
                onCompleted(deleted)
                onStatusChanged(
                    generation,
                    if (deleted) MatchCalculatedEvidenceSaveStatus.IDLE
                    else MatchCalculatedEvidenceSaveStatus.FAILED,
                )
            }
        }
    }
}
