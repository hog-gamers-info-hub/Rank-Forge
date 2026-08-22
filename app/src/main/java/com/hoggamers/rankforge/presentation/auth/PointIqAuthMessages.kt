package com.hoggamers.rankforge.presentation.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory

@Composable
internal fun PointIqAuthMessages(uiState: AuthUiState) {
    uiState.statusMessage?.let { message ->
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message.asPointIqText(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(AUTH_STATUS_TEST_TAG),
        )
    }
    uiState.warningMessage?.let { message ->
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message.asPointIqText(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(AUTH_WARNING_TEST_TAG),
        )
    }
    uiState.errorMessage?.let { message ->
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message.asPointIqText(),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(AUTH_ERROR_TEST_TAG),
        )
    }
}

@Composable
private fun AuthUiMessage.asPointIqText(): String =
    when (this) {
        AuthUiMessage.MissingCredentials -> stringResource(R.string.auth_missing_credentials_error)
        AuthUiMessage.SignedIn -> stringResource(R.string.auth_signed_in_message)
        AuthUiMessage.ExternalAuthenticationLaunched ->
            stringResource(R.string.auth_external_authentication_launched_message)
        AuthUiMessage.SignUpAuthenticated -> stringResource(R.string.auth_signup_authenticated_message)
        AuthUiMessage.SignUpConfirmationRequired ->
            stringResource(R.string.auth_signup_confirmation_required_message)
        AuthUiMessage.SignedOut -> stringResource(R.string.auth_signed_out_message)
        AuthUiMessage.LogoutRemoteWarning -> stringResource(R.string.auth_logout_remote_warning)
        AuthUiMessage.PasswordsDoNotMatch -> stringResource(R.string.auth_passwords_do_not_match_error)
        AuthUiMessage.PasswordTooShort -> stringResource(R.string.auth_password_too_short_error)
        AuthUiMessage.PasswordUpdated -> stringResource(R.string.auth_password_updated_message)
        is AuthUiMessage.AuthenticationFailure -> stringResource(category.failureMessageResource())
        is AuthUiMessage.RestorationWarning -> stringResource(category.restorationWarningResource())
    }

private fun AuthFailureCategory.failureMessageResource(): Int =
    when (this) {
        AuthFailureCategory.InvalidCredentials -> R.string.auth_failure_invalid_credentials
        AuthFailureCategory.InvalidEmail -> R.string.auth_failure_invalid_email
        AuthFailureCategory.WeakPassword -> R.string.auth_failure_weak_password
        AuthFailureCategory.AccountAlreadyRegistered -> R.string.auth_failure_account_already_registered
        AuthFailureCategory.EmailConfirmationRequired -> R.string.auth_failure_email_confirmation_required
        AuthFailureCategory.RateLimited -> R.string.auth_failure_rate_limited
        AuthFailureCategory.NetworkUnavailable -> R.string.auth_failure_network_unavailable
        AuthFailureCategory.Timeout -> R.string.auth_failure_timeout
        AuthFailureCategory.ExpiredOrInvalidSession -> R.string.auth_failure_expired_or_invalid_session
        AuthFailureCategory.MissingSupabaseConfiguration -> R.string.auth_failure_missing_configuration
        AuthFailureCategory.UnknownAuthenticationFailure -> R.string.auth_failure_unknown
    }

private fun AuthFailureCategory.restorationWarningResource(): Int =
    when (this) {
        AuthFailureCategory.NetworkUnavailable -> R.string.auth_restoration_network_warning
        AuthFailureCategory.Timeout -> R.string.auth_restoration_timeout_warning
        else -> R.string.auth_restoration_unknown_warning
    }
