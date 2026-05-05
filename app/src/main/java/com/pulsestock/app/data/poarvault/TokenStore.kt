package com.pulsestock.app.data.poarvault

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

class TokenStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "poarvault_tokens",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getOrCreatePassphrase(): ByteArray {
        val stored = prefs.getString(KEY_PASSPHRASE, null)
        if (stored != null) return Base64.decode(stored, Base64.NO_WRAP)
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_PASSPHRASE, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        return bytes
    }

    fun putAccessToken(institutionId: String, token: String) =
        prefs.edit().putString("token_$institutionId", token).apply()

    fun getAccessToken(institutionId: String): String? =
        prefs.getString("token_$institutionId", null)

    fun removeAccessToken(institutionId: String) =
        prefs.edit().remove("token_$institutionId").apply()

    fun putSplitwiseToken(token: String) =
        prefs.edit().putString(KEY_SPLITWISE_TOKEN, token).apply()

    fun getSplitwiseToken(): String? =
        prefs.getString(KEY_SPLITWISE_TOKEN, null)

    fun removeSplitwiseToken() =
        prefs.edit().remove(KEY_SPLITWISE_TOKEN).apply()

    fun putSplitwiseUserId(id: Long) =
        prefs.edit().putString(KEY_SPLITWISE_USER_ID, id.toString()).apply()

    fun getSplitwiseUserId(): Long =
        prefs.getString(KEY_SPLITWISE_USER_ID, null)?.toLongOrNull() ?: -1L

    fun getLastSplitwiseSyncAt(): String? =
        prefs.getString(KEY_SPLITWISE_LAST_SYNC, null)

    fun putLastSplitwiseSyncAt(isoTimestamp: String) =
        prefs.edit().putString(KEY_SPLITWISE_LAST_SYNC, isoTimestamp).apply()

    fun removeLastSplitwiseSyncAt() =
        prefs.edit().remove(KEY_SPLITWISE_LAST_SYNC).apply()

    // Tracks the furthest page offset fetched during historical loading,
    // regardless of how many expenses passed the paidShare filter on that page.
    fun getMaxFetchedOffset(): Int =
        prefs.getString(KEY_SPLITWISE_MAX_OFFSET, null)?.toIntOrNull() ?: -20

    fun putMaxFetchedOffset(offset: Int) =
        prefs.edit().putString(KEY_SPLITWISE_MAX_OFFSET, offset.toString()).apply()

    fun removeMaxFetchedOffset() =
        prefs.edit().remove(KEY_SPLITWISE_MAX_OFFSET).apply()

    companion object {
        private const val KEY_PASSPHRASE = "db_passphrase"
        private const val KEY_SPLITWISE_TOKEN = "splitwise_token"
        private const val KEY_SPLITWISE_USER_ID = "splitwise_user_id"
        private const val KEY_SPLITWISE_LAST_SYNC = "splitwise_last_sync"
        private const val KEY_SPLITWISE_MAX_OFFSET = "splitwise_max_offset"
    }
}
