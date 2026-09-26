package com.br.fiec.chamados.ui.chamados

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.br.fiec.chamados.data.TokenManager
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.databinding.ActivityListaChamadosBinding
import com.br.fiec.chamados.ui.login.LoginActivity
import kotlinx.coroutines.launch

class ListaChamadosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListaChamadosBinding
    private val adapter = ChamadoAdapter()

    // Contrato para permissão de notificação no Android 13+
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Permissão de notificação negada.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Uso do View Binding
        binding = ActivityListaChamadosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarRecyclerView()
        criarCanalNotificacao()
        solicitarPermissaoNotificacao()

        // Carrega os dados do perfil e a lista de chamados
        carregarPerfilUsuario()
        carregarChamados()

        // Trata o clique na notificação caso a app estivesse fechada
        processarIntentNotificacao(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Trata o clique na notificação caso a app estivesse aberta em segundo plano
        processarIntentNotificacao(intent)
    }

    private fun configurarRecyclerView() {
        binding.rvChamados.layoutManager = LinearLayoutManager(this)
        binding.rvChamados.adapter = adapter
        binding.rvChamados.addItemDecoration(
            DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        )
    }

    /** Consumo da rota GET /api/v1/users/me */
    private fun carregarPerfilUsuario() {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@ListaChamadosActivity)
                val response = api.getMe() // Certifique-se de que getMe() existe no ApiService

                if (response.isSuccessful && response.body() != null) {
                    val usuario = response.body()!!
                    // Supondo que você tenha um TextView tvBoasVindas no seu XML activity_lista_chamados.xml
                    // binding.tvBoasVindas.text = "Olá, ${usuario.nome}"
                    setTitle("Chamados de ${usuario.nome}")
                }
            } catch (e: Exception) {
                // Erro silencioso ao carregar perfil (sem impactar a listagem)
            }
        }
    }

    private fun carregarChamados() {
        mostrarCarregando(true)

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@ListaChamadosActivity)
                val response = api.searchSolicitacoes()

                if (response.isSuccessful && response.body() != null) {
                    val pagina = response.body()!!
                    if (pagina.content.isEmpty()) {
                        mostrarMensagem("Nenhum chamado encontrado.")
                    } else {
                        adapter.atualizarLista(pagina.content)
                        binding.rvChamados.visibility = View.VISIBLE
                        binding.tvVazioOuErro.visibility = View.GONE
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    TokenManager(this@ListaChamadosActivity).clearToken()
                    startActivity(Intent(this@ListaChamadosActivity, LoginActivity::class.java))
                    finish()
                } else {
                    mostrarMensagem("Não foi possível carregar os chamados (erro ${response.code()}).")
                }
            } catch (e: Exception) {
                mostrarMensagem("Falha de conexão. Verifique se o backend está no ar.")
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun solicitarPermissaoNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "chamados_notification_channel",
                "Notificações de Chamados",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificações do sistema de chamados prediais"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun processarIntentNotificacao(intent: Intent?) {
        if (intent?.getBooleanExtra("EXTRA_FROM_NOTIFICATION", false) == true) {
            Toast.makeText(this, "Acessado via Notificação!", Toast.LENGTH_LONG).show()
            // Aqui você pode capturar o ID do chamado enviado na notificação e abrir o detalhe
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
    }

    private fun mostrarMensagem(mensagem: String) {
        binding.tvVazioOuErro.text = mensagem
        binding.tvVazioOuErro.visibility = View.VISIBLE
        binding.rvChamados.visibility = View.GONE
    }
}