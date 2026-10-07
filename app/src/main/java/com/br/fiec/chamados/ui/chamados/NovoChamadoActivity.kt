package com.br.fiec.chamados.ui.chamados

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.br.fiec.chamados.data.model.CreateSolicitacaoRequestDTO
import com.br.fiec.chamados.data.model.TipoSolicitacao
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.databinding.ActivityNovoChamadoBinding
import com.google.gson.Gson
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class NovoChamadoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNovoChamadoBinding
    private val listaUrisSelecionadas = mutableListOf<Uri>()

    // Contrato para selecionar múltiplas imagens da galeria
    private val selecionarImagensLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            listaUrisSelecionadas.clear()
            listaUrisSelecionadas.addAll(uris)
            binding.tvAnexosContador.text = "${listaUrisSelecionadas.size} imagem(ns) selecionada(s)"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovoChamadoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDropdown()
        setupListeners()
    }

    private fun setupDropdown() {
        val tipos = TipoSolicitacao.entries.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tipos)
        binding.spinnerTipo.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnSelecionarFotos.setOnClickListener {
            selecionarImagensLauncher.launch("image/*")
        }

        binding.btnEnviar.setOnClickListener {
            validarEEnviarChamado()
        }

        binding.btnVoltar.setOnClickListener {
            finish()
        }
    }

    private fun validarEEnviarChamado() {
        val localizacao = binding.etLocalizacao.text.toString().trim()
        val descricao = binding.etDescricao.text.toString().trim()
        val patrimonio = binding.etPatrimonio.text.toString().trim().ifEmpty { null }
        val tipoSelecionado = binding.spinnerTipo.selectedItem?.toString()

        if (localizacao.isEmpty()) {
            binding.etLocalizacao.error = "Informe a localização do problema"
            return
        }

        if (descricao.isEmpty()) {
            binding.etDescricao.error = "Informe a descrição do problema"
            return
        }

        val tipoEnum = try {
            TipoSolicitacao.valueOf(tipoSelecionado ?: "MANUTENCAO")
        } catch (e: Exception) {
            TipoSolicitacao.MANUTENCAO
        }

        val dto = CreateSolicitacaoRequestDTO(
            tipo = tipoEnum,
            localizacaoProblema = localizacao,
            descricao = descricao,
            numeroPatrimonio = patrimonio
        )

        enviarSolicitacao(dto)
    }

    private fun enviarSolicitacao(dto: CreateSolicitacaoRequestDTO) {
        mostrarCarregando(true)

        lifecycleScope.launch {
            try {
                // 1. Converter DTO para RequestBody JSON com a chave "dados"
                val jsonDados = Gson().toJson(dto)
                val dadosPart = jsonDados.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                // 2. Converter Uris de imagens em MultipartBody.Part com a chave "anexos"
                val anexosParts = listaUrisSelecionadas.mapNotNull { uri ->
                    converterUriParaMultipart(uri, "anexos")
                }

                // 3. Chamada ao endpoint
                val apiService = RetrofitClient.getApiService(this@NovoChamadoActivity)
                val response = apiService.criarSolicitacao(
                    dados = dadosPart,
                    anexos = if (anexosParts.isNotEmpty()) anexosParts else null
                )

                if (response.isSuccessful) {
                    Toast.makeText(this@NovoChamadoActivity, "Chamado aberto com sucesso!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@NovoChamadoActivity, "Erro ao abrir chamado (Código ${response.code()})", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NovoChamadoActivity, "Falha de conexão: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun converterUriParaMultipart(uri: Uri, paramName: String): MultipartBody.Part? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(cacheDir, "temp_upload_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            MultipartBody.Part.createFormData(paramName, file.name, requestFile)
        } catch (e: Exception) {
            null
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
        binding.btnEnviar.isEnabled = !carregando
        binding.btnSelecionarFotos.isEnabled = !carregando
    }
}
