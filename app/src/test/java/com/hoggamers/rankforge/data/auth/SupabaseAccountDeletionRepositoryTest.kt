package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AccountDeletionFailureCategory
import com.hoggamers.rankforge.domain.auth.AccountDeletionResult
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAccountDeletionRepositoryTest {
    @Test
    fun sendsExactlyOneAuthenticatedEmptyDeletionRequest() = runTest {
        val transport = RecordingTransport(
            AccountDeletionHttpResponse(200, "{\"ok\":true}"),
        )
        val repository = repository(transport = transport)

        assertEquals(AccountDeletionResult.Success, repository.deleteCurrentAccount())
        assertEquals(1, transport.requests.size)
        val request = transport.requests.single()
        assertEquals(
            "https://project.supabase.co/functions/v1/delete-account",
            request.url,
        )
        assertEquals("Bearer access-token", request.headers["Authorization"])
        assertEquals("publishable-key", request.headers["apikey"])
        assertEquals("{}", request.body)
    }

    @Test
    fun missingSessionDoesNotMakeARequest() = runTest {
        val transport = RecordingTransport(AccountDeletionHttpResponse(200, "{\"ok\":true}"))
        val repository = repository(token = null, transport = transport)

        assertEquals(
            AccountDeletionResult.Failure(AccountDeletionFailureCategory.NO_SESSION),
            repository.deleteCurrentAccount(),
        )
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun mapsAuthenticationAndMalformedSuccessResponsesSafely() = runTest {
        val authenticationRepository = repository(
            transport = RecordingTransport(AccountDeletionHttpResponse(401, "{}")),
        )
        assertEquals(
            AccountDeletionResult.Failure(AccountDeletionFailureCategory.AUTHENTICATION),
            authenticationRepository.deleteCurrentAccount(),
        )

        val malformedRepository = repository(
            transport = RecordingTransport(AccountDeletionHttpResponse(200, "{}")),
        )
        assertEquals(
            AccountDeletionResult.Failure(AccountDeletionFailureCategory.UNKNOWN),
            malformedRepository.deleteCurrentAccount(),
        )
    }

    @Test
    fun rethrowsCancellationWithoutMappingItToFailure() = runTest {
        val repository = repository(
            transport = RecordingTransport(
                response = AccountDeletionHttpResponse(200, "{\"ok\":true}"),
                failure = CancellationException("cancelled"),
            ),
        )

        try {
            repository.deleteCurrentAccount()
            fail("Expected cancellation to propagate")
        } catch (_: CancellationException) {
            // Expected.
        }
    }

    private fun repository(
        token: String? = "access-token",
        transport: RecordingTransport,
    ) = SupabaseAccountDeletionRepository(
        config = SupabaseAuthConfig(
            supabaseUrl = "https://project.supabase.co",
            publishableKey = "publishable-key",
        ),
        accessTokenProvider = AccountDeletionAccessTokenProvider { token },
        transport = transport,
    )

    private class RecordingTransport(
        private val response: AccountDeletionHttpResponse,
        private val failure: Throwable? = null,
    ) : AccountDeletionHttpTransport {
        val requests = mutableListOf<AccountDeletionHttpRequest>()

        override suspend fun post(request: AccountDeletionHttpRequest): AccountDeletionHttpResponse {
            requests += request
            failure?.let { throw it }
            return response
        }
    }
}
