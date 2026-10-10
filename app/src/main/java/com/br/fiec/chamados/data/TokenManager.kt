package com.br.fiec.chamados.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject

/**
 * Guarda o token JWT do servidor localmente (SharedPreferences).
 *
 * O token do backend Kipper expira em 30 minutos (ver JwtUtil.EXPIRATION_TIME) e não há
 * refresh token. Por isso o app lê o campo "exp" do próprio JWT para saber se a sessão
 * ainda é válida — sem precisar consultar o servidor.
 */
class TokenManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chamados_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    /** Há token salvo e ele ainda não expirou. */
    fun isLoggedIn(): Boolean = !isTokenExpirado()

    /**
     * true se não houver token, se ele estiver malformado ou se faltar menos de
     * [margemSegundos] para expirar (a margem evita mandar um token que vence no caminho).
     */
    fun isTokenExpirado(margemSegundos: Long = 30): Boolean {
        val token = getToken()
        if (token.isNullOrBlank()) return true

        val expiraEmSegundos = lerExpiracao(token) ?: return true
        val agoraEmSegundos = System.currentTimeMillis() / 1000
        return agoraEmSegundos + margemSegundos >= expiraEmSegundos
    }

    /**
     * Um JWT tem o formato header.payload.assinatura, cada parte em Base64 URL-safe.
     * O payload é um JSON com o campo "exp" (segundos desde 1970). A assinatura não é
     * verificada aqui — quem valida o token de verdade é o backend.
     */
    private fun lerExpiracao(token: String): Long? {
        return try {
            val partes = token.split(".")
            if (partes.size < 2) return null
            val payload = String(
                Base64.decode(partes[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            JSONObject(payload).optLong("exp").takeIf { it > 0 }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
    }
}
