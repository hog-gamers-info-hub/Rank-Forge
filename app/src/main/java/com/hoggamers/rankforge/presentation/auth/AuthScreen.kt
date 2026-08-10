package com.hoggamers.rankforge.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.presentation.component.RankForgeScreenContainer
import com.hoggamers.rankforge.presentation.theme.RankForgeSpacing

const val AUTH_SCREEN_TEST_TAG = "auth_screen"
const val AUTH_EMAIL_FIELD_TEST_TAG = "auth_email_field"
const val AUTH_PASSWORD_FIELD_TEST_TAG = "auth_password_field"
const val AUTH_SUBMIT_ACTION_TEST_TAG = "auth_submit_action"
const val AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG = "auth_google_sign_in_action"
const val AUTH_LOGOUT_ACTION_TEST_TAG = "auth_logout_action"
const val AUTH_STATUS_TEST_TAG = "auth_status"
const val AUTH_WARNING_TEST_TAG = "auth_warning"
const val AUTH_ERROR_TEST_TAG = "auth_error"

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onModeSelected: (AuthMode) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
) {
    RankForgeScreenContainer(
        modifier = Modifier.testTag(AUTH_SCREEN_TEST_TAG),
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.auth_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        AuthAccountCard(
            uiState = uiState,
            onLogout = onLogout,
        )

        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        AuthModeSelector(
            selectedMode = uiState.mode,
            onModeSelected = onModeSelected,
        )

        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            label = { Text(text = stringResource(R.string.auth_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AUTH_EMAIL_FIELD_TEST_TAG),
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            label = { Text(text = stringResource(R.string.auth_password_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AUTH_PASSWORD_FIELD_TEST_TAG),
        )
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))

        Button(
            onClick = onSubmit,
            enabled = uiState.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AUTH_SUBMIT_ACTION_TEST_TAG),
        ) {
            Text(
                text = stringResource(
                    if (uiState.isSubmitting) {
                        R.string.auth_submitting_action
                    } else if (uiState.mode == AuthMode.Login) {
                        R.string.auth_login_action
                    } else {
                        R.string.auth_signup_action
                    },
                ),
            )
        }

        if (!uiState.isSignedIn) {
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            OutlinedButton(
                onClick = onGoogleSignIn,
                enabled = !uiState.isSubmitting && !uiState.isSessionLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.auth_google_sign_in_action))
            }
        }

        uiState.statusMessage?.let { message ->
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            Text(
                text = message.asText(),
                modifier = Modifier.testTag(AUTH_STATUS_TEST_TAG),
            )
        }
        uiState.warningMessage?.let { message ->
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            Text(
                text = message.asText(),
                modifier = Modifier.testTag(AUTH_WARNING_TEST_TAG),
            )
        }
        uiState.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
            Text(
                text = message.asText(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(AUTH_ERROR_TEST_TAG),
            )
        }

        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.back_to_tournament_list_action))
        }
    }
}

@Composable
private fun AuthAccountCard(
    uiState: AuthUiState,
    onLogout: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(RankForgeSpacing.Medium),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = when {
                    uiState.isSessionLoading -> stringResource(R.string.auth_checking_session)
                    uiState.isSignedIn -> stringResource(
                        R.string.auth_signed_in_as,
                        uiState.accountEmail ?: stringResource(R.string.auth_unknown_account),
                    )
                    else -> stringResource(R.string.auth_signed_out)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (uiState.isSignedIn) {
                Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AUTH_LOGOUT_ACTION_TEST_TAG),
                ) {
                    Text(text = stringResource(R.string.auth_logout_action))
                }
            }
        }
    }
}

@Composable
private fun AuthModeSelector(
    selectedMode: AuthMode,
    onModeSelected: (AuthMode) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = { onModeSelected(AuthMode.Login) },
            enabled = selectedMode != AuthMode.Login,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.auth_login_mode))
        }
        OutlinedButton(
            onClick = { onModeSelected(AuthMode.SignUp) },
            enabled = selectedMode != AuthMode.SignUp,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = stringResource(R.string.auth_signup_mode))
        }
    }
}

@Composable
private fun AuthUiMessage.asText(): String =
    when (this) {
        AuthUiMessage.MissingCredentials -> stringResource(R.string.auth_missing_credentials_error)
        AuthUiMessage.SignedIn -> stringResource(R.string.auth_signed_in_message)
        AuthUiMessage.ExternalAuthenticationLaunched ->
            stringResource(R.string.auth_external_authentication_launched_message)
        AuthUiMessage.SignUpAuthenticated -> stringResource(R.string.auth_signup_authenticated_message)
        AuthUiMessage.SignUpConfirmationRequired -> stringResource(
            R.string.auth_signup_confirmation_required_message,
        )
        AuthUiMessage.SignedOut -> stringResource(R.string.auth_signed_out_message)
        AuthUiMessage.LogoutRemoteWarning -> stringResource(R.string.auth_logout_remote_warning)
        is AuthUiMessage.AuthenticationFailure -> this.asFailureText()
        is AuthUiMessage.RestorationWarning -> this.asRestorationWarningText()
    }

@Composable
private fun AuthUiMessage.AuthenticationFailure.asFailureText(): String =
    stringResource(category.failureMessageResource())

@Composable
private fun AuthUiMessage.RestorationWarning.asRestorationWarningText(): String =
    stringResource(category.restorationWarningResource())

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
