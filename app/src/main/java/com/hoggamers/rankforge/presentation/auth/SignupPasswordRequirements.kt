package com.hoggamers.rankforge.presentation.auth

const val MIN_SIGNUP_PASSWORD_LENGTH = 10

data class SignupPasswordRequirements(
    val hasMinimumLength: Boolean,
    val hasUppercase: Boolean,
    val hasLowercase: Boolean,
    val hasDigit: Boolean,
) {
    val isValid: Boolean
        get() = hasMinimumLength && hasUppercase && hasLowercase && hasDigit
}

fun validateSignupPassword(password: String): SignupPasswordRequirements =
    SignupPasswordRequirements(
        hasMinimumLength = password.length >= MIN_SIGNUP_PASSWORD_LENGTH,
        hasUppercase = password.any { it in 'A'..'Z' },
        hasLowercase = password.any { it in 'a'..'z' },
        hasDigit = password.any { it in '0'..'9' },
    )
