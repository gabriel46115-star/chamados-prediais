package com.br.fiec.chamados.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.br.fiec.chamados.R
import com.br.fiec.chamados.ui.chamados.ListaChamadosActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_TOKEN", "Novo FCM Token gerado: $token")
        // Aqui você pode enviar o token para o backend associar ao usuário logado, se necessário
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Captura título e texto da mensagem (bloco notification ou data)
        val titulo = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "Atualização de Chamado"

        val corpo = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "Você tem uma nova movimentação no chamado."

        exibirNotificacaoLocal(titulo, corpo, remoteMessage.data)
    }

    private fun exibirNotificacaoLocal(titulo: String, corpo: String, dadosExtras: Map<String, String>) {
        val channelId = "chamados_notification_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Cria o canal de notificação para Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notificações de Chamados",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Recebe alertas sobre atualizações dos chamados prediais"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Prepara o Intent disparado ao clicar na notificação
        val intent = Intent(this, ListaChamadosActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_FROM_NOTIFICATION", true)
            dadosExtras.forEach { (chave, valor) ->
                putExtra(chave, valor)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // certifique-se de ter este vetor/imagem em res/drawable
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}