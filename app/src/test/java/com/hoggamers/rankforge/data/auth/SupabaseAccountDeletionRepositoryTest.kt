package com.hoggamers.rankforge.data.auth

import com.hoggamers.rankforge.domain.auth.AccountDeletionFailureCategory
import com.hoggamers.rankforge.domain.auth.AccountDeletionRemoteAccountStatus
import com.hoggamers.rankforge.domain.auth.AccountDeletionRequestDisposition
import com.hoggamers.rankforge.domain.auth.AccountDeletionResult
import java.io.IOException
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CancellationException
import java.net.SocketTimeoutException
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
            AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.AUTHENTICATION,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            ),
            authenticationRepository.deleteCurrentAccount(),
        )

        val malformedRepository = repository(
            transport = RecordingTransport(AccountDeletionHttpResponse(200, "{}")),
        )
        assertEquals(
            AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.UNKNOWN,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            ),
            malformedRepository.deleteCurrentAccount(),
        )
    }

    @Test
    fun postDispatchNetworkFailureIsMarkedAmbiguous() = runTest {
        val repository = repository(
            transport = RecordingTransport(
                response = AccountDeletionHttpResponse(500, "{}"),
                failure = IOException("connection lost"),
            ),
        )

        assertEquals(
            AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.NETWORK,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            ),
            repository.deleteCurrentAccount(),
        )
    }

    @Test
    fun postDispatchTimeoutFailureIsMarkedAmbiguous() = runTest {
        val repository = repository(
            transport = RecordingTransport(
                response = AccountDeletionHttpResponse(500, "{}"),
                failure = SocketTimeoutException("timed out"),
            ),
        )

        assertEquals(
            AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.NETWORK,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            ),
            repository.deleteCurrentAccount(),
        )
    }

    @Test
    fun probeUsesAuthenticatedUserEndpointAndExactNotFoundCode() = runTest {
        val transport = RecordingTransport(
            response = AccountDeletionHttpResponse(200, "{\"ok\":true}"),
            getResponse = AccountDeletionHttpResponse(
                404,
                "{\"code\":\"user_not_found\",\"message\":\"hidden\"}",
            ),
        )
        val repository = repository(transport = transport)

        assertEquals(
            AccountDeletionRemoteAccountStatus.DELETED,
            repository.probeCurrentAccount(),
        )
        val request = transport.getRequests.single()
        assertEquals("https://project.supabase.co/auth/v1/user", request.url)
        assertEquals("Bearer access-token", request.headers["Authorization"])
        assertEquals("publishable-key", request.headers["apikey"])
    }

    @Test
    fun probeOnlyTreatsExactUserNotFoundAsDeleted() = runTest {
        val cases = listOf(
            AccountDeletionHttpResponse(200, "{}") to AccountDeletionRemoteAccountStatus.PRESENT,
            AccountDeletionHttpResponse(401, "{\"code\":\"session_expired\"}") to AccountDeletionRemoteAccountStatus.UNKNOWN,
            AccountDeletionHttpResponse(403, "{\"code\":\"session_not_found\"}") to AccountDeletionRemoteAccountStatus.UNKNOWN,
            AccountDeletionHttpResponse(401, "{\"code\":\"bad_jwt\"}") to AccountDeletionRemoteAccountStatus.UNKNOWN,
            AccountDeletionHttpResponse(404, "not-json") to AccountDeletionRemoteAccountStatus.UNKNOWN,
        )

        cases.forEach { (response, expected) ->
            assertEquals(
                expected,
                repository(
                    transport = RecordingTransport(
                        response = AccountDeletionHttpResponse(200, "{\"ok\":true}"),
                        getResponse = response,
                    ),
                ).probeCurrentAccount(),
            )
        }
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
        private val getResponse: AccountDeletionHttpResponse = response,
        private val getFailure: Throwable? = null,
    ) : AccountDeletionHttpTransport {
        val requests = mutableListOf<AccountDeletionHttpRequest>()
        val getRequests = mutableListOf<AccountDeletionHttpRequest>()

        override suspend fun post(request: AccountDeletionHttpRequest): AccountDeletionHttpResponse {
            requests += request
            failure?.let { throw it }
            return response
        }

        override suspend fun get(request: AccountDeletionHttpRequest): AccountDeletionHttpResponse {
            getRequests += request
            getFailure?.let { throw it }
            return getResponse
        }
    }
}
