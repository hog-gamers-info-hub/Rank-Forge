package com.hoggamers.rankforge.data.auth

import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import com.hoggamers.rankforge.domain.auth.AuthFailure
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory

internal enum class AuthFailureContext {
    Restore,
    SignUp,
    Login,
    GoogleSignIn,
    Logout,
    PasswordResetRequest,
    PasswordUpdate,
}

internal object AuthFailureMapper {
    fun map(
        throwable: Throwable,
        context: AuthFailureContext,
    ): AuthFailure {
        val classificationText = throwable.classificationText()

        if (throwable is AuthConfigurationException) {
            return AuthFailure(AuthFailureCategory.MissingSupabaseConfiguration)
        }
        if (throwable is TimeoutCancellationException ||
            throwable is SocketTimeoutException ||
            throwable is HttpRequestTimeoutException ||
            classificationText.contains("timeout")
        ) {
            return AuthFailure(AuthFailureCategory.Timeout)
        }
        if (throwable is IOException ||
            classificationText.contains("network") ||
            classificationText.contains("connection") ||
            classificationText.contains("unreachable") ||
            classificationText.contains("unknown host")
        ) {
            return AuthFailure(AuthFailureCategory.NetworkUnavailable)
        }

        if (throwable is RestException) {
            when (throwable.statusCode) {
                429 -> return AuthFailure(AuthFailureCategory.RateLimited)
                401, 403 -> {
                    return if (context == AuthFailureContext.Restore ||
                        context == AuthFailureContext.PasswordUpdate
                    ) {
                        AuthFailure(AuthFailureCategory.ExpiredOrInvalidSession)
                    } else {
                        AuthFailure(AuthFailureCategory.InvalidCredentials)
                    }
                }
            }
        }

        return when {
            classificationText.contains("invalid email") ||
                classificationText.contains("email is invalid") ->
                AuthFailure(AuthFailureCategory.InvalidEmail)
            classificationText.contains("invalid login") ||
                classificationText.contains("invalid credentials") ||
                classificationText.contains("invalid password") ->
                AuthFailure(AuthFailureCategory.InvalidCredentials)
            classificationText.contains("password") &&
                (classificationText.contains("weak") ||
                    classificationText.contains("minimum") ||
                    classificationText.contains("at least")) ->
                AuthFailure(AuthFailureCategory.WeakPassword)
            classificationText.contains("already registered") ||
                classificationText.contains("already exists") ->
                AuthFailure(AuthFailureCategory.AccountAlreadyRegistered)
            classificationText.contains("confirmation required") ||
                classificationText.contains("confirm your email") ||
                classificationText.contains("email not confirmed") ->
                AuthFailure(AuthFailureCategory.EmailConfirmationRequired)
            classificationText.contains("rate limit") ||
                classificationText.contains("too many requests") ->
                AuthFailure(AuthFailureCategory.RateLimited)
            classificationText.contains("expired session") ||
                classificationText.contains("invalid session") ||
                classificationText.contains("refresh token") ||
                (context == AuthFailureContext.PasswordUpdate &&
                    (classificationText.contains("recovery session") ||
                        classificationText.contains("expired token") ||
                        classificationText.contains("invalid token"))) ->
                AuthFailure(AuthFailureCategory.ExpiredOrInvalidSession)
            else -> AuthFailure(AuthFailureCategory.UnknownAuthenticationFailure)
        }
    }

    private fun Throwable.classificationText(): String =
        when (this) {
            is RestException -> listOf(error, description, statusCode.toString())
                .joinToString(" ")
            else -> message.orEmpty()
        }.lowercase()
}
