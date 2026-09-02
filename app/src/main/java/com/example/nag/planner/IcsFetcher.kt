package com.example.nag.planner

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches raw ICS text over HTTP(S). Kept separate from [IcsParser] so the part with
 * real logic to verify — parsing — stays plain Kotlin and JVM-testable; this is a thin,
 * untested wrapper around a blocking network call.
 */
object IcsFetcher {

    private const val TIMEOUT_MS = 15_000

    /** Blocking fetch — call this off the main thread. Throws on any network/HTTP failure. */
    fun fetch(feedUrl: String): String {
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.requestMethod = "GET"
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("ICS feed returned HTTP $code")
            }
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
