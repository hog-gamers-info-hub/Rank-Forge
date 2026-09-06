package com.hoggamers.rankforge.data.auth

import io.github.jan.supabase.auth.auth
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import com.hoggamers.rankforge.domain.auth.AccountDeletionFailureCategory
import com.hoggamers.rankforge.domain.auth.AccountDeletionRemoteAccountStatus
import com.hoggamers.rankforge.domain.auth.AccountDeletionRequestDisposition
import com.hoggamers.rankforge.domain.auth.AccountDeletionRepository
import com.hoggamers.rankforge.domain.auth.AccountDeletionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun interface AccountDeletionAccessTokenProvider {
    suspend fun currentAccessToken(): String?
}

@Singleton
class SupabaseAccountDeletionAccessTokenProvider @Inject constructor(
    private val clientProvider: SupabaseClientProvider,
) : AccountDeletionAccessTokenProvider {
    override suspend fun currentAccessToken(): String? {
        clientProvider.client.auth.currentSessionOrNull()?.accessToken?.let { token ->
            return token
        }

        return try {
            clientProvider.client.auth.sessionManager.loadSessionOrNull()?.accessToken
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
    }
}

data class AccountDeletionHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class AccountDeletionHttpResponse(
    val statusCode: Int,
    val body: String,
)

fun interface AccountDeletionHttpTransport {
    suspend fun post(request: AccountDeletionHttpRequest): AccountDeletionHttpResponse

    suspend fun get(request: AccountDeletionHttpRequest): AccountDeletionHttpResponse =
        throw UnsupportedOperationException("GET is not supported")
}

@Singleton
class UrlConnectionAccountDeletionHttpTransport @Inject constructor() :
    AccountDeletionHttpTransport {
    override suspend fun post(request: AccountDeletionHttpRequest): AccountDeletionHttpResponse =
        execute(request, "POST")

    override suspend fun get(request: AccountDeletionHttpRequest): AccountDeletionHttpResponse =
        execute(request, "GET")

    private suspend fun execute(
        request: AccountDeletionHttpRequest,
        method: String,
    ): AccountDeletionHttpResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = REQUEST_TIMEOUT_MS
                readTimeout = REQUEST_TIMEOUT_MS
                doOutput = method == "POST"
                request.headers.forEach { (name, value) ->
                    setRequestProperty(name, value)
                }
            }

            try {
                if (method == "POST") {
                    connection.outputStream.use { output ->
                        output.write(request.body.toByteArray(StandardCharsets.UTF_8))
                    }
                }
                val responseStream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                AccountDeletionHttpResponse(
                    statusCode = connection.responseCode,
                    body = responseStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
                        .orEmpty(),
                )
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000
    }
}

@Singleton
class SupabaseAccountDeletionRepository @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val accessTokenProvider: AccountDeletionAccessTokenProvider,
    private val transport: AccountDeletionHttpTransport,
) : AccountDeletionRepository {
    override suspend fun deleteCurrentAccount(): AccountDeletionResult {
        val accessToken = accessTokenProvider.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: return AccountDeletionResult.Failure(AccountDeletionFailureCategory.NO_SESSION)

        if (!config.isConfigured) {
            return AccountDeletionResult.Failure(AccountDeletionFailureCategory.SERVER)
        }

        return try {
            val response = transport.post(
                AccountDeletionHttpRequest(
                    url = "${config.supabaseUrl.trimEnd('/')}/functions/v1/delete-account",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "apikey" to config.publishableKey,
                        "Content-Type" to "application/json",
                        "Accept" to "application/json",
                    ),
                    body = "{}",
                ),
            )
            response.toDeletionResult()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SocketTimeoutException) {
            AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.NETWORK,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            )
        } catch (_: IOException) {
            AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.NETWORK,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            )
        } catch (_: Throwable) {
            AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.UNKNOWN,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            )
        }
    }

    override suspend fun probeCurrentAccount(): AccountDeletionRemoteAccountStatus {
        val accessToken = accessTokenProvider.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: return AccountDeletionRemoteAccountStatus.UNKNOWN

        if (!config.isConfigured) {
            return AccountDeletionRemoteAccountStatus.UNKNOWN
        }

        return try {
            val response = transport.get(
                AccountDeletionHttpRequest(
                    url = "${config.supabaseUrl.trimEnd('/')}/auth/v1/user",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "apikey" to config.publishableKey,
                        "Accept" to "application/json",
                    ),
                    body = "",
                ),
            )
            if (response.statusCode == HttpURLConnection.HTTP_OK) {
                AccountDeletionRemoteAccountStatus.PRESENT.also { classification ->
                }
            } else {
                val code = runCatching {
                    Json.parseToJsonElement(response.body)
                        .jsonObject["code"]
                        ?.jsonPrimitive
                        ?.content
                }.getOrNull()
                val classification = if (code == "user_not_found") {
                    AccountDeletionRemoteAccountStatus.DELETED
                } else {
                    AccountDeletionRemoteAccountStatus.UNKNOWN
                }
                classification
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            AccountDeletionRemoteAccountStatus.UNKNOWN
        }
    }

    private fun AccountDeletionHttpResponse.toDeletionResult(): AccountDeletionResult {
        if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
            return AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.AUTHENTICATION,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            )
        }
        if (statusCode !in 200..299) {
            return AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.SERVER,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            )
        }

        return try {
            val ok = Json.parseToJsonElement(body).jsonObject["ok"]?.jsonPrimitive?.content == "true"
            if (ok) {
                AccountDeletionResult.Success
            } else {
                AccountDeletionResult.Failure(
                    AccountDeletionFailureCategory.UNKNOWN,
                    AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
                )
            }
        } catch (_: Throwable) {
            AccountDeletionResult.Failure(
                AccountDeletionFailureCategory.UNKNOWN,
                AccountDeletionRequestDisposition.MAY_HAVE_REACHED_SERVER,
            )
        }
    }

}
