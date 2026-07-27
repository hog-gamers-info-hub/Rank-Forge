package com.hoggamers.rankforge.data.auth

class AuthConfigurationException : IllegalStateException(
    "Supabase URL and publishable key are not configured.",
)
