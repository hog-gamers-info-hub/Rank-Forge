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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

private val LoginNavy = Color(0xFF071B3E)
private val LoginBody = Color(0xFF40536F)
private val LoginMuted = Color(0xFF7A8BA4)
private val LoginBlue = Color(0xFF176AF7)
private val LoginBorder = Color(0xFFD9E4F2)
private val LoginButtonStart = Color(0xFF082A63)
private val LoginButtonMiddle = Color(0xFF0A4AA6)
private val LoginButtonEnd = Color(0xFF0C6CD9)
private val LoginButtonDisabledStart = Color(0xFF18365F)
private val LoginButtonDisabledMiddle = Color(0xFF1B4F87)
private val LoginButtonDisabledEnd = Color(0xFF2B6FA5)

@Composable
internal fun PointIqLoginScreen(
    uiState: AuthUiState,
    onModeSelected: (AuthMode) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onBeginPasswordRecovery: () -> Unit,
    modifier: Modifier = Modifier,
    messages: @Composable () -> Unit = {},
) {
    PointIqAuthShell(
        title = stringResource(R.string.auth_login_heading),
        subtitle = stringResource(R.string.pointiq_login_subtitle),
        modifier = modifier,
    ) {
        PointIqFieldLabel(text = stringResource(R.string.auth_email_label))
        Spacer(modifier = Modifier.height(6.dp))
        PointIqEmailField(
            value = uiState.email,
            onValueChange = onEmailChanged,
            enabled = !uiState.isSubmitting,
        )

        Spacer(modifier = Modifier.height(14.dp))

        PointIqFieldLabel(text = stringResource(R.string.auth_password_label))
        Spacer(modifier = Modifier.height(6.dp))
        PointIqPasswordField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            enabled = !uiState.isSubmitting,
        )

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            TextButton(
                onClick = onBeginPasswordRecovery,
                enabled = !uiState.isSubmitting,
                modifier = Modifier.testTag(AUTH_FORGOT_PASSWORD_ACTION_TEST_TAG),
            ) {
                Text(
                    text = stringResource(R.string.auth_forgot_password_action),
                    color = LoginBlue,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        PointIqPrimaryButton(
            text = stringResource(
                if (uiState.isSubmitting) R.string.auth_submitting_action
                else R.string.auth_log_in_action,
            ),
            enabled = uiState.canSubmit,
            onClick = onSubmit,
        )

        Spacer(modifier = Modifier.height(18.dp))
        PointIqDivider()
        Spacer(modifier = Modifier.height(18.dp))

        PointIqGoogleButton(
            enabled = !uiState.isSubmitting && !uiState.isSessionLoading,
            onClick = onGoogleSignIn,
        )

        PointIqSignUpPrompt(
            onSignUp = { onModeSelected(AuthMode.SignUp) },
        )

        messages()
    }
}

@Composable
private fun PointIqFieldLabel(text: String) {
    Text(
        text = text,
        color = LoginNavy,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun PointIqEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        placeholder = {
            Text(
                text = stringResource(R.string.pointiq_email_placeholder),
                color = LoginMuted,
            )
        },
        leadingIcon = { PointIqMailIcon() },
        shape = RoundedCornerShape(16.dp),
        colors = pointIqFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_EMAIL_FIELD_TEST_TAG),
    )
}

@Composable
private fun PointIqPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        placeholder = {
            Text(
                text = stringResource(R.string.pointiq_password_placeholder),
                color = LoginMuted,
            )
        },
        leadingIcon = { PointIqLockIcon() },
        trailingIcon = {
            IconButton(
                onClick = { passwordVisible = !passwordVisible },
                enabled = enabled,
                modifier = Modifier.testTag(AUTH_PASSWORD_VISIBILITY_TEST_TAG),
            ) {
                PointIqEyeIcon(visible = passwordVisible)
            }
        },
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        shape = RoundedCornerShape(16.dp),
        colors = pointIqFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AUTH_PASSWORD_FIELD_TEST_TAG),
    )
}

@Composable
private fun pointIqFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LoginNavy,
    unfocusedTextColor = LoginNavy,
    disabledTextColor = LoginMuted,
    focusedBorderColor = LoginBlue,
    unfocusedBorderColor = LoginBorder,
    disabledBorderColor = LoginBorder,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    disabledContainerColor = Color.White,
    cursorColor = LoginBlue,
)

@Composable
private fun PointIqPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val gradient = if (enabled) {
        Brush.horizontalGradient(
            listOf(LoginButtonStart, LoginButtonMiddle, LoginButtonEnd),
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                LoginButtonDisabledStart,
                LoginButtonDisabledMiddle,
                LoginButtonDisabledEnd,
            ),
        )
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.92f),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = 9.dp,
                shape = shape,
                ambientColor = LoginBlue.copy(alpha = 0.16f),
                spotColor = LoginBlue.copy(alpha = 0.24f),
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
            PointIqArrowIcon(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(22.dp),
            )
        }
    }
}

@Composable
private fun PointIqDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = LoginBorder,
        )
        Text(
            text = stringResource(R.string.auth_or_divider),
            color = LoginMuted,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = LoginBorder,
        )
    }
}

@Composable
private fun PointIqGoogleButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, LoginBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = LoginNavy,
            disabledContainerColor = Color.White,
            disabledContentColor = LoginMuted,
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
private fun PointIqSignUpPrompt(
    onSignUp: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.pointiq_no_account_prompt),
            color = LoginBody,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onSignUp) {
            Text(
                text = stringResource(R.string.auth_signup_mode),
                color = LoginBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PointIqMailIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.7.dp.toPx()
        val left = size.width * 0.12f
        val right = size.width * 0.88f
        val top = size.height * 0.24f
        val bottom = size.height * 0.76f
        val color = LoginMuted

        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawLine(
            color,
            Offset(left + stroke, top + stroke),
            Offset(size.width / 2f, size.height * 0.52f),
            stroke,
        )
        drawLine(
            color,
            Offset(right - stroke, top + stroke),
            Offset(size.width / 2f, size.height * 0.52f),
            stroke,
        )
    }
}

@Composable
private fun PointIqLockIcon() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.7.dp.toPx()
        val color = LoginMuted
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.2f, size.height * 0.43f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.6f, size.height * 0.43f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(stroke),
        )
        drawArc(
            color = color,
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
private fun PointIqEyeIcon(visible: Boolean) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.6.dp.toPx()
        val color = LoginMuted
        val eye = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.5f)
            quadraticBezierTo(
                size.width * 0.28f,
                size.height * 0.18f,
                size.width * 0.5f,
                size.height * 0.18f,
            )
            quadraticBezierTo(
                size.width * 0.72f,
                size.height * 0.18f,
                size.width * 0.92f,
                size.height * 0.5f,
            )
            quadraticBezierTo(
                size.width * 0.72f,
                size.height * 0.82f,
                size.width * 0.5f,
                size.height * 0.82f,
            )
            quadraticBezierTo(
                size.width * 0.28f,
                size.height * 0.82f,
                size.width * 0.08f,
                size.height * 0.5f,
            )
            close()
        }
        drawPath(eye, color = color, style = Stroke(stroke))
        drawCircle(color = color, radius = size.minDimension * 0.1f, center = center)
        if (!visible) {
            drawLine(
                color = color,
                start = Offset(size.width * 0.18f, size.height * 0.18f),
                end = Offset(size.width * 0.82f, size.height * 0.82f),
                strokeWidth = stroke,
            )
        }
    }
}

@Composable
private fun PointIqArrowIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 2.dp.toPx()
        val color = Color.White
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, size.height * 0.5f),
            end = Offset(size.width * 0.76f, size.height * 0.5f),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.56f, size.height * 0.3f),
            end = Offset(size.width * 0.76f, size.height * 0.5f),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.56f, size.height * 0.7f),
            end = Offset(size.width * 0.76f, size.height * 0.5f),
            strokeWidth = stroke,
        )
    }
}
