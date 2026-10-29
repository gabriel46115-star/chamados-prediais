package com.br.fiec.chamados.ui.chamados

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.br.fiec.chamados.data.model.CreateSolicitacaoRequestDTO
import com.br.fiec.chamados.data.model.TipoChamadoDTO
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.databinding.ActivityNovoChamadoBinding
import com.br.fiec.chamados.ui.util.DitadoPorVoz
import com.br.fiec.chamados.ui.util.EspacamentoBarras
import com.br.fiec.chamados.ui.util.VisualizadorImagem
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

    // Pré-visualização das imagens escolhidas (toque abre em tela cheia)
    private val miniaturaAdapter = MiniaturaAdapter { imagem -> VisualizadorImagem.abrir(this, imagem) }

    // Ditado por voz: o texto falado entra na descrição, na posição do cursor
    private val ditado = DitadoPorVoz(this) { texto ->
        DitadoPorVoz.inserirNoCampo(binding.etDescricao, texto)
    }

    // Só libera o envio depois que os tipos de chamado forem carregados da API
    private var tiposCarregados = false

    // Contrato para selecionar múltiplas imagens da galeria
    private val selecionarImagensLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            listaUrisSelecionadas.clear()
            listaUrisSelecionadas.addAll(uris)
            binding.tvAnexosContador.text = "${listaUrisSelecionadas.size} imagem(ns) selecionada(s)"
            miniaturaAdapter.atualizarLista(listaUrisSelecionadas)
            binding.rvMiniaturas.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovoChamadoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EspacamentoBarras.aplicar(binding.root, incluirTeclado = true)

        setupDropdown()
        setupListeners()

        binding.rvMiniaturas.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvMiniaturas.adapter = miniaturaAdapter
    }

    /**
     * Na versão V4 do backend os tipos de chamado são cadastrados pelo administrador
     * no banco (com SLA próprio), então a lista vem de GET /api/v1/admin/tipos.
     */
    private fun setupDropdown() {
        binding.btnEnviar.isEnabled = false

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@NovoChamadoActivity)
                val response = api.listarTipos()

                if (response.isSuccessful) {
                    val tipos = response.body().orEmpty().sortedBy { it.nome }
                    if (tipos.isEmpty()) {
                        mostrarAvisoNoSpinner("Nenhum tipo de chamado disponível")
                        return@launch
                    }
                    binding.spinnerTipo.adapter = ArrayAdapter(
                        this@NovoChamadoActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        tipos
                    )
                    tiposCarregados = true
                    binding.btnEnviar.isEnabled = true
                } else {
                    mostrarAvisoNoSpinner("Erro ao carregar os tipos (código ${response.code()})")
                    Toast.makeText(
                        this@NovoChamadoActivity,
                        "Erro ao carregar os tipos de chamado (Código ${response.code()})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                mostrarAvisoNoSpinner("Sem conexão com o servidor")
                Toast.makeText(
                    this@NovoChamadoActivity,
                    "Falha de conexão ao carregar os tipos de chamado.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Um Spinner sem itens não abre ao ser tocado, o que parece um defeito. Quando os tipos
     * não puderem ser carregados, o motivo é exibido como item único e desabilitado.
     */
    private fun mostrarAvisoNoSpinner(mensagem: String) {
        binding.spinnerTipo.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(mensagem)
        )
        binding.spinnerTipo.isEnabled = false
    }

    private fun setupListeners() {
        binding.btnSelecionarFotos.setOnClickListener {
            selecionarImagensLauncher.launch("image/*")
        }

        binding.btnMicDescricao.setOnClickListener {
            ditado.ouvir("Descreva o problema")
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
        val tipoSelecionado = binding.spinnerTipo.selectedItem as? TipoChamadoDTO

        if (tipoSelecionado == null) {
            Toast.makeText(this, "Selecione o tipo do chamado", Toast.LENGTH_SHORT).show()
            return
        }

        if (localizacao.isEmpty()) {
            binding.etLocalizacao.error = "Informe a localização do problema"
            return
        }

        if (descricao.isEmpty()) {
            binding.etDescricao.error = "Informe a descrição do problema"
            return
        }

        val dto = CreateSolicitacaoRequestDTO(
            tipoId = tipoSelecionado.id,
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
                val anexosParts = listaUrisSelecionadas.mapIndexedNotNull { indice, uri ->
                    converterUriParaMultipart(uri, "anexos", indice)
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

    /**
     * Copia a imagem escolhida para o cache e monta a parte do multipart.
     * - O tipo real (image/jpeg, image/png...) e a extensão vêm do próprio arquivo.
     * - O índice no nome evita que duas imagens processadas no mesmo milissegundo
     *   gerem o mesmo arquivo temporário e uma sobrescreva a outra.
     */
    private fun converterUriParaMultipart(uri: Uri, paramName: String, indice: Int): MultipartBody.Part? {
        return try {
            val tipo = contentResolver.getType(uri) ?: "image/jpeg"
            val extensao = MimeTypeMap.getSingleton().getExtensionFromMimeType(tipo) ?: "jpg"
            val file = File(cacheDir, "foto_${System.currentTimeMillis()}_$indice.$extensao")

            val inputStream = contentResolver.openInputStream(uri) ?: return null
            inputStream.use { entrada ->
                file.outputStream().use { saida -> entrada.copyTo(saida) }
            }

            val requestFile = file.asRequestBody(tipo.toMediaTypeOrNull())
            MultipartBody.Part.createFormData(paramName, file.name, requestFile)
        } catch (e: Exception) {
            null
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
        binding.btnEnviar.isEnabled = !carregando && tiposCarregados
        binding.btnSelecionarFotos.isEnabled = !carregando
    }
}
