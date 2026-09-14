package com.hoggamers.rankforge.presentation.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalAutofillHighlightColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.hoggamers.rankforge.R

private val SignUpText = Color(0xFFF6F8FF)
private val SignUpSecondary = Color(0xFF91AFE0)
private val SignUpInactive = Color(0xFF7D9DCE)
private val SignUpCyan = Color(0xFF17C9F2)
private val SignUpBorder = Color(0xFF176AF7).copy(alpha = 0.6f)
private val SignUpButtonStart = Color(0xFF159CF8)
private val SignUpButtonMiddle = Color(0xFF1688F7)
private val SignUpButtonEnd = Color(0xFF1675F0)
private val SignUpButtonBorder = Color(0xFF4AAFF7)
private val SignUpFieldSurface = Color(0xFF071B3E)
private val SignUpAutofillHighlight = Color(0xFF0B2B55)

@Composable
internal fun PointIqSignUpScreen(
    uiState: AuthUiState,
    onModeSelected: (AuthMode) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    messages: @Composable () -> Unit = {},
) {
    PointIqAuthShell(
        title = stringResource(R.string.auth_signup_heading),
        subtitle = stringResource(R.string.pointiq_signup_subtitle),
        modifier = modifier,
        titleModifier = Modifier.testTag(AUTH_SIGNUP_HEADING_TEST_TAG),
    ) {
        PointIqSignUpFieldLabel(text = stringResource(R.string.auth_email_label))
        Spacer(modifier = Modifier.height(6.dp))
        PointIqSignUpEmailField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            enabled = !uiState.isSubmitting,
        )

        Spacer(modifier = Modifier.height(14.dp))

        PointIqSignUpFieldLabel(text = stringResource(R.string.auth_password_label))
        Spacer(modifier = Modifier.height(6.dp))
        PointIqSignUpPasswordField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            enabled = !uiState.isSubmitting,
        )

        Spacer(modifier = Modifier.height(18.dp))

        PointIqSignUpPrimaryButton(
            text = stringResource(
                if (uiState.isSubmitting) R.string.auth_submitting_action
                else R.string.auth_create_account_action,
            ),
            enabled = uiState.canSubmit,
            onClick = onSubmit,
        )

        Spacer(modifier = Modifier.height(18.dp))
        PointIqSignUpDivider()
        Spacer(modifier = Modifier.height(18.dp))

        PointIqSignUpGoogleButton(
            enabled = !uiState.isSubmitting && !uiState.isSessionLoading,
            onClick = onGoogleSignIn,
        )

        PointIqLoginPrompt(
            onLogIn = { onModeSelected(AuthMode.Login) },
        )

        messages()
    }
}

@Composable
private fun PointIqSignUpFieldLabel(text: String) {
    Text(
        text = text,
        color = SignUpText,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PointIqSignUpEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    CompositionLocalProvider(LocalAutofillHighlightColor provides SignUpAutofillHighlight) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.pointiq_email_placeholder),
                    color = SignUpSecondary,
                )
            },
            leadingIcon = { PointIqSignUpMailIcon() },
            shape = RoundedCornerShape(16.dp),
            colors = pointIqSignUpFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AUTH_EMAIL_FIELD_TEST_TAG),
        )
    }
}

@Composable
private fun PointIqSignUpPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalAutofillHighlightColor provides SignUpAutofillHighlight) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.pointiq_password_placeholder),
                    color = SignUpSecondary,
                )
            },
            leadingIcon = { PointIqSignUpLockIcon() },
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    enabled = enabled,
                    modifier = Modifier.testTag(AUTH_PASSWORD_VISIBILITY_TEST_TAG),
                ) {
                    PointIqSignUpEyeIcon(visible = passwordVisible)
                }
            },
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            shape = RoundedCornerShape(16.dp),
            colors = pointIqSignUpFieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AUTH_PASSWORD_FIELD_TEST_TAG),
        )
    }
}

@Composable
private fun pointIqSignUpFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SignUpText,
    unfocusedTextColor = SignUpText,
    disabledTextColor = SignUpInactive,
    focusedBorderColor = SignUpCyan,
    unfocusedBorderColor = SignUpBorder,
    disabledBorderColor = SignUpBorder,
    focusedContainerColor = SignUpFieldSurface,
    unfocusedContainerColor = SignUpFieldSurface,
    disabledContainerColor = SignUpFieldSurface,
    cursorColor = SignUpCyan,
)

@Composable
private fun PointIqSignUpPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val gradient = Brush.horizontalGradient(
        listOf(SignUpButtonStart, SignUpButtonMiddle, SignUpButtonEnd),
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = androidx.compose.foundation.BorderStroke(1.dp, SignUpButtonBorder),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = SignUpText,
            disabledContentColor = SignUpText,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = 9.dp,
                shape = shape,
                ambientColor = Color.Transparent,
                spotColor = Color.Transparent,
            )
            .background(gradient, shape)
            .testTag(AUTH_SUBMIT_ACTION_TEST_TAG),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Center),
            )
            PointIqSignUpArrowIcon(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(22.dp),
            )
        }
    }
}

@Composable
private fun PointIqSignUpDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = SignUpBorder,
        )
        Text(
            text = stringResource(R.string.auth_or_divider),
            color = SignUpSecondary,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = SignUpBorder,
        )
    }
}

@Composable
private fun PointIqSignUpGoogleButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SignUpBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SignUpFieldSurface,
            contentColor = SignUpText,
            disabledContainerColor = SignUpFieldSurface,
            disabledContentColor = SignUpInactive,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag(AUTH_GOOGLE_SIGN_IN_ACTION_TEST_TAG),
    ) {
        Image(
            painter = painterResource(R.drawable.google_g_logo),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.auth_google_continue_action),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PointIqLoginPrompt(
    onLogIn: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.pointiq_have_account_prompt),
            color = SignUpSecondary,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onLogIn) {
            Text(
                text = stringResource(R.string.auth_log_in_mode),
                color = SignUpCyan,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PointIqSignUpMailIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.7.dp.toPx()
        val left = size.width * 0.12f
        val right = size.width * 0.88f
        val top = size.height * 0.24f
        val bottom = size.height * 0.76f

        drawRoundRect(
            color = SignUpSecondary,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawLine(SignUpSecondary, Offset(left + stroke, top + stroke), Offset(size.width / 2f, size.height * 0.52f), stroke)
        drawLine(SignUpSecondary, Offset(right - stroke, top + stroke), Offset(size.width / 2f, size.height * 0.52f), stroke)
    }
}

@Composable
private fun PointIqSignUpLockIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.7.dp.toPx()
        drawRoundRect(
            color = SignUpSecondary,
            topLeft = Offset(size.width * 0.2f, size.height * 0.43f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.43f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawArc(
            color = SignUpSecondary,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(size.width * 0.31f, size.height * 0.12f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.38f, size.height * 0.48f),
            style = Stroke(stroke),
        )
    }
}

@Composable
private fun PointIqSignUpEyeIcon(visible: Boolean) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.6.dp.toPx()
        val eye = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.5f)
            quadraticBezierTo(size.width * 0.28f, size.height * 0.18f, size.width * 0.5f, size.height * 0.18f)
            quadraticBezierTo(size.width * 0.72f, size.height * 0.18f, size.width * 0.92f, size.height * 0.5f)
            quadraticBezierTo(size.width * 0.72f, size.height * 0.82f, size.width * 0.5f, size.height * 0.82f)
            quadraticBezierTo(size.width * 0.28f, size.height * 0.82f, size.width * 0.08f, size.height * 0.5f)
            close()
        }
        drawPath(eye, color = SignUpSecondary, style = Stroke(stroke))
        drawCircle(color = SignUpSecondary, radius = size.minDimension * 0.1f, center = center)
        if (!visible) {
            drawLine(
                color = SignUpSecondary,
                start = Offset(size.width * 0.18f, size.height * 0.18f),
                end = Offset(size.width * 0.82f, size.height * 0.82f),
                strokeWidth = stroke,
            )
        }
    }
}

@Composable
private fun PointIqSignUpArrowIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = null,
        tint = SignUpText,
        modifier = modifier,
    )
}
