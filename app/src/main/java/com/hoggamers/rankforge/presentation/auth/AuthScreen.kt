package com.hoggamers.rankforge.presentation.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hoggamers.rankforge.R
import com.hoggamers.rankforge.domain.auth.AuthFailureCategory
import com.hoggamers.rankforge.domain.auth.AccountDeletionFailureCategory
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
const val AUTH_PASSWORD_VISIBILITY_TEST_TAG = "auth_password_visibility"
const val AUTH_SIGNUP_HEADING_TEST_TAG = "auth_signup_heading"
const val AUTH_ACCOUNT_HOME_ACTION_TEST_TAG = "auth_account_home_action"
const val AUTH_ACCOUNT_BACK_ACTION_TEST_TAG = "auth_account_back_action"
const val AUTH_ACCOUNT_EMAIL_TEST_TAG = "auth_account_email"
const val AUTH_FORGOT_PASSWORD_ACTION_TEST_TAG = "auth_forgot_password_action"
const val AUTH_PASSWORD_RESET_SUBMIT_ACTION_TEST_TAG = "auth_password_reset_submit_action"
const val AUTH_PASSWORD_RECOVERY_BACK_ACTION_TEST_TAG = "auth_password_recovery_back_action"
const val AUTH_NEW_PASSWORD_FIELD_TEST_TAG = "auth_new_password_field"
const val AUTH_CONFIRM_NEW_PASSWORD_FIELD_TEST_TAG = "auth_confirm_new_password_field"
const val AUTH_NEW_PASSWORD_VISIBILITY_TEST_TAG = "auth_new_password_visibility"
const val AUTH_CONFIRM_NEW_PASSWORD_VISIBILITY_TEST_TAG = "auth_confirm_new_password_visibility"
const val AUTH_UPDATE_PASSWORD_ACTION_TEST_TAG = "auth_update_password_action"
const val AUTH_PASSWORD_VALIDATION_ERROR_TEST_TAG = "auth_password_validation_error"
const val AUTH_DELETE_ACCOUNT_ACTION_TEST_TAG = "auth_delete_account_action"
const val AUTH_DELETE_ACCOUNT_CONFIRMATION_TEST_TAG = "auth_delete_account_confirmation"
const val AUTH_DELETE_ACCOUNT_CONFIRM_TEST_TAG = "auth_delete_account_confirm"
const val AUTH_DELETE_ACCOUNT_CANCEL_TEST_TAG = "auth_delete_account_cancel"
const val AUTH_DELETE_ACCOUNT_PROGRESS_TEST_TAG = "auth_delete_account_progress"
const val AUTH_DELETE_ACCOUNT_PENDING_LOCAL_CLEANUP_TEST_TAG = "auth_delete_account_pending_local_cleanup"

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onModeSelected: (AuthMode) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onLogout: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onSignedInHome: () -> Unit = {},
    onSignedInBack: () -> Unit = {},
    onBeginPasswordRecovery: () -> Unit = {},
    onCancelPasswordRecovery: () -> Unit = {},
    onRequestPasswordReset: () -> Unit = {},
    onNewPasswordChanged: (String) -> Unit = {},
    onConfirmNewPasswordChanged: (String) -> Unit = {},
    onUpdateRecoveredPassword: () -> Unit = {},
    onExitPasswordRecovery: () -> Unit = {},
    onDeleteAccountConfirmed: () -> Unit = {},
) {
    RankForgeScreenContainer(
        modifier = Modifier
            .testTag(AUTH_SCREEN_TEST_TAG)
            .then(
                if (!uiState.isSignedIn) {
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        if (uiState.accountDeletionState == AccountDeletionUiState.REMOTE_DELETED_PENDING_LOCAL_CLEANUP ||
            uiState.accountDeletionState == AccountDeletionUiState.RECOVERY_REQUIRED
        ) {
            AccountDeletionPendingLocalCleanupContent(uiState.accountDeletionState)
        } else if (uiState.isSignedIn) {
            SignedInAuthContent(
                uiState = uiState,
                onLogout = onLogout,
                onHome = onSignedInHome,
                onBack = onSignedInBack,
                onDeleteAccountConfirmed = onDeleteAccountConfirmed,
            )
        } else if (uiState.passwordRecoveryStage == PasswordRecoveryStage.REQUEST_EMAIL) {
            PasswordRecoveryRequestContent(
                uiState = uiState,
                onEmailChanged = onEmailChanged,
                onRequestPasswordReset = onRequestPasswordReset,
                onCancelPasswordRecovery = onCancelPasswordRecovery,
            )
        } else if (uiState.passwordRecoveryStage == PasswordRecoveryStage.EMAIL_SENT) {
            PasswordRecoveryEmailSentContent(
                uiState = uiState,
                onCancelPasswordRecovery = onCancelPasswordRecovery,
            )
        } else if (uiState.passwordRecoveryStage == PasswordRecoveryStage.VERIFYING_LINK) {
            PasswordRecoveryVerifyingContent()
        } else if (uiState.passwordRecoveryStage == PasswordRecoveryStage.LINK_ERROR) {
            PasswordRecoveryLinkErrorContent(
                onExitPasswordRecovery = onExitPasswordRecovery,
            )
        } else if (uiState.passwordRecoveryStage == PasswordRecoveryStage.SET_NEW_PASSWORD) {
            PasswordRecoverySetNewPasswordContent(
                uiState = uiState,
                onNewPasswordChanged = onNewPasswordChanged,
                onConfirmNewPasswordChanged = onConfirmNewPasswordChanged,
                onUpdateRecoveredPassword = onUpdateRecoveredPassword,
                onExitPasswordRecovery = onExitPasswordRecovery,
            )
        } else if (uiState.mode == AuthMode.SignUp) {
            SignUpAuthContent(
                uiState = uiState,
                onModeSelected = onModeSelected,
                onEmailChanged = onEmailChanged,
                onPasswordChanged = onPasswordChanged,
                onSubmit = onSubmit,
                onGoogleSignIn = onGoogleSignIn,
            )
        } else {
            LoginAuthContent(
                uiState = uiState,
                onModeSelected = onModeSelected,
                onEmailChanged = onEmailChanged,
                onPasswordChanged = onPasswordChanged,
                onSubmit = onSubmit,
                onGoogleSignIn = onGoogleSignIn,
                onBeginPasswordRecovery = onBeginPasswordRecovery,
            )
        }
    }
}

@Composable
private fun LoginAuthContent(
    uiState: AuthUiState,
    onModeSelected: (AuthMode) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onBeginPasswordRecovery: () -> Unit,
) {
    Text(
        text = stringResource(R.string.auth_brand_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
    Text(
        text = stringResource(R.string.auth_login_heading),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    AuthModeSelector(
        selectedMode = uiState.mode,
        onModeSelected = onModeSelected,
        emphasizeSelection = true,
        loginModeLabelRes = R.string.auth_log_in_mode,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))

    OutlinedTextField(
        value = uiState.email,
        onValueChange = onEmailChanged,
        label = { Text(text = stringResource(R.string.auth_email_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().testTag(AUTH_EMAIL_FIELD_TEST_TAG),
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = uiState.password,
        onValueChange = onPasswordChanged,
        label = { Text(text = stringResource(R.string.auth_password_label)) },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = !uiState.isSubmitting,
                modifier = Modifier.testTag(AUTH_PASSWORD_VISIBILITY_TEST_TAG),
            ) {
                Text(
                    text = stringResource(
                        if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag(AUTH_PASSWORD_FIELD_TEST_TAG),
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    TextButton(
        onClick = onBeginPasswordRecovery,
        enabled = !uiState.isSubmitting,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_FORGOT_PASSWORD_ACTION_TEST_TAG),
    ) {
        Text(text = stringResource(R.string.auth_forgot_password_action))
    }
    Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
    Button(
        onClick = onSubmit,
        enabled = uiState.canSubmit,
        modifier = Modifier.fillMaxWidth().testTag(AUTH_SUBMIT_ACTION_TEST_TAG),
    ) {
        Text(
            text = stringResource(
                if (uiState.isSubmitting) R.string.auth_submitting_action
                else R.string.auth_log_in_action,
            ),
        )
    }

    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.auth_or_divider),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    OutlinedButton(
        onClick = onGoogleSignIn,
        enabled = !uiState.isSubmitting && !uiState.isSessionLoading,
        modifier = Modifier.fillMaxWidth().testTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG),
    ) {
        Text(text = stringResource(R.string.auth_google_continue_action))
    }

    AuthMessages(uiState = uiState)
}

@Composable
private fun PasswordRecoveryRequestContent(
    uiState: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onRequestPasswordReset: () -> Unit,
    onCancelPasswordRecovery: () -> Unit,
) {
    Text(
        text = stringResource(R.string.auth_brand_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
    Text(
        text = stringResource(R.string.auth_forgot_password_heading),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
    Text(text = stringResource(R.string.auth_forgot_password_explanation))
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    OutlinedTextField(
        value = uiState.email,
        onValueChange = onEmailChanged,
        label = { Text(text = stringResource(R.string.auth_email_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().testTag(AUTH_EMAIL_FIELD_TEST_TAG),
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    Button(
        onClick = onRequestPasswordReset,
        enabled = uiState.canRequestPasswordReset,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_PASSWORD_RESET_SUBMIT_ACTION_TEST_TAG),
    ) {
        Text(
            text = stringResource(
                if (uiState.isSubmitting) R.string.auth_submitting_action
                else R.string.auth_send_reset_link_action,
            ),
        )
    }
    TextButton(
        onClick = onCancelPasswordRecovery,
        enabled = !uiState.isSubmitting,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_PASSWORD_RECOVERY_BACK_ACTION_TEST_TAG),
    ) {
        Text(text = stringResource(R.string.auth_back_to_log_in_action))
    }
    AuthMessages(uiState = uiState)
}

@Composable
private fun PasswordRecoveryEmailSentContent(
    uiState: AuthUiState,
    onCancelPasswordRecovery: () -> Unit,
) {
    Text(
        text = stringResource(R.string.auth_brand_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
    Text(
        text = stringResource(R.string.auth_check_email_heading),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
    Text(text = stringResource(R.string.auth_password_reset_email_sent_message))
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    TextButton(
        onClick = onCancelPasswordRecovery,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_PASSWORD_RECOVERY_BACK_ACTION_TEST_TAG),
    ) {
        Text(text = stringResource(R.string.auth_back_to_log_in_action))
    }
    AuthMessages(uiState = uiState)
}

@Composable
private fun PasswordRecoveryVerifyingContent() {
    Text(
        text = stringResource(R.string.auth_brand_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
    Text(
        text = stringResource(R.string.auth_verifying_password_reset_link),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    CircularProgressIndicator()
}

@Composable
private fun PasswordRecoveryLinkErrorContent(
    onExitPasswordRecovery: () -> Unit,
) {
    Text(
        text = stringResource(R.string.auth_brand_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
    Text(
        text = stringResource(R.string.auth_password_reset_link_error),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    TextButton(
        onClick = onExitPasswordRecovery,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_PASSWORD_RECOVERY_BACK_ACTION_TEST_TAG),
    ) {
        Text(text = stringResource(R.string.auth_back_to_log_in_action))
    }
}

@Composable
private fun PasswordRecoverySetNewPasswordContent(
    uiState: AuthUiState,
    onNewPasswordChanged: (String) -> Unit,
    onConfirmNewPasswordChanged: (String) -> Unit,
    onUpdateRecoveredPassword: () -> Unit,
    onExitPasswordRecovery: () -> Unit,
) {
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.auth_brand_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
    Text(
        text = stringResource(R.string.auth_set_new_password_heading),
        style = MaterialTheme.typography.headlineMedium,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    OutlinedTextField(
        value = uiState.newPassword,
        onValueChange = onNewPasswordChanged,
        label = { Text(text = stringResource(R.string.auth_new_password_label)) },
        singleLine = true,
        visualTransformation = if (newPasswordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(
                onClick = { newPasswordVisible = !newPasswordVisible },
                enabled = !uiState.isSubmitting,
                modifier = Modifier.testTag(AUTH_NEW_PASSWORD_VISIBILITY_TEST_TAG),
            ) {
                Text(
                    text = stringResource(
                        if (newPasswordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag(AUTH_NEW_PASSWORD_FIELD_TEST_TAG),
    )
    if (uiState.newPassword.isNotEmpty() && uiState.newPassword.length < MIN_AUTH_PASSWORD_LENGTH) {
        Text(
            text = stringResource(R.string.auth_password_too_short_error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(AUTH_PASSWORD_VALIDATION_ERROR_TEST_TAG),
        )
    }
    Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
    OutlinedTextField(
        value = uiState.confirmNewPassword,
        onValueChange = onConfirmNewPasswordChanged,
        label = { Text(text = stringResource(R.string.auth_confirm_new_password_label)) },
        singleLine = true,
        visualTransformation = if (confirmPasswordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            TextButton(
                onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                enabled = !uiState.isSubmitting,
                modifier = Modifier.testTag(AUTH_CONFIRM_NEW_PASSWORD_VISIBILITY_TEST_TAG),
            ) {
                Text(
                    text = stringResource(
                        if (confirmPasswordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag(AUTH_CONFIRM_NEW_PASSWORD_FIELD_TEST_TAG),
    )
    if (uiState.confirmNewPassword.isNotEmpty() &&
        uiState.newPassword != uiState.confirmNewPassword
    ) {
        Text(
            text = stringResource(R.string.auth_passwords_do_not_match_error),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(AUTH_PASSWORD_VALIDATION_ERROR_TEST_TAG),
        )
    }
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    Button(
        onClick = onUpdateRecoveredPassword,
        enabled = uiState.canUpdateRecoveredPassword,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_UPDATE_PASSWORD_ACTION_TEST_TAG),
    ) {
        Text(
            text = stringResource(
                if (uiState.isSubmitting) R.string.auth_submitting_action
                else R.string.auth_update_password_action,
            ),
        )
    }
    TextButton(
        onClick = onExitPasswordRecovery,
        enabled = !uiState.isSubmitting,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_PASSWORD_RECOVERY_BACK_ACTION_TEST_TAG),
    ) {
        Text(text = stringResource(R.string.auth_back_to_log_in_action))
    }
    AuthMessages(uiState = uiState)
}

@Composable
private fun SignedInAuthContent(
    uiState: AuthUiState,
    onLogout: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onDeleteAccountConfirmed: () -> Unit,
) {
    val deletionInProgress = uiState.accountDeletionState == AccountDeletionUiState.DELETING
    BackHandler(enabled = true) {
        if (!deletionInProgress) onBack()
    }
    var showDeleteAccountConfirmation by remember { mutableStateOf(false) }

    val pointIqNavy = Color(0xFF071B3E)
    val pointIqBlue = Color(0xFF176AF7)
    val pointIqBody = Color(0xFF607393)
    val pointIqAccountContainer = Color(0xFFF7FAFF)
    val pointIqProfileBackground = Color(0xFFF0F6FF)
    val pointIqProfileBorder = Color(0xFFBBD5FF)
    val pointIqDanger = Color(0xFFD92D3A)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onHome,
            enabled = !deletionInProgress,
            modifier = Modifier.testTag(AUTH_ACCOUNT_HOME_ACTION_TEST_TAG),
        ) {
            Text(
                text = stringResource(R.string.auth_home_action),
                color = pointIqBlue,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            onClick = onBack,
            enabled = !deletionInProgress,
            modifier = Modifier.testTag(AUTH_ACCOUNT_BACK_ACTION_TEST_TAG),
        ) {
            Text(
                text = stringResource(R.string.back_action),
                color = pointIqBlue,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
    Text(
        text = stringResource(R.string.auth_account_section_title),
        style = MaterialTheme.typography.headlineMedium,
        color = pointIqNavy,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.ExtraSmall))
    Text(
        text = stringResource(R.string.pointiq_account_description),
        style = MaterialTheme.typography.bodyMedium,
        color = pointIqBody,
    )

    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = pointIqAccountContainer,
                shape = RoundedCornerShape(RankForgeSpacing.Medium),
            )
            .padding(RankForgeSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PointIqAccountProfileIcon(
            color = pointIqBlue,
            backgroundColor = pointIqProfileBackground,
            borderColor = pointIqProfileBorder,
        )
        Spacer(modifier = Modifier.width(RankForgeSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.pointiq_signed_in_account_label),
                style = MaterialTheme.typography.labelLarge,
                color = pointIqBlue,
            )
            Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
            Text(
                text = uiState.accountEmail?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.auth_unknown_account),
                style = MaterialTheme.typography.titleMedium,
                color = pointIqNavy,
                modifier = Modifier.testTag(AUTH_ACCOUNT_EMAIL_TEST_TAG),
            )
        }
    }

    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    OutlinedButton(
        onClick = onLogout,
        enabled = !deletionInProgress && !uiState.isSubmitting,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = pointIqDanger,
        ),
        shape = RoundedCornerShape(RankForgeSpacing.Medium),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_LOGOUT_ACTION_TEST_TAG),
    ) {
        Text(
            text = stringResource(R.string.auth_logout_action),
            style = MaterialTheme.typography.labelLarge,
        )
    }

    Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
    OutlinedButton(
        onClick = { showDeleteAccountConfirmation = true },
        enabled = !deletionInProgress && !uiState.isSubmitting,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = pointIqDanger,
        ),
        shape = RoundedCornerShape(RankForgeSpacing.Medium),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_DELETE_ACCOUNT_ACTION_TEST_TAG),
    ) {
        Text(
            text = stringResource(R.string.auth_delete_account_action),
            style = MaterialTheme.typography.labelLarge,
        )
    }

    if (deletionInProgress) {
        Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AUTH_DELETE_ACCOUNT_PROGRESS_TEST_TAG),
            horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text(text = stringResource(R.string.auth_delete_account_in_progress))
        }
    }

    if (showDeleteAccountConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!deletionInProgress) showDeleteAccountConfirmation = false
            },
            modifier = Modifier.testTag(AUTH_DELETE_ACCOUNT_CONFIRMATION_TEST_TAG),
            title = {
                Text(text = stringResource(R.string.auth_delete_account_confirmation_title))
            },
            text = {
                Text(text = stringResource(R.string.auth_delete_account_confirmation_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountConfirmation = false
                        onDeleteAccountConfirmed()
                    },
                    enabled = !deletionInProgress,
                    modifier = Modifier.testTag(AUTH_DELETE_ACCOUNT_CONFIRM_TEST_TAG),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = pointIqDanger,
                    ),
                ) {
                    Text(text = stringResource(R.string.auth_delete_account_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountConfirmation = false },
                    enabled = !deletionInProgress,
                    modifier = Modifier.testTag(AUTH_DELETE_ACCOUNT_CANCEL_TEST_TAG),
                ) {
                    Text(text = stringResource(R.string.auth_delete_account_cancel_action))
                }
            },
        )
    }

    AuthMessages(uiState = uiState)
}

@Composable
private fun AccountDeletionPendingLocalCleanupContent(
    state: AccountDeletionUiState,
) {
    BackHandler(enabled = true) {}
    Text(
        text = stringResource(
            if (state == AccountDeletionUiState.RECOVERY_REQUIRED) {
                R.string.auth_delete_account_recovery_required
            } else {
                R.string.auth_delete_account_pending_local_cleanup
            },
        ),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.testTag(AUTH_DELETE_ACCOUNT_PENDING_LOCAL_CLEANUP_TEST_TAG),
    )
}

@Composable
private fun PointIqAccountProfileIcon(
    color: Color,
    backgroundColor: Color,
    borderColor: Color,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .background(backgroundColor, CircleShape)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(46.dp)) {
            val strokeWidth = 2.8.dp.toPx()
            drawCircle(
                color = color,
                radius = size.minDimension * 0.18f,
                center = Offset(size.width / 2f, size.height * 0.31f),
                style = Stroke(width = strokeWidth),
            )
            drawArc(
                color = color,
                startAngle = 205f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(size.width * 0.17f, size.height * 0.48f),
                size = Size(size.width * 0.66f, size.height * 0.44f),
                style = Stroke(width = strokeWidth),
            )
        }
    }
}

@Composable
private fun SignUpAuthContent(
    uiState: AuthUiState,
    onModeSelected: (AuthMode) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
) {
    Text(
        text = stringResource(R.string.auth_brand_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Small))
    Text(
        text = stringResource(R.string.auth_signup_heading),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.testTag(AUTH_SIGNUP_HEADING_TEST_TAG),
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    AuthModeSelector(
        selectedMode = uiState.mode,
        onModeSelected = onModeSelected,
        emphasizeSelection = true,
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))

    OutlinedTextField(
        value = uiState.email,
        onValueChange = onEmailChanged,
        label = { Text(text = stringResource(R.string.auth_email_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().testTag(AUTH_EMAIL_FIELD_TEST_TAG),
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
    var passwordVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = uiState.password,
        onValueChange = onPasswordChanged,
        label = { Text(text = stringResource(R.string.auth_password_label)) },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = !uiState.isSubmitting,
                modifier = Modifier.testTag(AUTH_PASSWORD_VISIBILITY_TEST_TAG),
            ) {
                Text(
                    text = stringResource(
                        if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag(AUTH_PASSWORD_FIELD_TEST_TAG),
    )
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    Button(
        onClick = onSubmit,
        enabled = uiState.canSubmit,
        modifier = Modifier.fillMaxWidth().testTag(AUTH_SUBMIT_ACTION_TEST_TAG),
    ) {
        Text(
            text = stringResource(
                if (uiState.isSubmitting) R.string.auth_submitting_action
                else R.string.auth_create_account_action,
            ),
        )
    }

    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        modifier = Modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.auth_or_divider),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(RankForgeSpacing.Large))
    OutlinedButton(
        onClick = onGoogleSignIn,
        enabled = !uiState.isSubmitting && !uiState.isSessionLoading,
        modifier = Modifier.fillMaxWidth().testTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG),
    ) {
        Text(text = stringResource(R.string.auth_google_continue_action))
    }

    AuthMessages(uiState = uiState)
}

@Composable
private fun AuthMessages(uiState: AuthUiState) {
    uiState.statusMessage?.let { message ->
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Text(text = message.asText(), modifier = Modifier.testTag(AUTH_STATUS_TEST_TAG))
    }
    uiState.warningMessage?.let { message ->
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Text(text = message.asText(), modifier = Modifier.testTag(AUTH_WARNING_TEST_TAG))
    }
    uiState.errorMessage?.let { message ->
        Spacer(modifier = Modifier.height(RankForgeSpacing.Medium))
        Text(
            text = message.asText(),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(AUTH_ERROR_TEST_TAG),
        )
    }
}

@Composable
private fun AuthModeSelector(
    selectedMode: AuthMode,
    onModeSelected: (AuthMode) -> Unit,
    emphasizeSelection: Boolean = false,
    loginModeLabelRes: Int = R.string.auth_login_mode,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RankForgeSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (emphasizeSelection) {
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(RankForgeSpacing.Small),
                )
                .padding(RankForgeSpacing.ExtraSmall)
        } else {
            Modifier.fillMaxWidth()
        },
    ) {
        if (emphasizeSelection) {
            AuthModeButton(
                mode = AuthMode.Login,
                selectedMode = selectedMode,
                onModeSelected = onModeSelected,
                loginModeLabelRes = loginModeLabelRes,
            )
            AuthModeButton(
                mode = AuthMode.SignUp,
                selectedMode = selectedMode,
                onModeSelected = onModeSelected,
                loginModeLabelRes = loginModeLabelRes,
            )
        } else {
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
}

@Composable
private fun RowScope.AuthModeButton(
    mode: AuthMode,
    selectedMode: AuthMode,
    onModeSelected: (AuthMode) -> Unit,
    loginModeLabelRes: Int,
) {
    val selected = mode == selectedMode
    TextButton(
        onClick = { onModeSelected(mode) },
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Transparent
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        shape = RoundedCornerShape(RankForgeSpacing.Small),
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = stringResource(
                if (mode == AuthMode.Login) loginModeLabelRes else R.string.auth_signup_mode,
            ),
        )
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
        AuthUiMessage.PasswordsDoNotMatch -> stringResource(R.string.auth_passwords_do_not_match_error)
        AuthUiMessage.PasswordTooShort -> stringResource(R.string.auth_password_too_short_error)
        AuthUiMessage.PasswordUpdated -> stringResource(R.string.auth_password_updated_message)
        is AuthUiMessage.AuthenticationFailure -> this.asFailureText()
        is AuthUiMessage.RestorationWarning -> this.asRestorationWarningText()
        is AuthUiMessage.AccountDeletionFailure -> this.asAccountDeletionFailureText()
    }

@Composable
private fun AuthUiMessage.AccountDeletionFailure.asAccountDeletionFailureText(): String =
    stringResource(
        when (category) {
            AccountDeletionFailureCategory.NO_SESSION -> R.string.auth_delete_account_no_session
            AccountDeletionFailureCategory.NETWORK -> R.string.auth_delete_account_network_failure
            AccountDeletionFailureCategory.AUTHENTICATION -> R.string.auth_delete_account_authentication_failure
            AccountDeletionFailureCategory.SERVER -> R.string.auth_delete_account_server_failure
            AccountDeletionFailureCategory.UNKNOWN -> R.string.auth_delete_account_unknown_failure
        },
    )

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
