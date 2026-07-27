package com.hoggamers.rankforge.domain.auth

sealed interface AuthOperationResult {
    data object Success : AuthOperationResult
    data class Failure(val message: String) : AuthOperationResult
}
