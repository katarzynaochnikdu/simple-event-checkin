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
    private val sessionManager: SessionManager,
    private val sessionWipeHook: SessionWipeHook
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
            runBlocking {
                // Token czyścimy bezpośrednio NAJPIERW — nawet gdyby pełny wipe rzucił,
                // żadne kolejne żądanie nie może wyjść z martwym (a wciąż lokalnie żywym) JWT.
                authDataStore.clearAll()
                // WO-MOB-028 (F2A-001): pełny wipe lokalnego cache'u PII (Room) musi wykonać
                // się także na ścieżce 401 — przez SessionWipeHook, bo core-network nie może
                // zależeć od core-sync (cykl modułów). Hook jest idempotentny względem clearAll().
                try {
                    sessionWipeHook.onSessionExpired()
                } catch (e: Exception) {
                    // Nie zatruwamy łańcucha interceptorów wyjątkiem nie-IO — response 401
                    // i tak wraca do callera, a nawigację do logowania robi SessionManager.
                    Log.e("AuthInterceptor", "Local PII wipe after 401 failed", e)
                }
            }
            sessionManager.notifySessionExpired()
        }

        return response
    }
}
