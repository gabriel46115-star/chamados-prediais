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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.br.fiec.chamados.data.SessaoUsuario
import com.br.fiec.chamados.data.TokenManager
import com.br.fiec.chamados.data.model.StatusSolicitacao
import com.br.fiec.chamados.data.model.UserMeDTO
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.databinding.ActivityListaChamadosBinding
import com.br.fiec.chamados.ui.login.LoginActivity
import com.br.fiec.chamados.ui.util.EspacamentoBarras
import com.google.android.material.tabs.TabLayout
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Tela principal após o login. Adapta-se ao perfil do usuário (GET /api/v1/users/me):
 * - Usuário comum: lista dos próprios chamados (o backend já filtra pelo solicitante).
 * - Funcionário: painel com os chamados por situação, incluindo os atribuídos a ele e aos
 *   colegas. O funcionário não atribui chamados (isso é feito no computador).
 *
 * Escopo do app: uso rápido no dia a dia. No celular não há distinção entre funcionário,
 * supervisor e administrador (todos usam o painel de funcionário). Histórico completo,
 * indicadores de supervisão e administração ficam na aplicação para computador.
 */
class ListaChamadosActivity : AppCompatActivity() {

    /**
     * Abas do painel do funcionário. O card de cada chamado mostra o técnico responsável,
     * então em "Em andamento" o funcionário vê também os chamados dos colegas.
     * "Para fechar" são os concluídos pelo técnico, aguardando a validação do solicitante.
     */
    private enum class FiltroPainel(val rotulo: String, val status: StatusSolicitacao?, val atribuidosAMim: Boolean = false) {
        ABERTOS("Abertos", StatusSolicitacao.ABERTO),
        ATRIBUIDOS_A_MIM("Atribuídos a mim", null, atribuidosAMim = true),
        EM_ANDAMENTO("Em andamento", StatusSolicitacao.EM_ANDAMENTO),
        AGUARDANDO("Aguardando", StatusSolicitacao.AGUARDANDO_ASSISTENCIA_EXTERNA),
        PARA_FECHAR("Para fechar", StatusSolicitacao.CONCLUIDO),
        FINALIZADOS("Finalizados", StatusSolicitacao.FINALIZADA)
    }

    private lateinit var binding: ActivityListaChamadosBinding

    // Perfil do usuário logado, compartilhado com a tela de detalhe
    private val usuario: UserMeDTO?
        get() = SessaoUsuario.usuario
    private var filtroAtual = FiltroPainel.ABERTOS

    private val ehEquipe: Boolean
        get() = SessaoUsuario.ehEquipe

    private val adapter = ChamadoAdapter { chamado ->
        val intent = Intent(this, DetalheChamadoActivity::class.java).apply {
            putExtra("CHAMADO_ID", chamado.id)
        }
        startActivity(intent)
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "As notificações do aplicativo estão desativadas.", Toast.LENGTH_LONG).show()
        }
    }

    private val novoChamadoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            atualizarTudo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListaChamadosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacamentoDasBarras()

        configurarRecyclerView()
        criarCanalNotificacao()
        solicitarPermissaoNotificacao()

        binding.fabNovoChamado.setOnClickListener {
            novoChamadoLauncher.launch(Intent(this, NovoChamadoActivity::class.java))
        }

        binding.btnSair.setOnClickListener {
            confirmarSaida()
        }

        carregarPerfilEChamados()

        // Trata o clique na notificação caso a app estivesse fechada
        processarIntentNotificacao(intent)
    }

    /** Ao voltar do detalhe, o chamado pode ter mudado de status: recarrega a lista e as contagens. */
    override fun onRestart() {
        super.onRestart()
        if (usuario != null) atualizarTudo()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Trata o clique na notificação caso a app estivesse aberta em segundo plano
        processarIntentNotificacao(intent)
    }

    /** Mesmo ajuste do detalhe: o app é desenhado de ponta a ponta (targetSdk 35+). */
    private fun aplicarEspacamentoDasBarras() {
        EspacamentoBarras.aplicar(binding.root)
    }

    private fun configurarRecyclerView() {
        binding.rvChamados.layoutManager = LinearLayoutManager(this)
        binding.rvChamados.adapter = adapter
        binding.rvChamados.addItemDecoration(
            DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
        )
    }

    // ------------------------------------------------------------ Perfil e modo

    /**
     * Primeiro descobre quem está logado (GET /users/me) para decidir o modo da tela;
     * só depois carrega os chamados. Se o perfil falhar, cai no modo de usuário comum.
     */
    private fun carregarPerfilEChamados() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@ListaChamadosActivity).getMe()
                if (response.isSuccessful) {
                    SessaoUsuario.usuario = response.body()
                }
            } catch (e: Exception) {
                // Sem perfil: segue no modo de usuário comum
            }

            exibirCabecalho()
            if (ehEquipe) configurarPainelEquipe()
            atualizarTudo()
        }
    }

    private fun exibirCabecalho() {
        val nome = usuario?.nome?.split(" ")?.firstOrNull()?.lowercase()?.replaceFirstChar { it.uppercase() }
        binding.tvSaudacao.text = if (nome.isNullOrBlank()) "Chamados" else "Olá, $nome"
        // No app, supervisor e administrador usam o mesmo painel do funcionário
        binding.tvPerfil.text = if (ehEquipe) "Funcionário" else "Seus chamados"
    }

    private fun configurarPainelEquipe() {
        binding.tabStatus.visibility = View.VISIBLE
        binding.tabStatus.removeAllTabs()
        FiltroPainel.entries.forEach { filtro ->
            binding.tabStatus.addTab(binding.tabStatus.newTab().setText(filtro.rotulo).setTag(filtro))
        }

        binding.tabStatus.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                filtroAtual = tab.tag as FiltroPainel
                carregarChamados()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                carregarChamados()
            }
        })
    }

    private fun atualizarTudo() {
        carregarChamados()
        if (ehEquipe) carregarContagens()
    }

    // ------------------------------------------------------------------ Busca

    private fun carregarChamados() {
        mostrarCarregando(true)

        // Usuário comum: sem filtro (o backend já devolve só os chamados dele)
        val filtro = if (ehEquipe) filtroAtual else null

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@ListaChamadosActivity)
                val response = api.searchSolicitacoes(
                    status = filtro?.status,
                    // "Atribuídos a mim" sem id conhecido usaria um filtro vazio (todos os chamados)
                    tecnicoResponsavelId = if (filtro?.atribuidosAMim == true) usuario?.id ?: ID_INEXISTENTE else null,
                    size = TAMANHO_PAGINA
                )

                if (response.isSuccessful && response.body() != null) {
                    val chamados = response.body()!!.content
                    adapter.atualizarLista(chamados)
                    if (chamados.isEmpty()) {
                        mostrarMensagem(
                            if (filtro == null) "Você ainda não abriu nenhum chamado.\nToque em + para abrir o primeiro."
                            else "Nenhum chamado em \"${filtro.rotulo}\"."
                        )
                    } else {
                        binding.rvChamados.visibility = View.VISIBLE
                        binding.tvVazioOuErro.visibility = View.GONE
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    // Sessão expirada é tratada pelo AuthInterceptor (volta ao login sozinho);
                    // se chegou aqui com token válido, é falta de permissão.
                    mostrarMensagem("Você não tem permissão para ver estes chamados.")
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

    /**
     * Mostra a quantidade de chamados no título de cada aba, ex: "Abertos (4)".
     * Usa a própria busca paginada com size=1: só interessa o totalElements.
     * As consultas são feitas em paralelo.
     */
    private fun carregarContagens() {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@ListaChamadosActivity)
                // coroutineScope: se uma das consultas falhar, a exceção cai no catch abaixo
                val totais = coroutineScope {
                    FiltroPainel.entries.map { filtro ->
                    async {
                        val response = api.searchSolicitacoes(
                            status = filtro.status,
                            tecnicoResponsavelId = if (filtro.atribuidosAMim) usuario?.id else null,
                            size = 1
                        )
                        filtro to (response.body()?.totalElements ?: -1L)
                    }
                    }.awaitAll()
                }

                totais.forEach { (filtro, total) ->
                    val aba = binding.tabStatus.getTabAt(filtro.ordinal)
                    aba?.text = if (total >= 0) "${filtro.rotulo} ($total)" else filtro.rotulo
                }
            } catch (e: Exception) {
                // Contagens são só informativas: em caso de erro, as abas ficam sem número
            }
        }
    }

    // ------------------------------------------------------------------- Sair

    private fun confirmarSaida() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Deseja sair da sua conta?")
            .setPositiveButton("Sair") { _, _ -> sair() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Encerra a sessão neste aparelho:
     * - descarta o token FCM, para o aparelho deixar de receber notificações dessa conta
     *   (um novo token é gerado e registrado no próximo login);
     * - limpa o estado do Credential Manager, para o próximo login com Google voltar a
     *   mostrar o seletor de contas;
     * - apaga o JWT e o perfil guardados e volta ao login, sem permitir voltar com o botão "Voltar".
     */
    private fun sair() {
        binding.btnSair.isEnabled = false

        lifecycleScope.launch {
            try {
                FirebaseMessaging.getInstance().deleteToken()
            } catch (e: Exception) {
                // Sem conexão com o Firebase: o token antigo apenas deixa de ser usado
            }
            try {
                CredentialManager.create(this@ListaChamadosActivity)
                    .clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                // Nada a limpar (ex: login feito por e-mail e senha)
            }

            TokenManager(this@ListaChamadosActivity).clearToken()
            SessaoUsuario.limpar()

            startActivity(Intent(this@ListaChamadosActivity, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }
    }

    // ------------------------------------------------------------ Notificações

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

    /**
     * Ao tocar em uma notificação, abre direto o chamado (id no bloco "data" da mensagem).
     * O extra é removido em seguida para o chamado não reabrir ao girar a tela.
     */
    private fun processarIntentNotificacao(intent: Intent?) {
        val chamadoId = intent?.getStringExtra(LoginActivity.EXTRA_CHAMADO_ID)
        if (!chamadoId.isNullOrBlank()) {
            intent?.removeExtra(LoginActivity.EXTRA_CHAMADO_ID)
            startActivity(Intent(this, DetalheChamadoActivity::class.java).apply {
                putExtra("CHAMADO_ID", chamadoId)
            })
        }
        // A lista também é recarregada para refletir a novidade
        if (intent?.getBooleanExtra("EXTRA_FROM_NOTIFICATION", false) == true && usuario != null) {
            atualizarTudo()
        }
    }

    // -------------------------------------------------------------- Utilitários

    private fun mostrarCarregando(carregando: Boolean) {
        binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
    }

    private fun mostrarMensagem(mensagem: String) {
        binding.tvVazioOuErro.text = mensagem
        binding.tvVazioOuErro.visibility = View.VISIBLE
        binding.rvChamados.visibility = View.GONE
    }

    companion object {
        private const val TAMANHO_PAGINA = 50
        private const val ID_INEXISTENTE = "00000000-0000-0000-0000-000000000000"
    }
}
