package com.br.fiec.chamados.ui.chamados

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.br.fiec.chamados.data.SessaoUsuario
import com.br.fiec.chamados.data.model.ComentarioRequestDTO
import com.br.fiec.chamados.data.model.StatusSolicitacao
import com.br.fiec.chamados.data.model.SolicitacaoResponseDTO
import com.br.fiec.chamados.data.model.rotuloOuPadrao
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.databinding.ActivityDetalheChamadoBinding
import com.br.fiec.chamados.ui.util.EspacamentoBarras
import com.br.fiec.chamados.ui.util.FormatoData
import com.br.fiec.chamados.ui.util.VisualizadorImagem
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * Detalhe do chamado, organizado em três abas:
 * - Detalhes: dados gerais (GET /solicitacoes/{id})
 * - Comentários: conversa sobre o chamado (GET/POST /solicitacoes/{id}/comentarios)
 * - Histórico: linha do tempo de movimentações (GET /solicitacoes/{id}/historico)
 *
 * Para o funcionário há também a ação "Alterar status" (PATCH /solicitacoes/{id}/status).
 * O funcionário não atribui chamados: isso é feito na aplicação para computador.
 */
class DetalheChamadoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalheChamadoBinding
    private var chamadoId: String? = null
    private var chamadoAtual: SolicitacaoResponseDTO? = null

    private val comentarioAdapter = ComentarioAdapter()
    private val movimentacaoAdapter = MovimentacaoAdapter()

    // Fotos anexadas ao chamado (toque abre em tela cheia)
    private val anexoAdapter = MiniaturaAdapter { imagem -> VisualizadorImagem.abrir(this, imagem) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalheChamadoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        aplicarEspacamentoDasBarras()

        chamadoId = intent.getStringExtra("CHAMADO_ID")

        if (chamadoId.isNullOrEmpty()) {
            Toast.makeText(this, "ID do chamado inválido.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        configurarAbas()
        configurarListas()
        setupListeners()

        carregarDetalhesChamado()
        carregarComentarios()
        carregarHistorico()
        configurarAcoesDoFuncionario()
    }

    /**
     * Com targetSdk 35+ o Android desenha o app de ponta a ponta (edge-to-edge): o conteúdo
     * fica por baixo da barra de status, da barra de navegação e do teclado. Aqui somamos
     * esses espaços ao padding original da tela, para o campo de comentário ficar visível
     * acima do teclado.
     */
    private fun aplicarEspacamentoDasBarras() {
        EspacamentoBarras.aplicar(binding.root, incluirTeclado = true)
    }

    private fun configurarAbas() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                mostrarAba(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        mostrarAba(ABA_DETALHES)
    }

    private fun mostrarAba(posicao: Int) {
        binding.abaDetalhes.visibility = if (posicao == ABA_DETALHES) View.VISIBLE else View.GONE
        binding.abaComentarios.visibility = if (posicao == ABA_COMENTARIOS) View.VISIBLE else View.GONE
        binding.abaHistorico.visibility = if (posicao == ABA_HISTORICO) View.VISIBLE else View.GONE

        if (posicao != ABA_COMENTARIOS) esconderTeclado()
    }

    private fun configurarListas() {
        // stackFromEnd: comentários se comportam como um chat, com os mais recentes embaixo
        binding.rvComentarios.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.rvComentarios.adapter = comentarioAdapter

        binding.rvHistorico.layoutManager = LinearLayoutManager(this)
        binding.rvHistorico.adapter = movimentacaoAdapter

        binding.rvAnexos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvAnexos.adapter = anexoAdapter
    }

    private fun setupListeners() {
        binding.btnVoltar.setOnClickListener {
            finish()
        }

        binding.btnAlterarStatus.setOnClickListener {
            mostrarOpcoesDeStatus()
        }

        binding.btnEnviarComentario.setOnClickListener {
            enviarComentario()
        }

        // Botão "enviar" do próprio teclado
        binding.etComentario.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                enviarComentario()
                true
            } else {
                false
            }
        }
    }

    // ---------------------------------------------------------------- Detalhes

    private fun carregarDetalhesChamado() {
        mostrarCarregando(true)

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(this@DetalheChamadoActivity)
                val response = apiService.buscarPorId(chamadoId!!)

                if (response.isSuccessful && response.body() != null) {
                    exibirDadosChamado(response.body()!!)
                } else {
                    Toast.makeText(
                        this@DetalheChamadoActivity,
                        "Erro ao carregar chamado (Código ${response.code()})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@DetalheChamadoActivity,
                    "Falha na rede: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun exibirDadosChamado(chamado: SolicitacaoResponseDTO) {
        chamadoAtual = chamado
        binding.apply {
            tvTituloTipo.text = listOfNotNull(chamado.tipoIcone, chamado.tipoNome)
                .joinToString(" ")
                .ifBlank { "Tipo não informado" }
            tvStatus.text = chamado.status.rotuloOuPadrao()
            tvPrioridade.text = if (chamado.urgente == true) {
                "Prioridade: ${chamado.prioridade.rotuloOuPadrao()} · URGENTE"
            } else {
                "Prioridade: ${chamado.prioridade.rotuloOuPadrao()}"
            }
            // Local/setor cadastrados (quando houver) + a descrição livre digitada pelo solicitante
            tvLocalizacao.text = listOfNotNull(chamado.setorNome, chamado.localNome, chamado.localizacaoProblema)
                .joinToString(" — ")
            tvPatrimonio.text = chamado.numeroPatrimonio ?: "Não informado"
            tvSolicitante.text = chamado.usuarioSolicitanteNome ?: "Não identificado"
            tvTecnico.text = chamado.tecnicoResponsavelNome ?: "Não atribuído"
            tvDescricao.text = chamado.descricao
            tvDataAbertura.text = FormatoData.formatar(chamado.dataAbertura)

            // Exibição de anexos: miniaturas carregadas de <servidor>/uploads/...
            val anexos = chamado.anexos.orEmpty()
            tvAnexosInfo.text = if (anexos.isNotEmpty()) {
                "${anexos.size} anexo(s) — toque para ampliar"
            } else {
                "Nenhum anexo disponível"
            }
            anexoAdapter.atualizarLista(anexos.map { RetrofitClient.urlCompleta(it.url) })
            rvAnexos.visibility = if (anexos.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ------------------------------------------------- Ações do funcionário

    /**
     * Mostra o botão "Alterar status" só para a equipe. O perfil normalmente já foi
     * carregado pela lista; se o app foi aberto direto aqui, é consultado agora.
     */
    private fun configurarAcoesDoFuncionario() {
        if (SessaoUsuario.usuario != null) {
            binding.btnAlterarStatus.visibility = if (SessaoUsuario.ehEquipe) View.VISIBLE else View.GONE
            return
        }
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@DetalheChamadoActivity).getMe()
                if (response.isSuccessful) SessaoUsuario.usuario = response.body()
            } catch (e: Exception) {
                // Sem perfil: o botão fica oculto
            }
            binding.btnAlterarStatus.visibility = if (SessaoUsuario.ehEquipe) View.VISIBLE else View.GONE
        }
    }

    private fun mostrarOpcoesDeStatus() {
        val statusAtual = chamadoAtual?.status
        val opcoes = STATUS_DO_FUNCIONARIO.filter { it != statusAtual }
        val rotulos = opcoes.map { it.rotulo }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Alterar status")
            .setItems(rotulos) { _, posicao -> confirmarAlteracaoDeStatus(opcoes[posicao]) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarAlteracaoDeStatus(novoStatus: StatusSolicitacao) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar alteração")
            .setMessage("Alterar o status do chamado para \"${novoStatus.rotulo}\"?")
            .setPositiveButton("Alterar") { _, _ -> alterarStatus(novoStatus) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun alterarStatus(novoStatus: StatusSolicitacao) {
        val id = chamadoId ?: return
        binding.btnAlterarStatus.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@DetalheChamadoActivity)
                    .atualizarStatus(id, novoStatus)

                if (response.isSuccessful && response.body() != null) {
                    val atualizado = response.body()!!
                    // Versões anteriores do backend devolviam esta resposta sem os anexos, o que
                    // fazia as fotos sumirem da tela. Por segurança, mantém os anexos já exibidos.
                    exibirDadosChamado(
                        if (atualizado.anexos.isNullOrEmpty()) atualizado.copy(anexos = chamadoAtual?.anexos)
                        else atualizado
                    )
                    // O backend registra "Status alterado" no histórico do chamado
                    carregarHistorico()
                    Toast.makeText(
                        this@DetalheChamadoActivity,
                        "Status alterado para \"${novoStatus.rotulo}\".",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@DetalheChamadoActivity,
                        "Não foi possível alterar o status (erro ${response.code()}).",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@DetalheChamadoActivity,
                    "Falha de conexão ao alterar o status.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnAlterarStatus.isEnabled = true
            }
        }
    }

    // ------------------------------------------------------------- Comentários

    private fun carregarComentarios() {
        val id = chamadoId ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@DetalheChamadoActivity).listarComentarios(id)

                if (response.isSuccessful) {
                    val comentarios = response.body().orEmpty()
                    comentarioAdapter.atualizarLista(comentarios)
                    atualizarTituloAba(ABA_COMENTARIOS, "Comentários", comentarios.size)
                    mostrarAvisoLista(
                        binding.tvComentariosVazio,
                        comentarios.isEmpty(),
                        "Nenhum comentário ainda.\nUse o campo abaixo para comentar."
                    )
                    if (comentarios.isNotEmpty()) {
                        binding.rvComentarios.scrollToPosition(comentarios.size - 1)
                    }
                } else {
                    mostrarAvisoLista(
                        binding.tvComentariosVazio, true,
                        "Não foi possível carregar os comentários (erro ${response.code()})."
                    )
                }
            } catch (e: Exception) {
                mostrarAvisoLista(binding.tvComentariosVazio, true, "Falha de conexão ao carregar os comentários.")
            }
        }
    }

    private fun enviarComentario() {
        val id = chamadoId ?: return
        val texto = binding.etComentario.text.toString().trim()

        if (texto.isEmpty()) {
            binding.etComentario.error = "Escreva um comentário"
            return
        }

        binding.btnEnviarComentario.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@DetalheChamadoActivity)
                    .comentar(id, ComentarioRequestDTO(texto = texto))

                if (response.isSuccessful && response.body() != null) {
                    binding.etComentario.text.clear()
                    comentarioAdapter.adicionar(response.body()!!)
                    binding.tvComentariosVazio.visibility = View.GONE
                    binding.rvComentarios.scrollToPosition(comentarioAdapter.itemCount - 1)
                    atualizarTituloAba(ABA_COMENTARIOS, "Comentários", comentarioAdapter.itemCount)

                    // O backend registra "Comentário adicionado" no histórico do chamado
                    carregarHistorico()
                } else {
                    Toast.makeText(
                        this@DetalheChamadoActivity,
                        "Não foi possível enviar o comentário (erro ${response.code()}).",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@DetalheChamadoActivity,
                    "Falha de conexão ao enviar o comentário.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.btnEnviarComentario.isEnabled = true
            }
        }
    }

    // ---------------------------------------------------------------- Histórico

    private fun carregarHistorico() {
        val id = chamadoId ?: return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@DetalheChamadoActivity).listarHistorico(id)

                if (response.isSuccessful) {
                    val movimentacoes = response.body().orEmpty()
                    movimentacaoAdapter.atualizarLista(movimentacoes)
                    atualizarTituloAba(ABA_HISTORICO, "Histórico", movimentacoes.size)
                    mostrarAvisoLista(
                        binding.tvHistoricoVazio,
                        movimentacoes.isEmpty(),
                        "Nenhuma movimentação registrada."
                    )
                } else {
                    mostrarAvisoLista(
                        binding.tvHistoricoVazio, true,
                        "Não foi possível carregar o histórico (erro ${response.code()})."
                    )
                }
            } catch (e: Exception) {
                mostrarAvisoLista(binding.tvHistoricoVazio, true, "Falha de conexão ao carregar o histórico.")
            }
        }
    }

    // -------------------------------------------------------------- Utilitários

    /** Mostra a quantidade de itens no título da aba, ex: "Comentários (3)". */
    private fun atualizarTituloAba(posicao: Int, nome: String, quantidade: Int) {
        binding.tabLayout.getTabAt(posicao)?.text = if (quantidade > 0) "$nome ($quantidade)" else nome
    }

    private fun mostrarAvisoLista(aviso: TextView, mostrar: Boolean, mensagem: String) {
        aviso.text = mensagem
        aviso.visibility = if (mostrar) View.VISIBLE else View.GONE
    }

    private fun esconderTeclado() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun mostrarCarregando(carregando: Boolean) {
        binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
        binding.contentLayout.visibility = if (carregando) View.GONE else View.VISIBLE
    }

    companion object {
        private const val ABA_DETALHES = 0
        private const val ABA_COMENTARIOS = 1
        private const val ABA_HISTORICO = 2

        /**
         * Status que o funcionário pode escolher no app. Rejeitar e cancelar são decisões
         * de gestão (rejeitar exige motivo) e ficam na aplicação para computador.
         */
        private val STATUS_DO_FUNCIONARIO = listOf(
            StatusSolicitacao.EM_ANDAMENTO,
            StatusSolicitacao.AGUARDANDO_ASSISTENCIA_EXTERNA,
            StatusSolicitacao.CONCLUIDO,
            StatusSolicitacao.FINALIZADA
        )
    }
}
