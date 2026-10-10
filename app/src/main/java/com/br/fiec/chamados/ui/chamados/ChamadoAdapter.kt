package com.br.fiec.chamados.ui.chamados

import android.content.Intent
import android.view.LayoutInflater
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
        holder.binding.tvStatus.text = if (chamado.urgente == true) {
            "${chamado.status.rotuloOuPadrao()} · URGENTE"
        } else {
            chamado.status.rotuloOuPadrao()
        }
        holder.binding.tvTipo.text = chamado.tipoNome ?: "Tipo não informado"

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
}
