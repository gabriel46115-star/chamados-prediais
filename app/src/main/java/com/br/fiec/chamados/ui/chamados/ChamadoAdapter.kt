package com.br.fiec.chamados.ui.chamados

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.br.fiec.chamados.data.model.SolicitacaoResponseDTO
import com.br.fiec.chamados.data.model.rotuloOuPadrao
import com.br.fiec.chamados.databinding.ItemChamadoBinding

class ChamadoAdapter(
    private val chamados: MutableList<SolicitacaoResponseDTO> = mutableListOf(),
    private val onItemClick: ((SolicitacaoResponseDTO) -> Unit)? = null
) : RecyclerView.Adapter<ChamadoAdapter.ChamadoViewHolder>() {

    class ChamadoViewHolder(val binding: ItemChamadoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChamadoViewHolder {
        val binding = ItemChamadoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChamadoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChamadoViewHolder, position: Int) {
        val chamado = chamados[position]
        holder.binding.tvTitulo.text = chamado.titulo
        // Chamados urgentes (marcados manualmente ou por SLA estourado) ficam em vermelho
        val urgente = chamado.urgente == true
        holder.binding.tvStatus.text = if (urgente) {
            "${chamado.status.rotuloOuPadrao()} · URGENTE"
        } else {
            chamado.status.rotuloOuPadrao()
        }
        holder.binding.tvStatus.setTextColor(if (urgente) COR_URGENTE else COR_STATUS)
        holder.binding.tvStatus.setTypeface(null, if (urgente) Typeface.BOLD else Typeface.NORMAL)
        holder.binding.tvTipo.text = chamado.tipoNome ?: "Tipo não informado"

        val tecnico = chamado.tecnicoResponsavelNome
        holder.binding.tvResponsavel.text = "Técnico: $tecnico"
        holder.binding.tvResponsavel.visibility = if (tecnico.isNullOrBlank()) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener {
            if (onItemClick != null) {
                onItemClick.invoke(chamado)
            } else {
                val context = holder.itemView.context
                val intent = Intent(context, DetalheChamadoActivity::class.java).apply {
                    putExtra("CHAMADO_ID", chamado.id)
                }
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount(): Int = chamados.size

    fun atualizarLista(novosChamados: List<SolicitacaoResponseDTO>) {
        chamados.clear()
        chamados.addAll(novosChamados)
        notifyDataSetChanged()
    }

    companion object {
        private val COR_URGENTE = Color.parseColor("#C62828")
        private val COR_STATUS = Color.parseColor("#1565C0")
    }
}
