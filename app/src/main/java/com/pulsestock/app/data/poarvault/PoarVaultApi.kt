package com.pulsestock.app.data.poarvault

import com.pulsestock.app.BuildConfig
import com.pulsestock.app.PulseLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PoarVaultApi {

    private val client = OkHttpClient.Builder()
        .certificatePinner(buildPinner())
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    private fun post(path: String, body: String): Request = Request.Builder()
        .url("${BuildConfig.POARVAULT_API_URL}$path")
        .addHeader("x-api-key", BuildConfig.POARVAULT_API_KEY)
        .post(body.toRequestBody(mediaType))
        .build()

    suspend fun getLinkToken(userId: String): LinkTokenResponse =
        call(post("/link-token", """{"user_id":"$userId"}"""))

    suspend fun exchangeToken(publicToken: String): ExchangeResponse =
        call(post("/exchange-token", """{"public_token":"$publicToken"}"""))

    suspend fun getBalances(accessToken: String): BalancesResponse =
        call(post("/balances", """{"access_token":"$accessToken"}"""))

    suspend fun disconnect(accessToken: String) = withContext(Dispatchers.IO) {
        client.newCall(post("/disconnect", """{"access_token":"$accessToken"}""")).execute().close()
    }

    suspend fun getLiabilities(accessToken: String): PlaidLiabilitiesResponse =
        call(post("/liabilities", """{"access_token":"$accessToken"}"""))

    suspend fun getTransactions(accessToken: String, startDate: String, endDate: String): PlaidTransactionsResponse =
        call(post("/transactions", """{"access_token":"$accessToken","start_date":"$startDate","end_date":"$endDate"}"""))

    suspend fun exchangeSplitwiseCode(code: String): SplitwiseAuthResponse =
        call(post("/splitwise-auth", """{"code":"$code"}"""))

    private suspend inline fun <reified T> call(request: Request): T = withContext(Dispatchers.IO) {
        val path = request.url.encodedPath
        PulseLog.d("PoarVaultApi", "→ POST $path")
        val resp = client.newCall(request).execute()
        PulseLog.d("PoarVaultApi", "← $path ${resp.code}")
        val body = resp.body?.string() ?: error("Empty response from $path")
        if (!resp.isSuccessful) {
            PulseLog.e("PoarVaultApi", "← $path ${resp.code} error body: $body")
            error("API ${resp.code}: $body")
        }
        json.decodeFromString<T>(body)
    }

    companion object {
        // Extract hostname from the API URL so the pin pattern matches whatever Gateway
        // endpoint is configured — regardless of API ID or region.
        private val apiHost: String by lazy {
            runCatching {
                java.net.URL(BuildConfig.POARVAULT_API_URL).host
            }.getOrDefault("*.execute-api.us-east-2.amazonaws.com")
        }

        // Pins: Amazon RSA 2048 M01 (intermediate) + Amazon Root CA 1 (root).
        // Pinning intermediates and roots is more stable than pinning the leaf — AWS rotates
        // leaf certs for API Gateway roughly every 13 months and without advance notice.
        // If Amazon issues a new intermediate CA, update AMAZON_INTERMEDIATE below.
        // Hashes verified: 2025-05-10 via openssl s_client against the production endpoint.
        private const val AMAZON_INTERMEDIATE = "sha256/DxH4tt40L+eduF6szpY6TONlxhZhBd+pJ9wbHlQ2fuw="
        private const val AMAZON_ROOT_CA1     = "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="

        fun buildPinner(): CertificatePinner = CertificatePinner.Builder()
            .add(apiHost, AMAZON_INTERMEDIATE, AMAZON_ROOT_CA1)
            .build()
    }
}
