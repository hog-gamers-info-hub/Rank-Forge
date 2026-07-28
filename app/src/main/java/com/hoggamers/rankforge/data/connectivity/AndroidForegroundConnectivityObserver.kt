package com.hoggamers.rankforge.data.connectivity

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.hoggamers.rankforge.domain.sync.ForegroundConnectivityRetryAction
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

interface ForegroundConnectivityObserver {
    fun start()
    fun stop()
}

@Singleton
class AndroidForegroundConnectivityObserver @Inject constructor(
    @ApplicationContext context: Context,
    private val retryAction: ForegroundConnectivityRetryAction,
) : ForegroundConnectivityObserver, Application.ActivityLifecycleCallbacks {
    private val application = context.applicationContext as Application
    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var startedActivityCount = 0
    private var isNetworkCallbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            dispatchNetworkAvailability()
        }
    }

    override fun start() {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun stop() {
        application.unregisterActivityLifecycleCallbacks(this)
        unregisterNetworkCallback()
        scope.cancel()
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount += 1
        if (startedActivityCount == 1) {
            registerNetworkCallback()
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        if (startedActivityCount == 0) {
            unregisterNetworkCallback()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun registerNetworkCallback() {
        if (isNetworkCallbackRegistered) return
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            isNetworkCallbackRegistered = true
        } catch (_: SecurityException) {
            // The observer is best-effort and must not affect app startup.
        }
    }

    private fun unregisterNetworkCallback() {
        if (!isNetworkCallbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
            // The platform may already have removed the callback.
        } finally {
            isNetworkCallbackRegistered = false
        }
    }

    private fun dispatchNetworkAvailability() {
        if (startedActivityCount == 0) return
        scope.launch {
            try {
                retryAction.onConnectivityChanged(isNetworkAvailable = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Connectivity-triggered recovery must not affect the foreground app flow.
            }
        }
    }
}
