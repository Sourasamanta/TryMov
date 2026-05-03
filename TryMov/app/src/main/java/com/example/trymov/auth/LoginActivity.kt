package com.example.trymov.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.trymov.MainActivity
import com.example.trymov.ui.theme.TryMovTheme
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

class LoginActivity : ComponentActivity() {

    private lateinit var authService: AuthorizationService
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(this)

        if (TokenManager.isLoggedIn()) {
            goToMain()
            return
        }

        setContent {
            TryMovTheme {
                LoginScreen(
                    isLoading = isLoading,
                    onLoginClick = ::startCognitoLogin
                )
            }
        }
    }

    private fun startCognitoLogin() {
        isLoading = true

        // Fetch endpoints from Cognito discovery document — avoids hardcoding URLs
        val issuerUri = Uri.parse(
            "https://cognito-idp.${CognitoConfig.REGION}.amazonaws.com/${CognitoConfig.USER_POOL_ID}"
        )

        AuthorizationServiceConfiguration.fetchFromIssuer(issuerUri) { config, ex ->
            if (ex != null || config == null) {
                Log.e(TAG, "Failed to fetch Cognito config: ${ex?.message}")
                runOnUiThread {
                    isLoading = false
                    Toast.makeText(this, "Cannot reach Cognito: ${ex?.message}", Toast.LENGTH_LONG).show()
                }
                return@fetchFromIssuer
            }

            val request = AuthorizationRequest.Builder(
                config,
                CognitoConfig.APP_CLIENT_ID,
                ResponseTypeValues.CODE,
                Uri.parse(CognitoConfig.REDIRECT_URI)
            )
                .setScope("openid email")
                .build()

            runOnUiThread {
                @Suppress("DEPRECATION")
                startActivityForResult(authService.getAuthorizationRequestIntent(request), RC_AUTH)
            }
        }
    }

    @Deprecated("Required for AppAuth callback handling")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != RC_AUTH || data == null) {
            isLoading = false
            return
        }

        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)

        if (error != null || response == null) {
            isLoading = false
            Log.e(TAG, "Auth error: ${error?.errorDescription ?: error?.message}")
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Login failed: ${error?.errorDescription ?: error?.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        authService.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenError ->
            runOnUiThread { isLoading = false }

            if (tokenError != null || tokenResponse == null) {
                Log.e(TAG, "Token exchange failed: ${tokenError?.errorDescription}")
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Token exchange failed: ${tokenError?.errorDescription}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@performTokenRequest
            }

            val idToken = tokenResponse.idToken
            val accessToken = tokenResponse.accessToken

            if (idToken == null || accessToken == null) {
                Log.e(TAG, "Missing tokens — idToken=$idToken accessToken=$accessToken")
                runOnUiThread {
                    Toast.makeText(this, "Missing tokens from Cognito", Toast.LENGTH_LONG).show()
                }
                return@performTokenRequest
            }else{
                Log.e("asdfghjkl", "idToken=$idToken accessToken=$accessToken")
            }

            TokenManager.saveTokens(idToken, accessToken, tokenResponse.refreshToken)
            goToMain()
        }
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        authService.dispose()
    }

    companion object {
        private const val TAG = "LoginActivity"
        private const val RC_AUTH = 1001
    }
}
