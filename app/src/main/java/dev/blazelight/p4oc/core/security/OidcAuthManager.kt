package dev.blazelight.p4oc.core.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.blazelight.p4oc.core.log.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * OAuth2 / OIDC authentication via AppAuth (Authorization Code + PKCE).
 *
 * Lets P4OC obtain and auto-refresh a bearer access token from an identity provider (e.g. Keycloak),
 * so the OpenCode server can sit behind an authenticating reverse proxy. The full OAuth state
 * (access/refresh material + discovered endpoints) is serialized by AppAuth and stored encrypted in
 * [CredentialStore], keyed by server URL. Tokens are never kept in plaintext config.
 *
 * The OkHttp interceptor calls [freshAccessTokenBlocking] on a background thread; AppAuth refreshes
 * transparently when the access token is expired.
 */
class OidcAuthManager(
    context: Context,
    private val credentialStore: CredentialStore,
) {
    companion object {
        private const val TAG = "OidcAuthManager"
        private const val SCOPES = "openid profile offline_access"
        private const val REFRESH_TIMEOUT_SECONDS = 30L

        /** Redirect URI scheme is the app id; matches RedirectUriReceiverActivity in the manifest. */
        fun redirectUri(applicationId: String): String = "$applicationId:/oauth2redirect"
    }

    private val authService = AuthorizationService(context.applicationContext)

    /** Discover the IdP endpoints from its issuer URL (.well-known/openid-configuration). */
    private suspend fun discover(issuer: String): AuthorizationServiceConfiguration =
        suspendCancellableCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(issuer)) { config, ex ->
                if (config != null) {
                    cont.resume(config)
                } else {
                    cont.resumeWith(Result.failure(ex ?: IllegalStateException("OIDC discovery failed")))
                }
            }
        }

    /**
     * Build the Custom Tab intent that starts the login flow. The UI launches it with an
     * ActivityResultLauncher and passes the result back to [completeLogin].
     */
    suspend fun buildLoginIntent(issuer: String, clientId: String, redirectUri: String): Intent {
        val config = discover(issuer)
        val request = AuthorizationRequest.Builder(
            config,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(redirectUri),
        ).setScope(SCOPES).build()
        return authService.getAuthorizationRequestIntent(request)
    }

    /**
     * Complete login from the redirect intent: exchange the auth code for tokens and persist the
     * resulting AuthState for [serverUrl].
     */
    suspend fun completeLogin(serverUrl: String, data: Intent): Result<Unit> {
        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (response == null) {
            return Result.failure(ex ?: IllegalStateException("No authorization response"))
        }
        val authState = AuthState(response, ex)
        val (tokenResponse, tokenEx) = performTokenRequest(response.createTokenExchangeRequest())
        authState.update(tokenResponse, tokenEx)
        if (tokenResponse == null) {
            return Result.failure(tokenEx ?: IllegalStateException("Token exchange failed"))
        }
        persist(serverUrl, authState)
        AppLog.d(TAG, "OIDC login complete for $serverUrl")
        return Result.success(Unit)
    }

    private suspend fun performTokenRequest(
        request: net.openid.appauth.TokenRequest,
    ): Pair<TokenResponse?, AuthorizationException?> =
        suspendCancellableCoroutine { cont ->
            authService.performTokenRequest(request) { resp, ex -> cont.resume(resp to ex) }
        }

    /**
     * Return a valid access token for [serverUrl], refreshing it if expired. Blocks the calling
     * (background) thread — intended for use inside an OkHttp interceptor. Returns null if there is
     * no stored auth or the refresh fails.
     */
    fun freshAccessTokenBlocking(serverUrl: String): String? {
        val serialized = credentialStore.getServerOidc(serverUrl) ?: credentialStore.getActiveOidc() ?: return null
        val authState = runCatching { AuthState.jsonDeserialize(serialized) }.getOrNull() ?: return null

        val latch = CountDownLatch(1)
        val tokenRef = AtomicReference<String?>(null)
        authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
            if (ex == null) tokenRef.set(accessToken) else AppLog.w(TAG, "Token refresh failed: ${ex.message}")
            persist(serverUrl, authState) // persist refreshed material
            latch.countDown()
        }
        if (!latch.await(REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            AppLog.w(TAG, "Token refresh timed out for $serverUrl")
            return null
        }
        return tokenRef.get()
    }

    /** True if we have a stored OIDC session for [serverUrl] (active or per-server). */
    fun hasSession(serverUrl: String): Boolean =
        credentialStore.getServerOidc(serverUrl) != null || credentialStore.getActiveOidc() != null

    fun logout(serverUrl: String) {
        credentialStore.removeServerOidc(serverUrl)
        credentialStore.clearActiveOidc()
    }

    private fun persist(serverUrl: String, authState: AuthState) {
        val serialized = authState.jsonSerializeString()
        credentialStore.setServerOidc(serverUrl, serialized)
        credentialStore.setActiveOidc(serialized)
    }

    fun dispose() = authService.dispose()
}
