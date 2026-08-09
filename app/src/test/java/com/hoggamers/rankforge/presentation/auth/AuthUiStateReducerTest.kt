package com.hoggamers.rankforge.presentation.auth

import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AuthOperationResult
import com.hoggamers.rankforge.domain.auth.AuthRestorationResult
import com.hoggamers.rankforge.domain.auth.AuthState
import com.hoggamers.rankforge.domain.auth.AuthSuccessOutcome
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
    fun temporaryRestorationFailureShowsWarningWithoutSigningIn() {
        val uiState = AuthUiStateReducer.reduceRestoration(
            currentState = AuthUiState(isSessionLoading = true),
            result = AuthRestorationResult.TemporaryFailure(
                AuthFailure(AuthFailureCategory.NetworkUnavailable),
            ),
        )

        assertFalse(uiState.isSignedIn)
        assertFalse(uiState.isSessionLoading)
        assertEquals(
            AuthUiMessage.RestorationWarning(AuthFailureCategory.NetworkUnavailable),
            uiState.warningMessage,
        )
    }

    @Test
    fun expiredRestorationShowsTypedSignInAgainError() {
        val uiState = AuthUiStateReducer.reduceRestoration(
            currentState = AuthUiState(isSessionLoading = true),
            result = AuthRestorationResult.ExpiredOrInvalid(
                AuthFailure(AuthFailureCategory.ExpiredOrInvalidSession),
            ),
        )

        assertFalse(uiState.isSignedIn)
        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.ExpiredOrInvalidSession),
            uiState.errorMessage,
        )
    }

    @Test
    fun failedOperationUsesTypedErrorWithoutRawText() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(isSubmitting = true),
            result = AuthOperationResult.Failure(
                AuthFailure(AuthFailureCategory.InvalidCredentials),
            ),
        )

        assertFalse(uiState.isSubmitting)
        assertEquals(
            AuthUiMessage.AuthenticationFailure(AuthFailureCategory.InvalidCredentials),
            uiState.errorMessage,
        )
    }

    @Test
    fun immediateSignUpMapsToSignedInOutcome() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(
                email = "new@example.com",
                isSubmitting = true,
            ),
            result = AuthOperationResult.Success(AuthSuccessOutcome.SignUpAuthenticated),
        )

        assertTrue(uiState.isSignedIn)
        assertEquals(AuthUiMessage.SignUpAuthenticated, uiState.statusMessage)
    }

    @Test
    fun confirmationRequiredSignUpRemainsSignedOut() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(isSubmitting = true),
            result = AuthOperationResult.Success(AuthSuccessOutcome.EmailConfirmationRequired),
        )

        assertFalse(uiState.isSignedIn)
        assertEquals(AuthUiMessage.SignUpConfirmationRequired, uiState.statusMessage)
    }

    @Test
    fun externalAuthenticationLaunchReturnsToIdleWithoutSigningInOrFakingEmail() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(
                email = "typed@example.com",
                accountEmail = "existing@example.com",
                isSubmitting = true,
            ),
            result = AuthOperationResult.Success(
                AuthSuccessOutcome.ExternalAuthenticationLaunched,
            ),
        )

        assertFalse(uiState.isSubmitting)
        assertFalse(uiState.isSignedIn)
        assertEquals("existing@example.com", uiState.accountEmail)
        assertEquals(AuthUiMessage.ExternalAuthenticationLaunched, uiState.statusMessage)
        assertEquals(null, uiState.errorMessage)
    }

    @Test
    fun remoteLogoutWarningStillLeavesSignedOutState() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(
                accountEmail = "user@example.com",
                isSignedIn = true,
                isSubmitting = true,
            ),
            result = AuthOperationResult.Success(
                AuthSuccessOutcome.SignedOutLocallyWithRemoteFailure(
                    AuthFailure(AuthFailureCategory.NetworkUnavailable),
                ),
            ),
        )

        assertFalse(uiState.isSignedIn)
        assertEquals(AuthUiMessage.LogoutRemoteWarning, uiState.warningMessage)
    }
}
