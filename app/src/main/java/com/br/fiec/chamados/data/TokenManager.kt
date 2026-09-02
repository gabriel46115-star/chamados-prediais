package com.br.fiec.chamados.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Guarda o token JWT localmente (SharedPreferences).
 * Lembrete: o token do backend Kipper expira em 30 minutos (ver JwtUtil.EXPIRATION_TIME) —
 * então uma chamada pode voltar 401/403 depois desse tempo e será preciso logar de novo.
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

    fun isLoggedIn(): Boolean = !getToken().isNullOrBlank()

    companion object {
        private const val KEY_TOKEN = "jwt_token"
    }
}