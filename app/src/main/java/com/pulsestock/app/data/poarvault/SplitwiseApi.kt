package com.pulsestock.app.data.poarvault

import com.pulsestock.app.PulseLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request

class SplitwiseApi {
    private val client = OkHttpClient.Builder()
        .certificatePinner(PINNER)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private fun get(path: String, token: String): Request = Request.Builder()
        .url("https://secure.splitwise.com/api/v3.0$path")
        .addHeader("Authorization", "Bearer $token")
        .get()
        .build()

    suspend fun getCurrentUser(token: String): SplitwiseCurrentUserResponse =
        call(get("/get_current_user", token))

    suspend fun getExpenses(
        token: String,
        offset: Int = 0,
        limit: Int = 20,
        updatedAfter: String? = null,
    ): SplitwiseExpensesResponse {
        val params = buildString {
            append("/get_expenses?limit=$limit&offset=$offset")
            if (updatedAfter != null) append("&updated_after=${updatedAfter}")
        }
        return call(get(params, token))
    }

    private suspend inline fun <reified T> call(request: Request): T = withContext(Dispatchers.IO) {
        val path = request.url.encodedPath
        PulseLog.d("SplitwiseApi", "→ ${request.method} $path")
        val resp = client.newCall(request).execute()
        PulseLog.d("SplitwiseApi", "← $path ${resp.code}")
        val body = resp.body?.string() ?: error("Empty Splitwise response from $path")
        if (!resp.isSuccessful) {
            PulseLog.e("SplitwiseApi", "← $path ${resp.code} error body: $body")
            error("Splitwise API ${resp.code}: $body")
        }
        json.decodeFromString<T>(body)
    }

    companion object {
        // Pins: Google Trust Services WE1 (intermediate) + GTS Root R4 (root).
        // Pinning intermediates and roots is more stable than pinning the leaf cert.
        // Hashes verified: 2025-05-10 via openssl s_client against secure.splitwise.com.
        private const val GTS_WE1_INTERMEDIATE = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
        private const val GTS_ROOT_R4          = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="

        private val PINNER = CertificatePinner.Builder()
            .add("secure.splitwise.com", GTS_WE1_INTERMEDIATE, GTS_ROOT_R4)
            .build()
    }
}
