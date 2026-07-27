package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUiStateReducerTest {
    @Test
    fun signedInSessionMapsToSignedInUiState() {
        val uiState = AuthUiStateReducer.reduceSession(
            currentState = AuthUiState(isSessionLoading = true),
            authState = AuthState.SignedIn(AuthUser(id = "user-id", email = "user@example.com")),
        )

        assertTrue(uiState.isSignedIn)
        assertFalse(uiState.isSessionLoading)
        assertEquals("user@example.com", uiState.accountEmail)
    }

    @Test
    fun signedOutSessionKeepsLocalModeAvailable() {
        val uiState = AuthUiStateReducer.reduceSession(
            currentState = AuthUiState(accountEmail = "user@example.com", isSignedIn = true),
            authState = AuthState.SignedOut,
        )

        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.accountEmail)
    }

    @Test
    fun failedOperationMapsToReadableError() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(isSubmitting = true),
            result = AuthOperationResult.Failure("Invalid login credentials."),
            successMessage = AuthUiMessage.SignedIn,
        )

        assertFalse(uiState.isSubmitting)
        assertEquals(AuthUiMessage.Text("Invalid login credentials."), uiState.errorMessage)
    }

    @Test
    fun successfulOperationClearsSubmittingState() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(isSubmitting = true),
            result = AuthOperationResult.Success,
            successMessage = AuthUiMessage.SignUpSubmitted,
        )

        assertFalse(uiState.isSubmitting)
        assertEquals(AuthUiMessage.SignUpSubmitted, uiState.statusMessage)
    }
}
