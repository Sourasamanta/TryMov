package com.example.trymov.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject

object TokenManager {

    private const val PREFS_NAME = "trymov_auth"
    private const val KEY_ID_TOKEN = "id_token"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Persist tokens received from Cognito after a successful auth code exchange.
     * The ID token is used as the Bearer token for our FastAPI backend.
     */
    fun saveTokens(idToken: String, accessToken: String, refreshToken: String?) {
        prefs.edit()
            .putString(KEY_ID_TOKEN, idToken)
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .apply()
        refreshToken?.let { prefs.edit().putString(KEY_REFRESH_TOKEN, it).apply() }
    }

    /** Returns the Cognito ID token used as the Authorization: Bearer header. */
    fun getToken(): String? = prefs.getString(KEY_ID_TOKEN, null)

    fun isLoggedIn(): Boolean = getToken() != null

    fun getUserId(): String? {
        val token = getToken() ?: return null
        return try {
            val payload = token.split(".").getOrNull(1) ?: return null
            val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING))
            JSONObject(decoded).getString("sub")
        } catch (_: Exception) { null }
    }

    fun clear() = prefs.edit().clear().apply()
}
