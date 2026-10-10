package com.br.fiec.chamados.ui.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Formatação de datas vindas do backend (LocalDateTime em ISO, ex: 2026-10-09T21:30:15.123). */
object FormatoData {

    private val formatoDataHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    /** Converte para dd/MM/yyyy HH:mm; devolve "-" se vazio ou o texto original se não reconhecer. */
    fun formatar(iso: String?): String {
        if (iso.isNullOrBlank()) return "-"
        return try {
            LocalDateTime.parse(iso).format(formatoDataHora)
        } catch (e: Exception) {
            iso
        }
    }
}
