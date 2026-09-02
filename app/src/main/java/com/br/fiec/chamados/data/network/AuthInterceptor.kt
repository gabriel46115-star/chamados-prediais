package com.br.fiec.chamados.data.network

import com.br.fiec.chamados.data.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Rotas de auth não precisam (e não têm) token ainda
        if (original.url.encodedPath.contains("/api/v1/auth/")) {
            return chain.proceed(original)
        }

        val token = tokenManager.getToken()
        val request = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        return chain.proceed(request)
    }
}
