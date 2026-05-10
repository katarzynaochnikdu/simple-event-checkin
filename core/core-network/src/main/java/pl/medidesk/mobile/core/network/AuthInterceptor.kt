package pl.medidesk.mobile.core.network

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Response
import pl.medidesk.mobile.core.datastore.AuthDataStore
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val authDataStore: AuthDataStore,
    private val sessionManager: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            withTimeoutOrNull(3000L) {
                authDataStore.tokenFlow.first()
            }
        }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)

        val isLoginRequest = chain.request().url.encodedPath.endsWith("/login")
        if (response.code == 401 && !isLoginRequest) {
            Log.w("AuthInterceptor", "401 — session expired, forcing logout")
            runBlocking { authDataStore.clearAll() }
            sessionManager.notifySessionExpired()
        }

        return response
    }
}
