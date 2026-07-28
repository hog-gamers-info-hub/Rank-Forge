package com.hoggamers.rankforge

import android.app.Application
import com.hoggamers.rankforge.data.connectivity.ForegroundConnectivityObserver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RankForgeApplication : Application() {
    @Inject lateinit var foregroundConnectivityObserver: ForegroundConnectivityObserver

    override fun onCreate() {
        super.onCreate()
        foregroundConnectivityObserver.start()
    }

    override fun onTerminate() {
        foregroundConnectivityObserver.stop()
        super.onTerminate()
    }
}
