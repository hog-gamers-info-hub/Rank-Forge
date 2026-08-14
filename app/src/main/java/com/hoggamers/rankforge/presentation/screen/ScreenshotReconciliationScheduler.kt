package com.hoggamers.rankforge.presentation.screen

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns fire-and-forget screenshot reconciliation outside a crop ViewModel.
 * The scope is application-lifetime through the singleton component.
 */
@Singleton
class ScreenshotReconciliationScheduler private constructor(
    private val scope: CoroutineScope,
) {
    @Inject
    constructor() : this(
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    internal constructor(scope: CoroutineScope, testOnly: Boolean) : this(scope)

    fun schedule(block: suspend () -> Unit): Job = scope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // The local crop is already authoritative; a later foreground retry remains available.
        }
    }
}
