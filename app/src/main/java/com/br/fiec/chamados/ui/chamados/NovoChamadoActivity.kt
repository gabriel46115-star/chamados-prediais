package com.br.fiec.chamados.ui.chamados

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.br.fiec.chamados.data.model.CreateSolicitacaoRequestDTO
import com.br.fiec.chamados.data.model.OrganizarDescricaoRequestDTO
import com.br.fiec.chamados.data.model.OrganizarDescricaoResponseDTO
import com.br.fiec.chamados.data.model.TipoChamadoDTO
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.databinding.ActivityNovoChamadoBinding
import com.br.fiec.chamados.ui.util.DitadoPorVoz
import com.br.fiec.chamados.ui.util.EspacamentoBarras
import com.br.fiec.chamados.ui.util.VisualizadorImagem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import org.json.JSONObject

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
    private var tiposDisponiveis: List<TipoChamadoDTO> = emptyList()

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
        verificarIaDisponivel()
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
                    tiposDisponiveis = tipos
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

        binding.btnOrganizarIa.setOnClickListener {
            organizarDescricaoComIa()
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
                // 1. Cada campo vira uma parte de texto do multipart
                fun texto(valor: String) = valor.toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())

                // 2. Converter Uris de imagens em MultipartBody.Part com a chave "anexos"
                val anexosParts = listaUrisSelecionadas.mapIndexedNotNull { indice, uri ->
                    converterUriParaMultipart(uri, "anexos", indice)
                }

                // 3. Chamada ao endpoint
                val apiService = RetrofitClient.getApiService(this@NovoChamadoActivity)
                val response = apiService.criarSolicitacao(
                    tipoId = texto(dto.tipoId),
                    localizacaoProblema = texto(dto.localizacaoProblema),
                    descricao = texto(dto.descricao),
                    numeroPatrimonio = dto.numeroPatrimonio?.let { texto(it) },
                    localId = dto.localId?.let { texto(it) },
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
     * Prepara a imagem escolhida para envio:
     * - reduz para no máximo 1600 px no maior lado e salva em JPEG com qualidade 80
     *   (uma foto de câmera de 3–8 MB fica com algumas centenas de KB, abaixo do limite do servidor);
     * - o ImageDecoder já aplica a rotação da foto (EXIF), então ela não chega deitada;
     * - se a compressão falhar, envia o arquivo original;
     * - o índice no nome evita que duas imagens gerem o mesmo arquivo temporário.
     * Roda fora da thread principal (Dispatchers.IO) porque decodificar imagem é pesado.
     */
    private suspend fun converterUriParaMultipart(uri: Uri, paramName: String, indice: Int): MultipartBody.Part? =
        withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, "foto_${System.currentTimeMillis()}_$indice.jpg")
                if (comprimirImagem(uri, file)) {
                    val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    return@withContext MultipartBody.Part.createFormData(paramName, file.name, requestFile)
                }

                // Plano B: envia a imagem original, sem compressão
                val tipo = contentResolver.getType(uri) ?: "image/jpeg"
                val extensao = MimeTypeMap.getSingleton().getExtensionFromMimeType(tipo) ?: "jpg"
                val original = File(cacheDir, "foto_${System.currentTimeMillis()}_$indice.$extensao")
                val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
                inputStream.use { entrada ->
                    original.outputStream().use { saida -> entrada.copyTo(saida) }
                }
                MultipartBody.Part.createFormData(paramName, original.name, original.asRequestBody(tipo.toMediaTypeOrNull()))
            } catch (e: Exception) {
                null
            }
        }

    private fun comprimirImagem(uri: Uri, destino: File): Boolean {
        return try {
            val source = ImageDecoder.createSource(contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val largura = info.size.width
                val altura = info.size.height
                val maiorLado = maxOf(largura, altura)
                if (maiorLado > LADO_MAXIMO_PX) {
                    val escala = LADO_MAXIMO_PX.toFloat() / maiorLado
                    decoder.setTargetSize((largura * escala).toInt(), (altura * escala).toInt())
                }
                // Bitmap em memória comum (não de GPU), necessário para comprimir
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            destino.outputStream().use { saida ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, saida)
            }
            bitmap.recycle()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- Assistente de IA ----

    /** Mostra o botão "Organizar com IA" só se o servidor estiver com a IA configurada. */
    private fun verificarIaDisponivel() {
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@NovoChamadoActivity).statusIa()
                val disponivel = response.isSuccessful && response.body()?.disponivel == true
                binding.btnOrganizarIa.visibility = if (disponivel) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                // Sem IA o app funciona normalmente; o botão só fica escondido
                binding.btnOrganizarIa.visibility = View.GONE
            }
        }
    }

    private fun organizarDescricaoComIa() {
        val texto = binding.etDescricao.text.toString().trim()
        if (texto.length < 5) {
            binding.etDescricao.error = "Escreva ou dite o problema primeiro"
            return
        }
        val localizacao = binding.etLocalizacao.text.toString().trim().ifEmpty { null }

        binding.btnOrganizarIa.isEnabled = false
        binding.btnOrganizarIa.text = "Organizando..."

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getApiService(this@NovoChamadoActivity)
                    .organizarDescricao(OrganizarDescricaoRequestDTO(texto, localizacao))
                val sugestao = response.body()
                if (response.isSuccessful && sugestao != null) {
                    mostrarSugestaoIa(sugestao)
                } else {
                    val mensagem = lerMensagemErro(response.errorBody()?.string())
                        ?: "Não foi possível organizar agora (código ${response.code()})."
                    Toast.makeText(this@NovoChamadoActivity, mensagem, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@NovoChamadoActivity, "Falha de conexão com o servidor.", Toast.LENGTH_LONG).show()
            } finally {
                binding.btnOrganizarIa.isEnabled = true
                binding.btnOrganizarIa.text = "✨ Organizar com IA"
            }
        }
    }

    /** A sugestão só é aplicada se o usuário aceitar; ele ainda pode editar depois. */
    private fun mostrarSugestaoIa(sugestao: OrganizarDescricaoResponseDTO) {
        val descricao = sugestao.descricao?.trim().orEmpty()
        val tipo = tiposDisponiveis.firstOrNull { it.id == sugestao.tipoSugeridoId }

        val mensagem = buildString {
            append(descricao.ifEmpty { "(sem sugestão de texto)" })
            if (tipo != null) append("\n\nTipo sugerido: ").append(tipo.nome)
            if (sugestao.pareceUrgente == true) {
                append("\n\n⚠️ Parece urgente")
                sugestao.motivoUrgencia?.let { append(": ").append(it) }
                append("\nSe houver risco imediato, avise também a equipe de manutenção pessoalmente.")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Sugestão da IA")
            .setMessage(mensagem)
            .setPositiveButton("Usar sugestão") { _, _ ->
                if (descricao.isNotEmpty()) {
                    binding.etDescricao.setText(descricao)
                    binding.etDescricao.setSelection(descricao.length)
                }
                if (tipo != null && tiposCarregados) {
                    binding.spinnerTipo.setSelection(tiposDisponiveis.indexOf(tipo))
                }
            }
            .setNegativeButton("Manter o meu", null)
            .show()
    }

    private fun lerMensagemErro(corpo: String?): String? {
        if (corpo.isNullOrBlank()) return null
        return try {
            JSONObject(corpo).optString("erro").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        binding.progressBar.visibility = if (carregando) View.VISIBLE else View.GONE
        binding.btnEnviar.isEnabled = !carregando && tiposCarregados
        binding.btnSelecionarFotos.isEnabled = !carregando
    }

    companion object {
        private const val LADO_MAXIMO_PX = 1600
        private const val QUALIDADE_JPEG = 80
    }
}
