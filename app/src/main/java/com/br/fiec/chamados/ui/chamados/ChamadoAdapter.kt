package com.br.fiec.chamados.ui.chamados

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.br.fiec.chamados.R
import com.br.fiec.chamados.data.model.SolicitacaoResponseDTO

class ChamadoAdapter(
    private val chamados: MutableList<SolicitacaoResponseDTO> = mutableListOf()
) : RecyclerView.Adapter<ChamadoAdapter.ChamadoViewHolder>() {

    class ChamadoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTitulo)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvTipo: TextView = view.findViewById(R.id.tvTipo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChamadoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chamado, parent, false)
        return ChamadoViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChamadoViewHolder, position: Int) {
        val chamado = chamados[position]
        holder.tvTitulo.text = chamado.titulo
        holder.tvStatus.text = chamado.status.name
        holder.tvTipo.text = chamado.tipo.name
    }

    override fun getItemCount(): Int = chamados.size

    fun atualizarLista(novosChamados: List<SolicitacaoResponseDTO>) {
        chamados.clear()
        chamados.addAll(novosChamados)
        notifyDataSetChanged()
    }
}
