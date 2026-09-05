package com.hoggamers.rankforge.presentation.auth

enum class AccountDeletionUiState {
    IDLE,
    DELETING,
    REMOTE_DELETED_PENDING_LOCAL_CLEANUP,
    RECOVERY_REQUIRED,
}
