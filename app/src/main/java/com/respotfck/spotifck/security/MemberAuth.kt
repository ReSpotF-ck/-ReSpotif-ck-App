package com.respotfck.spotifck.security

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Member-code login shared with the website (respotfck.netlify.app/api/member). */
object MemberAuth {
    private const val PREFS = "respotifuck_member"
    private const val KEY_TOKEN = "token"
    private const val KEY_NAME = "name"
    private const val KEY_LAST_OK = "last_ok"
    private const val API = "https://respotfck.netlify.app/api/member"
    private const val OFFLINE_GRACE_MS = 7L * 24 * 60 * 60 * 1000

    sealed interface LoginResult {
        data object Ok : LoginResult
        data object Invalid : LoginResult
        data object NotSetUp : LoginResult
        data object Offline : LoginResult
    }

    private lateinit var prefs: SharedPreferences
    val signedIn = mutableStateOf(false)
    val memberName = mutableStateOf<String?>(null)

    fun install(app: Application) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        memberName.value = prefs.getString(KEY_NAME, null)
        val lastOk = prefs.getLong(KEY_LAST_OK, 0L)
        signedIn.value = prefs.getString(KEY_TOKEN, null) != null &&
            System.currentTimeMillis() - lastOk in 0..OFFLINE_GRACE_MS
    }

    /** Re-checks the saved token; a revoked code signs out, a network error keeps the offline grace. */
    suspend fun refresh() {
        val token = prefs.getString(KEY_TOKEN, null) ?: return
        val (code, body) = request("GET", null, token) ?: return
        when (code) {
            200 -> save(body)
            401 -> signOut()
        }
    }

    suspend fun login(memberCode: String): LoginResult {
        val payload = JSONObject().put("code", memberCode.trim()).toString()
        val (code, body) = request("POST", payload, null) ?: return LoginResult.Offline
        return when (code) {
            200 -> if (save(body)) LoginResult.Ok else LoginResult.Offline
            401 -> LoginResult.Invalid
            404, 503 -> LoginResult.NotSetUp
            else -> LoginResult.Offline
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
        memberName.value = null
        signedIn.value = false
    }

    private fun save(body: String?): Boolean {
        val json = runCatching { JSONObject(body ?: "") }.getOrNull() ?: return false
        if (!json.optBoolean("ok")) return false
        val token = json.optString("token").ifEmpty { return false }
        val name = json.optString("name")
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_NAME, name)
            .putLong(KEY_LAST_OK, System.currentTimeMillis())
            .apply()
        memberName.value = name
        signedIn.value = true
        return true
    }

    private suspend fun request(method: String, body: String?, token: String?): Pair<Int, String?>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(API).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = method
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.setRequestProperty("Accept", "application/json")
                    token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                    if (body != null) {
                        conn.doOutput = true
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.outputStream.use { it.write(body.toByteArray()) }
                    }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    code to stream?.bufferedReader()?.use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
        }
}
