package com.br.fiec.chamados.ui.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Ditado por voz: abre o reconhecimento de fala do próprio Android (serviço de voz do Google)
 * e devolve o texto transcrito em português.
 *
 * Usa a tela de reconhecimento do sistema, então o app não precisa pedir a permissão de
 * microfone (RECORD_AUDIO): quem acessa o microfone é o serviço de voz do aparelho.
 *
 * Deve ser criado como propriedade da Activity (antes do onCreate terminar), porque registra
 * um ActivityResultLauncher.
 */
class DitadoPorVoz(
    private val activity: ComponentActivity,
    private val aoReceberTexto: (String) -> Unit
) {

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val texto = resultado.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!texto.isNullOrBlank()) aoReceberTexto(texto)
    }

    fun ouvir(dica: String = "Fale agora") {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, dica)
        }
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                activity,
                "Reconhecimento de voz não disponível neste aparelho.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        /**
         * Insere o texto ditado na posição do cursor (ou no fim), sem apagar o que já foi
         * digitado. Coloca espaço antes quando preciso e inicia com maiúscula no começo
         * do texto ou depois de um ponto final.
         */
        fun inserirNoCampo(campo: EditText, texto: String) {
            val atual = campo.text ?: return
            val selInicio = campo.selectionStart.takeIf { it >= 0 } ?: atual.length
            val selFim = campo.selectionEnd.takeIf { it >= 0 } ?: atual.length
            val inicio = minOf(selInicio, selFim)
            val fim = maxOf(selInicio, selFim)

            val antes = atual.substring(0, inicio).trimEnd()
            val comecoDeFrase = antes.isEmpty() || antes.last() in ".!?"
            val trecho = buildString {
                if (inicio > 0 && !atual[inicio - 1].isWhitespace()) append(' ')
                append(if (comecoDeFrase) texto.replaceFirstChar { it.uppercase() } else texto)
            }

            atual.replace(inicio, fim, trecho)
            campo.setSelection(inicio + trecho.length)
            campo.requestFocus()
        }
    }
}
