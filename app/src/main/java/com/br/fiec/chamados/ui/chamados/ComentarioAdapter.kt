package com.br.fiec.chamados.ui.chamados

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.br.fiec.chamados.data.model.ComentarioResponseDTO
import com.br.fiec.chamados.databinding.ItemComentarioBinding
import com.br.fiec.chamados.ui.util.FormatoData

/** Lista de comentários do chamado, em ordem cronológica (estilo chat). */
class ComentarioAdapter : RecyclerView.Adapter<ComentarioAdapter.ComentarioViewHolder>() {

    private val comentarios = mutableListOf<ComentarioResponseDTO>()

    class ComentarioViewHolder(val binding: ItemComentarioBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComentarioViewHolder {
        val binding = ItemComentarioBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ComentarioViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ComentarioViewHolder, position: Int) {
        val comentario = comentarios[position]
        holder.binding.tvAutor.text = comentario.autorNome ?: "Usuário"
        holder.binding.tvData.text = FormatoData.formatar(comentario.criadoEm)
        holder.binding.tvTexto.text = comentario.texto
    }

    override fun getItemCount(): Int = comentarios.size

    @SuppressLint("NotifyDataSetChanged")
    fun atualizarLista(novos: List<ComentarioResponseDTO>) {
        comentarios.clear()
        comentarios.addAll(novos)
        notifyDataSetChanged()
    }

    /** Acrescenta o comentário recém-enviado no fim da lista, sem recarregar tudo. */
    fun adicionar(comentario: ComentarioResponseDTO) {
        comentarios.add(comentario)
        notifyItemInserted(comentarios.size - 1)
    }
}
