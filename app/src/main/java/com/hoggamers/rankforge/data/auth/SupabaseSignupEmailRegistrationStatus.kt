package com.hoggamers.rankforge.data.auth

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SignupEmailRegistrationStatusHttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class SignupEmailRegistrationStatusHttpResponse(
    val statusCode: Int,
    val body: String,
)

fun interface SignupEmailRegistrationStatusHttpTransport {
    suspend fun post(
        request: SignupEmailRegistrationStatusHttpRequest,
    ): SignupEmailRegistrationStatusHttpResponse
}

@Singleton
class UrlConnectionSignupEmailRegistrationStatusHttpTransport @Inject constructor() :
    SignupEmailRegistrationStatusHttpTransport {
    override suspend fun post(
        request: SignupEmailRegistrationStatusHttpRequest,
    ): SignupEmailRegistrationStatusHttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            doOutput = true
            request.headers.forEach { (name, value) ->
                setRequestProperty(name, value)
            }
        }

        try {
            connection.outputStream.use { output ->
                output.write(request.body.toByteArray(StandardCharsets.UTF_8))
            }
            val responseStream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            SignupEmailRegistrationStatusHttpResponse(
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

@Serializable
private data class SignupEmailRegistrationStatusResponse(
    val registered: Boolean,
)

@Singleton
class SupabaseSignupEmailRegistrationStatusClient @Inject constructor(
    private val config: SupabaseAuthConfig,
    private val transport: SignupEmailRegistrationStatusHttpTransport,
) {
    suspend fun isConfirmedEmailRegistered(email: String): Boolean {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val response = try {
            transport.post(
                SignupEmailRegistrationStatusHttpRequest(
                    url = "${config.supabaseUrl.trimEnd('/')}/functions/v1/signup-email-registration-status",
                    headers = mapOf(
                        "Accept" to "application/json",
                        "apikey" to config.publishableKey,
                        "Content-Type" to "application/json",
                    ),
                    body = buildJsonObject {
                        put("email", normalizedEmail)
                    }.toString(),
                ),
            )
        } catch (timeout: SocketTimeoutException) {
            throw timeout
        } catch (io: IOException) {
            throw io
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            throw IOException("Signup registration status could not be checked.", throwable)
        }

        if (response.statusCode !in 200..299) {
            throw IOException("Signup registration status could not be checked.")
        }

        return try {
            Json.decodeFromString<SignupEmailRegistrationStatusResponse>(response.body).registered
        } catch (throwable: Throwable) {
            throw IOException("Signup registration status could not be checked.", throwable)
        }
    }
}
