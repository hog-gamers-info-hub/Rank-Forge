package com.hoggamers.rankforge.domain.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AuthRestorationConnectivityCoordinator @Inject constructor() {
    private val _connectivityAvailable = MutableStateFlow(false)
    val connectivityAvailable: StateFlow<Boolean> = _connectivityAvailable.asStateFlow()

    fun onConnectivityAvailable() {
        _connectivityAvailable.value = true
    }

    fun consumeConnectivityAvailability(): Boolean {
        if (!_connectivityAvailable.value) return false
        _connectivityAvailable.value = false
        return true
    }
}
