package pl.medidesk.mobile.core.network

import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.Response
import pl.medidesk.mobile.core.datastore.AuthDataStore
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val authDataStore: AuthDataStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            withTimeoutOrNull(3000L) {
                authDataStore.tokenFlow.first()
            }
        }
        if (token == null) {
            Log.w("AuthInterceptor", "No JWT token available for request: ${chain.request().url}")
        }
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
