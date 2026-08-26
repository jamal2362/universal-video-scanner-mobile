package com.jamal2367.uvsmobile.util

import android.content.Context
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.data.remote.ApiFailure

/**
 * What to put on screen for a failed call.
 *
 * The API answers every failure with a machine-readable `code`, so the handful
 * that a person can actually do something about get a sentence that says what
 * to do; anything else falls back to the server's own wording, which is written
 * for a human already.
 */
fun Throwable.toUserMessage(context: Context): String = when (this) {
    is ApiFailure.NotConfigured -> context.getString(R.string.error_not_configured)

    is ApiFailure.Unreachable -> {
        val where = serverLabel?.takeIf { it.isNotBlank() }
        when {
            // Named before the address is: without this permission nothing on
            // the local network answers, and every other wording would send a
            // reader off to check a server that is not the problem.
            !LocalNetworkAccess.isGranted(context) ->
                context.getString(R.string.error_local_network_denied)

            where == null -> context.getString(R.string.error_unreachable)
            timedOut -> context.getString(R.string.api_error_timeout, where)
            else -> context.getString(R.string.api_error_network, where)
        }
    }

    is ApiFailure.Api -> when (code) {
        "api_disabled" -> context.getString(R.string.api_error_disabled)
        "unauthorized" -> context.getString(R.string.api_error_unauthorized)
        "not_found" -> context.getString(R.string.api_error_not_found)
        "scan_running" -> context.getString(R.string.api_error_scan_running)
        "no_scan_running" -> context.getString(R.string.api_error_no_scan)
        "entry_not_found" -> context.getString(R.string.detail_not_found)
        else -> serverMessage ?: context.getString(R.string.error_title)
    }

    is ApiFailure.Malformed -> context.getString(R.string.api_error_parse)

    else -> message ?: context.getString(R.string.error_title)
}
