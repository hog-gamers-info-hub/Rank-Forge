package com.hoggamers.rankforge.data.auth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

fun interface AuthFirstLaunchStore {
    fun consumeFirstLaunch(): Boolean
}

object NoOpAuthFirstLaunchStore : AuthFirstLaunchStore {
    override fun consumeFirstLaunch(): Boolean = false
}

@Singleton
class SharedPreferencesAuthFirstLaunchStore @Inject constructor(
    @ApplicationContext context: Context,
) : AuthFirstLaunchStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun consumeFirstLaunch(): Boolean {
        if (preferences.getBoolean(HAS_LAUNCHED_KEY, false)) {
            return false
        }
        preferences.edit().putBoolean(HAS_LAUNCHED_KEY, true).apply()
        return true
    }

    private companion object {
        const val PREFERENCES_NAME = "pointiq_auth_preferences"
        const val HAS_LAUNCHED_KEY = "has_launched"
    }
}
