package com.jamal2367.uvsmobile.data.remote

import com.jamal2367.uvsmobile.data.model.ApiErrorBody
import com.jamal2367.uvsmobile.data.model.ApiIndex
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** What the button in the settings found out about one address. */
sealed interface ConnectionTestResult {
    data class Reachable(val apiVersion: String) : ConnectionTestResult
    data class Refused(val failure: ApiFailure) : ConnectionTestResult
}

/**
 * Tries one address on its own.
 *
 * Deliberately not routed through the failover interceptor: the point of the
 * test is to find out whether *this* address works, and an answer that came
 * from the other one would be worse than useless.
 *
 * `GET /api/v1` is the endpoint for it - it needs the token, so it tells apart
 * "unreachable", "no token configured on the server", "wrong token" and "fine".
 */
class ConnectionTester(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun test(server: ServerConfig): ConnectionTestResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${server.baseUrl}/api/v1/")
            .apply {
                if (server.token.isNotBlank()) {
                    header(FailoverInterceptor.TOKEN_HEADER, server.token.trim())
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    val parsed = runCatching {
                        json.decodeFromString(ApiErrorBody.serializer(), body)
                    }.getOrNull()
                    return@use ConnectionTestResult.Refused(
                        ApiFailure.Api(response.code, parsed?.code, parsed?.error ?: response.message)
                    )
                }

                val index = runCatching {
                    json.decodeFromString(ApiIndex.serializer(), body)
                }.getOrNull()
                    ?: return@use ConnectionTestResult.Refused(ApiFailure.Malformed(null))

                ConnectionTestResult.Reachable(index.version.ifBlank { "v1" })
            }
        } catch (io: IOException) {
            ConnectionTestResult.Refused(
                ApiFailure.Unreachable(
                    serverLabel = server.label,
                    timedOut = io is java.net.SocketTimeoutException,
                    cause = io,
                )
            )
        }
    }
}
