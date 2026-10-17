package com.br.fiec.chamados.data.network

import android.content.Context
import android.util.Log
import com.br.fiec.chamados.data.TokenManager
import com.br.fiec.chamados.data.model.FcmTokenRequestDTO
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Envia ao backend o token FCM deste aparelho (PATCH /api/v1/users/me/fcm-token).
 *
 * O backend só consegue mandar notificações push para usuários que têm fcmToken salvo.
 * O envio acontece:
 *  - logo após o login (inclusive ao reabrir o app com sessão ainda válida);
 *  - quando o Firebase gera um token novo (MyFirebaseMessagingService.onNewToken).
 *
 * Falhas são apenas registradas no Logcat: não impedem o uso do app, e o envio é
 * repetido no próximo login.
 */
object FcmTokenSync {

    private const val TAG = "FcmTokenSync"

    // Escopo próprio: o envio continua mesmo se a tela que o iniciou for fechada
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Obtém o token atual do Firebase e envia ao backend. */
    fun sincronizar(context: Context) {
        val appContext = context.applicationContext
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> enviarToken(appContext, token) }
            .addOnFailureListener { e -> Log.w(TAG, "Não foi possível obter o token FCM: ${e.message}") }
    }

    /** Envia um token já conhecido, desde que exista sessão válida. */
    fun enviarToken(context: Context, fcmToken: String) {
        val appContext = context.applicationContext
        if (!TokenManager(appContext).isLoggedIn()) return

        escopo.launch {
            try {
                val response = RetrofitClient.getApiService(appContext)
                    .atualizarFcmToken(FcmTokenRequestDTO(fcmToken = fcmToken))
                if (response.isSuccessful) {
                    Log.d(TAG, "Token FCM registrado no backend")
                } else {
                    Log.w(TAG, "Backend recusou o token FCM (erro ${response.code()})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha de rede ao enviar o token FCM: ${e.message}")
            }
        }
    }
}
