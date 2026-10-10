package com.br.fiec.chamados.ui.chamados

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.br.fiec.chamados.data.model.SolicitacaoResponseDTO
import com.br.fiec.chamados.data.model.rotuloOuPadrao
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.databinding.ActivityDetalheChamadoBinding
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DetalheChamadoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalheChamadoBinding
    private var chamadoId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalheChamadoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chamadoId = intent.getStringExtra("CHAMADO_ID")

        if (chamadoId.isNullOrEmpty()) {
            Toast.makeText(this, "ID do chamado inválido.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupListeners()
        carregarDetalhesChamado()
    }

    private fun setupListeners() {
        binding.btnVoltar.setOnClickListener {
            finish()
        }
    }

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
            tvDataAbertura.text = formatarData(chamado.dataAbertura)

            // Exibição de anexos
            val qtdAnexos = chamado.anexos?.size ?: 0
            tvAnexosInfo.text = if (qtdAnexos > 0) {
                "$qtdAnexos anexo(s) vinculado(s)"
            } else {
                "Nenhum anexo disponível"
            }
        }
    }

    /** Converte o LocalDateTime ISO do backend (ex: 2026-10-07T21:30:15.123) para dd/MM/yyyy HH:mm. */
    private fun formatarData(iso: String?): String {
        if (iso.isNullOrBlank()) return "-"
        return try {
            LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        } catch (e: Exception) {
            iso
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
        binding.contentLayout.visibility = if (carregando) View.GONE else View.VISIBLE
    }
}
