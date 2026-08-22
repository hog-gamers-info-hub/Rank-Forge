package com.hoggamers.rankforge.presentation.auth

enum class PasswordRecoveryStage {
    NONE,
    REQUEST_EMAIL,
    EMAIL_SENT,
    VERIFYING_LINK,
    SET_NEW_PASSWORD,
    LINK_ERROR,
}
