package com.br.fiec.chamados.ui.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.ImageView
import coil.load

/** Abre uma imagem (URL ou Uri) em tela cheia; um toque fecha. */
object VisualizadorImagem {

    fun abrir(context: Context, imagem: Any) {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            contentDescription = "Imagem ampliada"
            setOnClickListener { dialog.dismiss() }
            load(imagem) {
                crossfade(true)
                error(android.R.drawable.ic_menu_report_image)
            }
        }

        dialog.setContentView(imageView)
        dialog.show()
    }
}
