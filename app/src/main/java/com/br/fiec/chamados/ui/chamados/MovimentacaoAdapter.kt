package com.br.fiec.chamados.ui.chamados

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.br.fiec.chamados.data.model.MovimentacaoResponseDTO
import com.br.fiec.chamados.databinding.ItemMovimentacaoBinding
import com.br.fiec.chamados.ui.util.FormatoData

/** Linha do tempo com o histórico de movimentações do chamado (mais recente primeiro). */
class MovimentacaoAdapter : RecyclerView.Adapter<MovimentacaoAdapter.MovimentacaoViewHolder>() {

    private val movimentacoes = mutableListOf<MovimentacaoResponseDTO>()

    class MovimentacaoViewHolder(val binding: ItemMovimentacaoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovimentacaoViewHolder {
        val binding = ItemMovimentacaoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MovimentacaoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovimentacaoViewHolder, position: Int) {
        val movimentacao = movimentacoes[position]
        with(holder.binding) {
            tvTitulo.text = movimentacao.titulo

            tvDescricao.text = movimentacao.descricao
            tvDescricao.visibility = if (movimentacao.descricao.isNullOrBlank()) View.GONE else View.VISIBLE

            // Movimentações automáticas (ex: SLA estourado) não têm autor
            val autor = movimentacao.autorNome ?: "Sistema"
            tvAutorData.text = "$autor · ${FormatoData.formatar(movimentacao.criadoEm)}"

            // O traço vertical liga um evento ao próximo; no último item ele não é desenhado
            viewLinha.visibility = if (position == movimentacoes.lastIndex) View.INVISIBLE else View.VISIBLE
        }
    }

    override fun getItemCount(): Int = movimentacoes.size

    @SuppressLint("NotifyDataSetChanged")
    fun atualizarLista(novas: List<MovimentacaoResponseDTO>) {
        movimentacoes.clear()
        movimentacoes.addAll(novas)
        notifyDataSetChanged()
    }
}
