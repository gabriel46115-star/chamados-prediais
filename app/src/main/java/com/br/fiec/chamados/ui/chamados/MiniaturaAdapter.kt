package com.br.fiec.chamados.ui.chamados

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.br.fiec.chamados.databinding.ItemMiniaturaBinding

/**
 * Lista horizontal de miniaturas de imagem, usada em dois lugares:
 * - Detalhe do chamado: anexos vindos do servidor (URL em String);
 * - Novo chamado: pré-visualização das fotos escolhidas na galeria (Uri).
 * O Coil aceita os dois tipos, por isso os itens são do tipo Any.
 */
class MiniaturaAdapter(
    private val onClick: (Any) -> Unit
) : RecyclerView.Adapter<MiniaturaAdapter.MiniaturaViewHolder>() {

    private val imagens = mutableListOf<Any>()

    class MiniaturaViewHolder(val binding: ItemMiniaturaBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MiniaturaViewHolder {
        val binding = ItemMiniaturaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MiniaturaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MiniaturaViewHolder, position: Int) {
        val imagem = imagens[position]
        holder.binding.ivMiniatura.load(imagem) {
            crossfade(true)
            error(android.R.drawable.ic_menu_report_image)
        }
        holder.binding.root.setOnClickListener { onClick(imagem) }
    }

    override fun getItemCount(): Int = imagens.size

    @SuppressLint("NotifyDataSetChanged")
    fun atualizarLista(novas: List<Any>) {
        imagens.clear()
        imagens.addAll(novas)
        notifyDataSetChanged()
    }
}
