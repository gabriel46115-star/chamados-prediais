package com.br.fiec.chamados.ui.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Com targetSdk 35+ o Android desenha o app de ponta a ponta (edge-to-edge): o conteúdo
 * fica por baixo da barra de status, da barra de navegação e, opcionalmente, do teclado.
 * Esta função soma esses espaços ao padding que a view já tem no XML.
 */
object EspacamentoBarras {

    fun aplicar(view: View, incluirTeclado: Boolean = false) {
        val esquerda = view.paddingLeft
        val topo = view.paddingTop
        val direita = view.paddingRight
        val baixo = view.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            var tipos = WindowInsetsCompat.Type.systemBars()
            if (incluirTeclado) tipos = tipos or WindowInsetsCompat.Type.ime()
            val barras = insets.getInsets(tipos)
            v.setPadding(
                esquerda + barras.left,
                topo + barras.top,
                direita + barras.right,
                baixo + barras.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }
}
