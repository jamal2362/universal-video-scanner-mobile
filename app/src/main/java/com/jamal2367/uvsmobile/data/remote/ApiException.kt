package com.jamal2367.uvsmobile.data.remote

import java.io.IOException

/**
 * What went wrong with a call, in the shape the screens can act on.
 *
 * The API answers every failure - including the ones Flask itself produces -
 * as `{success, error, code}`, so a screen can react to the machine-readable
 * `code` and still have something to print when it does not know it.
 */
sealed class ApiFailure : Exception() {

    /** No server is filled in far enough to be worth a request. */
    data object NotConfigured : ApiFailure() {
        private fun readResolve(): Any = NotConfigured
        override val message: String get() = "No server configured"
    }

    /** None of the configured servers answered. */
    data class Unreachable(
        val serverLabel: String?,
        val timedOut: Boolean,
        override val cause: IOException?,
    ) : ApiFailure() {
        override val message: String
            get() = cause?.message ?: "Server unreachable"
    }

    /** The server answered, and said no. */
    data class Api(
        val status: Int,
        val code: String?,
        val serverMessage: String?,
    ) : ApiFailure() {
        override val message: String
            get() = serverMessage ?: "HTTP $status"
    }

    /** The answer arrived but could not be read as this API's JSON. */
    data class Malformed(override val cause: Throwable?) : ApiFailure() {
        override val message: String
            get() = cause?.message ?: "Malformed response"
    }
}

/** Thrown by the interceptor when there is nothing to send the request to. */
class NoServerConfiguredException : IOException("No server configured")
