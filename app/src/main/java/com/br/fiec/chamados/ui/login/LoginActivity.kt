package com.br.fiec.chamados.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.br.fiec.chamados.BuildConfig
import com.br.fiec.chamados.data.TokenManager
import com.br.fiec.chamados.data.model.LoginRequestDTO
import com.br.fiec.chamados.data.model.GoogleTokenRequestDTO
import com.br.fiec.chamados.data.network.AuthInterceptor
import com.br.fiec.chamados.data.network.FcmTokenSync
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.databinding.ActivityLoginBinding
import com.br.fiec.chamados.ui.chamados.ListaChamadosActivity
import com.br.fiec.chamados.ui.util.EspacamentoBarras
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Login por e-mail/senha (backend próprio) e login social com Google, este
 * último via Credential Manager (API recomendada pelo Google, substituindo o
 * antigo GoogleSignInClient/play-services-auth).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var tokenManager: TokenManager

    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EspacamentoBarras.aplicar(binding.root, incluirTeclado = true)

        tokenManager = TokenManager(this)
        credentialManager = CredentialManager.create(this)

        // Se já tem token salvo e ainda válido, pula direto pra listagem
        if (tokenManager.isLoggedIn()) {
            irParaListaChamados()
            return
        }

        // Aberta pelo AuthInterceptor porque o token do servidor expirou
        if (intent.getBooleanExtra(EXTRA_SESSAO_EXPIRADA, false)) {
            mostrarErro("Sua sessão expirou. Faça login novamente.")
        }

        binding.btnEntrar.setOnClickListener {
            realizarLogin()
        }

        binding.btnEntrarComGoogle.setOnClickListener {
            limparErro()
            iniciarLoginComGoogle()
        }
    }

    private fun realizarLogin() {
        val email = binding.etEmail.text.toString().trim()
        val senha = binding.etSenha.text.toString()

        if (email.isEmpty() || senha.isEmpty()) {
            mostrarErro("Preencha e-mail e senha.")
            return
        }

        mostrarCarregando(true)

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@LoginActivity)
                val response = api.login(LoginRequestDTO(email = email, password = senha))

                if (response.isSuccessful && response.body() != null) {
                    tokenManager.saveToken(response.body()!!.token)
                    irParaListaChamados()
                } else {
                    val erroMsg = extrairMensagemErro(response.errorBody()?.string())
                        ?: "E-mail ou senha inválidos."
                    mostrarErro(erroMsg)
                }
            } catch (e: Exception) {
                mostrarErro("Não foi possível conectar ao servidor (${e.localizedMessage ?: "Erro de rede"}). Verifique se o backend está rodando.")
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    /**
     * Etapa 3 do roteiro: dispara a requisição de credencial via Credential Manager.
     * O serverClientId precisa ser o Client ID do tipo WEB (não o Android): ele vira o
     * campo "aud" do idToken, e o backend só aceita o token se esse "aud" for igual ao
     * app.google.web-client-id configurado no Spring Boot. O valor vem do BuildConfig,
     * carregado a partir de local.properties (nunca commitado no repositório).
     * O app é identificado pelo Android Client ID (package + SHA-1) cadastrado no
     * mesmo projeto do Google Cloud.
     */
    private fun iniciarLoginComGoogle() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // mostra todas as contas do dispositivo, não só as já usadas antes
            .setServerClientId(BuildConfig.WEB_CLIENT_ID)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        mostrarCarregando(true)

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                processarResultadoCredencial(result.credential)
            } catch (e: GetCredentialCancellationException) {
                // Usuário fechou o seletor de contas — não é erro, apenas cancela silenciosamente
                mostrarCarregando(false)
            } catch (e: NoCredentialException) {
                mostrarCarregando(false)
                mostrarErro("Nenhuma conta Google encontrada neste dispositivo. Adicione uma conta em Configurações > Contas.")
            } catch (e: GetCredentialCustomException) {
                mostrarCarregando(false)
                mostrarErro("Falha ao abrir o seletor de contas (${e.type}). Veja o Logcat para detalhes.")
            } catch (e: GetCredentialException) {
                mostrarCarregando(false)
                mostrarErro("Não foi possível fazer login com Google: ${e.message}")
            }
        }
    }

    /**
     * Etapa 3, item 3: extrai o idToken do tipo GoogleIdTokenCredential.
     * A credencial chega como CustomCredential genérica; é preciso conferir o
     * type antes de converter, como a própria documentação do Credential Manager recomenda.
     */
    private fun processarResultadoCredencial(credential: androidx.credentials.Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                autenticarNoBackend(googleIdTokenCredential.idToken)
            } catch (e: GoogleIdTokenParsingException) {
                mostrarCarregando(false)
                mostrarErro("Token do Google em formato inesperado.")
            }
        } else {
            mostrarCarregando(false)
            mostrarErro("Tipo de credencial inesperado recebido.")
        }
    }

    /**
     * Envia o idToken do Google direto para o backend (POST /api/v1/auth/google).
     * O backend valida o token com o GoogleIdTokenVerifier (assinatura do Google,
     * expiração e audiência == Web Client ID), busca o usuário pelo e-mail e devolve
     * o JWT da própria aplicação — esse é o token salvo e usado nas demais requisições.
     */
    private fun autenticarNoBackend(googleIdToken: String) {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@LoginActivity)
                val response = api.loginComGoogle(GoogleTokenRequestDTO(idToken = googleIdToken))

                if (response.isSuccessful && response.body() != null) {
                    tokenManager.saveToken(response.body()!!.token)
                    irParaListaChamados()
                } else if (response.code() == 401) {
                    // O backend devolve { "erro": "..." } explicando o motivo (token inválido, etc.)
                    mostrarErro(extrairMensagemErro(response.errorBody()?.string())
                        ?: "Login com Google recusado pelo servidor.")
                } else {
                    mostrarErro("Backend recusou o login com Google (erro ${response.code()}).")
                }
            } catch (e: Exception) {
                mostrarErro("Não foi possível conectar ao servidor (${e.localizedMessage ?: "Erro de rede"}). Verifique se o backend está rodando.")
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    /** Lê o campo "erro" do JSON de erro do backend (GlobalExceptionHandler). */
    private fun extrairMensagemErro(corpo: String?): String? {
        if (corpo.isNullOrBlank()) return null
        return try {
            JSONObject(corpo).optString("erro").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun irParaListaChamados() {
        AuthInterceptor.novaSessaoIniciada()
        // Vincula este aparelho ao usuário no backend, para receber as notificações push
        FcmTokenSync.sincronizar(this)

        // CLEAR_TOP + SINGLE_TOP: se a lista já estiver aberta por trás, ela é reaproveitada
        // (recebe o chamadoId em onNewIntent) em vez de abrir uma segunda cópia
        val destino = Intent(this, ListaChamadosActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        // Com o app fechado, tocar na notificação abre esta tela (a de entrada) trazendo o
        // "chamadoId" enviado pelo backend; ele é repassado para a lista abrir o chamado.
        intent.getStringExtra(EXTRA_CHAMADO_ID)?.let { destino.putExtra(EXTRA_CHAMADO_ID, it) }
        startActivity(destino)
        finish()
    }

    private fun mostrarCarregando(carregando: Boolean) {
        binding.progressBar.visibility = if (carregando) android.view.View.VISIBLE else android.view.View.GONE
        binding.btnEntrar.isEnabled = !carregando
        binding.btnEntrarComGoogle.isEnabled = !carregando
    }

    private fun mostrarErro(mensagem: String) {
        binding.tvErro.text = mensagem
        binding.tvErro.visibility = android.view.View.VISIBLE
    }

    private fun limparErro() {
        binding.tvErro.visibility = android.view.View.GONE
    }

    companion object {
        const val EXTRA_SESSAO_EXPIRADA = "EXTRA_SESSAO_EXPIRADA"

        /** Chave do id do chamado no bloco "data" das notificações enviadas pelo backend. */
        const val EXTRA_CHAMADO_ID = "chamadoId"
    }
}
