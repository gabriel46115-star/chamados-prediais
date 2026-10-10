package com.br.fiec.chamados.data.network

import android.content.Context
import android.content.Intent
import com.br.fiec.chamados.data.TokenManager
import com.br.fiec.chamados.ui.login.LoginActivity
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 1. Injeta "Authorization: Bearer <jwt>" em todas as requisições autenticadas.
 * 2. Trata sessão expirada em um único lugar, para qualquer tela do app.
 *
 * No backend, token vencido ou inválido resulta em HTTP 403 (entry point padrão do
 * Spring Security). Como 401/403 também podem significar "sem permissão", o app só
 * desloga quando o próprio token salvo está de fato expirado (campo "exp" do JWT).
 */
class AuthInterceptor(
    context: Context,
    private val tokenManager: TokenManager
) : Interceptor {

    private val appContext = context.applicationContext

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

        val response = chain.proceed(request)

        if ((response.code == 401 || response.code == 403) && tokenManager.isTokenExpirado(margemSegundos = 0)) {
            encerrarSessao()
        }

        return response
    }

    /** Limpa o token e volta para o login, uma única vez mesmo com várias requisições simultâneas. */
    private fun encerrarSessao() {
        if (!sessaoEncerrada.compareAndSet(false, true)) return

        tokenManager.clearToken()
        val intent = Intent(appContext, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(LoginActivity.EXTRA_SESSAO_EXPIRADA, true)
        }
        appContext.startActivity(intent)
    }

    companion object {
        private val sessaoEncerrada = AtomicBoolean(false)

        /** Chamado após um login bem-sucedido, liberando um novo encerramento futuro. */
        fun novaSessaoIniciada() {
            sessaoEncerrada.set(false)
        }
    }
}
