package com.pulsestock.app.data.poarvault

import com.pulsestock.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class PoarVaultApi {

    private val client = OkHttpClient()
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

    private suspend inline fun <reified T> call(request: Request): T = withContext(Dispatchers.IO) {
        val resp = client.newCall(request).execute()
        val body = resp.body?.string() ?: error("Empty response from ${request.url}")
        if (!resp.isSuccessful) error("API ${resp.code}: $body")
        json.decodeFromString<T>(body)
    }
}
