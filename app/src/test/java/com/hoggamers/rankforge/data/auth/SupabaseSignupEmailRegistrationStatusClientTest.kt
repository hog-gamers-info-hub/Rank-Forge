package com.hoggamers.rankforge.data.auth

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SupabaseSignupEmailRegistrationStatusClientTest {
    @Test
    fun registeredResponseUsesMinimalNormalizedRequest() = runTest {
        var capturedRequest: SignupEmailRegistrationStatusHttpRequest? = null
        val client = client { request ->
            capturedRequest = request
            SignupEmailRegistrationStatusHttpResponse(200, "{\"registered\":true}")
        }

        assertTrue(client.isConfirmedEmailRegistered("  User@Example.COM  "))
        assertEquals(
            "https://project.example/functions/v1/signup-email-registration-status",
            capturedRequest?.url,
        )
        assertEquals("{\"email\":\"user@example.com\"}", capturedRequest?.body)
        assertEquals("publishable-key", capturedRequest?.headers?.get("apikey"))
    }

    @Test
    fun unregisteredResponseReturnsFalse() = runTest {
        val client = client {
            SignupEmailRegistrationStatusHttpResponse(200, "{\"registered\":false}")
        }

        assertFalse(client.isConfirmedEmailRegistered("new@example.com"))
    }

    @Test
    fun invalidResponseFailsClosed() = runTest {
        val client = client {
            SignupEmailRegistrationStatusHttpResponse(200, "{\"registered\":\"unknown\"}")
        }

        try {
            client.isConfirmedEmailRegistered("user@example.com")
            fail("Expected invalid registration status response to fail")
        } catch (_: IOException) {
            // A failed preflight must prevent the caller from guessing and continuing.
        }
    }

    private fun client(
        response: suspend (
            SignupEmailRegistrationStatusHttpRequest,
        ) -> SignupEmailRegistrationStatusHttpResponse,
    ): SupabaseSignupEmailRegistrationStatusClient =
        SupabaseSignupEmailRegistrationStatusClient(
            config = SupabaseAuthConfig(
                supabaseUrl = "https://project.example",
                publishableKey = "publishable-key",
            ),
            transport = SignupEmailRegistrationStatusHttpTransport { request ->
                response(request)
            },
        )
}
