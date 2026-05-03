package com.pulsestock.app.data.poarvault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

class SplitwiseApi {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun get(path: String, token: String): Request = Request.Builder()
        .url("https://secure.splitwise.com/api/v3.0$path")
        .addHeader("Authorization", "Bearer $token")
        .get()
        .build()

    suspend fun getCurrentUser(token: String): SplitwiseCurrentUserResponse =
        call(get("/get_current_user", token))

    suspend fun getExpenses(token: String, offset: Int = 0, limit: Int = 20): SplitwiseExpensesResponse =
        call(get("/get_expenses?limit=$limit&offset=$offset", token))

    private suspend inline fun <reified T> call(request: Request): T = withContext(Dispatchers.IO) {
        val resp = client.newCall(request).execute()
        val body = resp.body?.string() ?: error("Empty Splitwise response from ${request.url}")
        if (!resp.isSuccessful) error("Splitwise API ${resp.code}: $body")
        json.decodeFromString<T>(body)
    }
}
