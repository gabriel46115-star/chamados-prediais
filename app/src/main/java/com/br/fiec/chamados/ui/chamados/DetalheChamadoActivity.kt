package com.br.fiec.chamados.ui.chamados

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.br.fiec.chamados.data.model.ComentarioRequestDTO
import com.br.fiec.chamados.data.model.SolicitacaoResponseDTO
import com.br.fiec.chamados.data.model.rotuloOuPadrao
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.databinding.ActivityDetalheChamadoBinding
import com.br.fiec.chamados.ui.util.FormatoData
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * Detalhe do chamado, organizado em três abas:
 * - Detalhes: dados gerais (GET /solicitacoes/{id})
 * - Comentários: conversa sobre o chamado (GET/POST /solicitacoes/{id}/comentarios)
 * - Histórico: linha do tempo de movimentações (GET /solicitacoes/{id}/historico)
 */
class DetalheChamadoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalheChamadoBinding
    private var chamadoId: String? = null

    private val comentarioAdapter = ComentarioAdapter()
    private val movimentacaoAdapter = MovimentacaoAdapter()

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
    }

    /**
     * Com targetSdk 35+ o Android desenha o app de ponta a ponta (edge-to-edge): o conteúdo
     * fica por baixo da barra de status, da barra de navegação e do teclado. Aqui somamos
     * esses espaços ao padding original da tela, para o campo de comentário ficar visível
     * acima do teclado.
     */
    private fun aplicarEspacamentoDasBarras() {
        val paddingOriginal = binding.root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val barras = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            view.setPadding(
                paddingOriginal + barras.left,
                paddingOriginal + barras.top,
                paddingOriginal + barras.right,
                paddingOriginal + barras.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
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
    }

    private fun setupListeners() {
        binding.btnVoltar.setOnClickListener {
            finish()
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

            // Exibição de anexos
            val qtdAnexos = chamado.anexos?.size ?: 0
            tvAnexosInfo.text = if (qtdAnexos > 0) {
                "$qtdAnexos anexo(s) vinculado(s)"
            } else {
                "Nenhum anexo disponível"
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
    }
}
