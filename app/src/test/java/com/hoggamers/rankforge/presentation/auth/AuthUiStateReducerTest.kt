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
    fun defaultStateStartsWithoutPasswordRecovery() {
        val uiState = AuthUiState()

        assertEquals(PasswordRecoveryStage.NONE, uiState.passwordRecoveryStage)
        assertFalse(uiState.isPasswordRecoveryActive)
    }

    @Test
    fun beginPasswordRecoveryRequestMovesToRequestEmailAndClearsTransientState() {
        val uiState = AuthUiStateReducer.beginPasswordRecoveryRequest(
            AuthUiState(
                isSessionLoading = true,
                isSubmitting = true,
                warningMessage = AuthUiMessage.LogoutRemoteWarning,
                errorMessage = AuthUiMessage.AuthenticationFailure(
                    AuthFailureCategory.InvalidCredentials,
                ),
            ),
        )

        assertEquals(PasswordRecoveryStage.REQUEST_EMAIL, uiState.passwordRecoveryStage)
        assertFalse(uiState.isSessionLoading)
        assertFalse(uiState.isSubmitting)
        assertEquals(null, uiState.warningMessage)
        assertEquals(null, uiState.errorMessage)
    }

    @Test
    fun signedInSessionMapsToSignedInUiState() {
        val uiState = AuthUiStateReducer.reduceSession(
            currentState = AuthUiState(
                isSessionLoading = true,
                isExternalAuthCallbackProcessing = true,
            ),
            authState = AuthState.SignedIn(AuthUser(id = "user-id", email = "user@example.com")),
        )

        assertTrue(uiState.isSignedIn)
        assertFalse(uiState.isSessionLoading)
        assertFalse(uiState.isExternalAuthCallbackProcessing)
        assertEquals("user@example.com", uiState.accountEmail)
    }

    @Test
    fun externalAuthCallbackReceivedShowsLoadingWithoutSigningIn() {
        val uiState = AuthUiStateReducer.beginExternalAuthCallback(
            AuthUiState(statusMessage = AuthUiMessage.ExternalAuthenticationLaunched),
        )

        assertTrue(uiState.isSessionLoading)
        assertTrue(uiState.isExternalAuthCallbackProcessing)
        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.statusMessage)
    }

    @Test
    fun failedExternalAuthCallbackClearsLoadingWithoutSigningIn() {
        val uiState = AuthUiStateReducer.failExternalAuthCallback(
            AuthUiState(
                accountEmail = "stale@example.com",
                isSessionLoading = true,
                isSignedIn = true,
            ),
        )

        assertFalse(uiState.isSessionLoading)
        assertFalse(uiState.isExternalAuthCallbackProcessing)
        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.accountEmail)
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
    fun passwordResetEmailSuccessMovesRequestEmailToEmailSentWithoutAccountDisclosure() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(
                accountEmail = "existing@example.com",
                isSignedIn = true,
                isSubmitting = true,
                passwordRecoveryStage = PasswordRecoveryStage.REQUEST_EMAIL,
            ),
            result = AuthOperationResult.Success(
                AuthSuccessOutcome.PasswordResetEmailRequested,
            ),
        )

        assertEquals(PasswordRecoveryStage.EMAIL_SENT, uiState.passwordRecoveryStage)
        assertFalse(uiState.isSubmitting)
        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.accountEmail)
        assertEquals(null, uiState.statusMessage)
    }

    @Test
    fun passwordRecoveryLinkVerificationTransitionsAreDeterministic() {
        val verifying = AuthUiStateReducer.beginPasswordRecoveryLinkVerification(
            AuthUiState(passwordRecoveryStage = PasswordRecoveryStage.EMAIL_SENT),
        )
        val readyForPassword = AuthUiStateReducer.completePasswordRecoveryLinkVerification(verifying)
        val failed = AuthUiStateReducer.failPasswordRecoveryLinkVerification(verifying)

        assertEquals(PasswordRecoveryStage.VERIFYING_LINK, verifying.passwordRecoveryStage)
        assertEquals(PasswordRecoveryStage.SET_NEW_PASSWORD, readyForPassword.passwordRecoveryStage)
        assertEquals(PasswordRecoveryStage.LINK_ERROR, failed.passwordRecoveryStage)
        assertFalse(verifying.isSubmitting)
        assertFalse(readyForPassword.isSubmitting)
        assertFalse(failed.isSubmitting)
    }

    @Test
    fun activeRecoverySignedInSessionDoesNotBecomeNormalSignedInState() {
        val uiState = AuthUiStateReducer.reduceSession(
            currentState = AuthUiState(
                accountEmail = null,
                isSignedIn = false,
                passwordRecoveryStage = PasswordRecoveryStage.VERIFYING_LINK,
            ),
            authState = AuthState.SignedIn(
                AuthUser(id = "recovery-user", email = "user@example.com"),
            ),
        )

        assertEquals(PasswordRecoveryStage.VERIFYING_LINK, uiState.passwordRecoveryStage)
        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.accountEmail)
    }

    @Test
    fun setNewPasswordRecoverySessionDoesNotBecomeNormalSignedInState() {
        val uiState = AuthUiStateReducer.reduceSession(
            currentState = AuthUiState(
                passwordRecoveryStage = PasswordRecoveryStage.SET_NEW_PASSWORD,
            ),
            authState = AuthState.SignedIn(
                AuthUser(id = "recovery-user", email = "user@example.com"),
            ),
        )

        assertEquals(PasswordRecoveryStage.SET_NEW_PASSWORD, uiState.passwordRecoveryStage)
        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.accountEmail)
    }

    @Test
    fun restoredRecoverySessionDoesNotBecomeNormalSignedInState() {
        val uiState = AuthUiStateReducer.reduceRestoration(
            currentState = AuthUiState(
                passwordRecoveryStage = PasswordRecoveryStage.VERIFYING_LINK,
            ),
            result = AuthRestorationResult.Restored(
                AuthUser(id = "recovery-user", email = "user@example.com"),
            ),
        )

        assertEquals(PasswordRecoveryStage.VERIFYING_LINK, uiState.passwordRecoveryStage)
        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.accountEmail)
    }

    @Test
    fun normalSignedInSessionStillSignsInWithoutRecovery() {
        val uiState = AuthUiStateReducer.reduceSession(
            currentState = AuthUiState(),
            authState = AuthState.SignedIn(
                AuthUser(id = "user-id", email = "user@example.com"),
            ),
        )

        assertEquals(PasswordRecoveryStage.NONE, uiState.passwordRecoveryStage)
        assertTrue(uiState.isSignedIn)
        assertEquals("user@example.com", uiState.accountEmail)
    }

    @Test
    fun passwordUpdatedWhileRecoveringRemainsInRecovery() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(
                isSignedIn = false,
                isSubmitting = true,
                passwordRecoveryStage = PasswordRecoveryStage.SET_NEW_PASSWORD,
            ),
            result = AuthOperationResult.Success(AuthSuccessOutcome.PasswordUpdated),
        )

        assertEquals(PasswordRecoveryStage.SET_NEW_PASSWORD, uiState.passwordRecoveryStage)
        assertFalse(uiState.isSubmitting)
        assertFalse(uiState.isSignedIn)
    }

    @Test
    fun completePasswordRecoveryClearsRecoveryAndPasswordState() {
        val uiState = AuthUiStateReducer.completePasswordRecovery(
            currentState = AuthUiState(
                accountEmail = "recovery@example.com",
                isSignedIn = true,
                isSubmitting = true,
                newPassword = "new-password",
                confirmNewPassword = "new-password",
                passwordRecoveryStage = PasswordRecoveryStage.SET_NEW_PASSWORD,
                warningMessage = AuthUiMessage.LogoutRemoteWarning,
            ),
        )

        assertEquals(PasswordRecoveryStage.NONE, uiState.passwordRecoveryStage)
        assertFalse(uiState.isSubmitting)
        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.accountEmail)
        assertEquals("", uiState.newPassword)
        assertEquals("", uiState.confirmNewPassword)
        assertEquals(AuthUiMessage.PasswordUpdated, uiState.statusMessage)
        assertEquals(null, uiState.warningMessage)
    }

    @Test
    fun cancelPasswordRecoveryClearsOnlyTheRecoveryFlowState() {
        val uiState = AuthUiStateReducer.cancelPasswordRecovery(
            AuthUiState(
                passwordRecoveryStage = PasswordRecoveryStage.LINK_ERROR,
                isSubmitting = true,
                warningMessage = AuthUiMessage.LogoutRemoteWarning,
                errorMessage = AuthUiMessage.AuthenticationFailure(
                    AuthFailureCategory.InvalidCredentials,
                ),
            ),
        )

        assertEquals(PasswordRecoveryStage.NONE, uiState.passwordRecoveryStage)
        assertFalse(uiState.isSubmitting)
        assertEquals(null, uiState.warningMessage)
        assertEquals(null, uiState.errorMessage)
    }

    @Test
    fun passwordResetRequestedCompletesWithoutAuthenticating() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(
                email = "typed@example.com",
                accountEmail = null,
                isSignedIn = false,
                isSubmitting = true,
                statusMessage = AuthUiMessage.ExternalAuthenticationLaunched,
                warningMessage = AuthUiMessage.LogoutRemoteWarning,
                errorMessage = AuthUiMessage.AuthenticationFailure(
                    AuthFailureCategory.InvalidCredentials,
                ),
            ),
            result = AuthOperationResult.Success(
                AuthSuccessOutcome.PasswordResetEmailRequested,
            ),
        )

        assertFalse(uiState.isSubmitting)
        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.accountEmail)
        assertEquals(null, uiState.statusMessage)
        assertEquals(null, uiState.warningMessage)
        assertEquals(null, uiState.errorMessage)
    }

    @Test
    fun passwordUpdatedCompletesWithoutAuthenticating() {
        val uiState = AuthUiStateReducer.finishOperation(
            currentState = AuthUiState(
                email = "typed@example.com",
                accountEmail = null,
                isSignedIn = false,
                isSubmitting = true,
                statusMessage = AuthUiMessage.ExternalAuthenticationLaunched,
                warningMessage = AuthUiMessage.LogoutRemoteWarning,
                errorMessage = AuthUiMessage.AuthenticationFailure(
                    AuthFailureCategory.InvalidCredentials,
                ),
            ),
            result = AuthOperationResult.Success(AuthSuccessOutcome.PasswordUpdated),
        )

        assertFalse(uiState.isSubmitting)
        assertFalse(uiState.isSignedIn)
        assertEquals(null, uiState.accountEmail)
        assertEquals(AuthUiMessage.ExternalAuthenticationLaunched, uiState.statusMessage)
        assertEquals(null, uiState.warningMessage)
        assertEquals(null, uiState.errorMessage)
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
