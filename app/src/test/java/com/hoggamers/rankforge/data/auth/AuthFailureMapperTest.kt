package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthFailureMapperTest {
    @Test
    fun mapsPasswordResetInvalidEmailToInvalidEmail() {
        assertEquals(
            AuthFailureCategory.InvalidEmail,
            AuthFailureMapper.map(
                IllegalStateException("Invalid email"),
                AuthFailureContext.PasswordResetRequest,
            ).category,
        )
    }

    @Test
    fun mapsPasswordResetRateLimitToRateLimited() {
        assertEquals(
            AuthFailureCategory.RateLimited,
            AuthFailureMapper.map(
                IllegalStateException("Rate limit exceeded"),
                AuthFailureContext.PasswordResetRequest,
            ).category,
        )
    }

    @Test
    fun mapsPasswordUpdateWeakPasswordToWeakPassword() {
        assertEquals(
            AuthFailureCategory.WeakPassword,
            AuthFailureMapper.map(
                IllegalStateException("Password is too weak"),
                AuthFailureContext.PasswordUpdate,
            ).category,
        )
    }

    @Test
    fun mapsPasswordUpdateInvalidRecoverySessionToExpiredOrInvalidSession() {
        assertEquals(
            AuthFailureCategory.ExpiredOrInvalidSession,
            AuthFailureMapper.map(
                IllegalStateException("Invalid recovery session token"),
                AuthFailureContext.PasswordUpdate,
            ).category,
        )
    }

    @Test
    fun mapsEveryApprovedFailureCategoryToStableDomainCategory() {
        val cases = listOf(
            IllegalStateException("Invalid login credentials") to AuthFailureCategory.InvalidCredentials,
            IllegalStateException("Invalid email") to AuthFailureCategory.InvalidEmail,
            IllegalStateException("Password should be at least 8 characters") to AuthFailureCategory.WeakPassword,
            IllegalStateException("User already registered") to AuthFailureCategory.AccountAlreadyRegistered,
            IllegalStateException("Email confirmation required") to AuthFailureCategory.EmailConfirmationRequired,
            IllegalStateException("Rate limit exceeded") to AuthFailureCategory.RateLimited,
            IOException("network unavailable") to AuthFailureCategory.NetworkUnavailable,
            SocketTimeoutException("request timed out") to AuthFailureCategory.Timeout,
            IllegalStateException("refresh token is invalid") to AuthFailureCategory.ExpiredOrInvalidSession,
            AuthConfigurationException() to AuthFailureCategory.MissingSupabaseConfiguration,
            IllegalStateException("unexpected backend detail") to AuthFailureCategory.UnknownAuthenticationFailure,
        )

        cases.forEach { (throwable, expectedCategory) ->
            assertEquals(
                expectedCategory,
                AuthFailureMapper.map(throwable, AuthFailureContext.Restore).category,
            )
        }
    }
}
