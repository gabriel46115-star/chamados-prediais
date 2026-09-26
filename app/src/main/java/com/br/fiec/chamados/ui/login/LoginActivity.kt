package com.br.fiec.chamados.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
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
import com.br.fiec.chamados.R
import com.br.fiec.chamados.data.TokenManager
import com.br.fiec.chamados.data.model.LoginRequestDTO
import com.br.fiec.chamados.data.model.TokenRequestDTO
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.ui.chamados.ListaChamadosActivity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

/**
 * Login por e-mail/senha (backend próprio) e login social com Google, este
 * último via Credential Manager (API recomendada pelo Google, substituindo o
 * antigo GoogleSignInClient/play-services-auth).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etSenha: EditText
    private lateinit var btnEntrar: Button
    private lateinit var btnEntrarComGoogle: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvErro: TextView
    private lateinit var tokenManager: TokenManager

    private lateinit var credentialManager: CredentialManager
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tokenManager = TokenManager(this)
        firebaseAuth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        etEmail = findViewById(R.id.etEmail)
        etSenha = findViewById(R.id.etSenha)
        btnEntrar = findViewById(R.id.btnEntrar)
        btnEntrarComGoogle = findViewById(R.id.btnEntrarComGoogle)
        progressBar = findViewById(R.id.progressBar)
        tvErro = findViewById(R.id.tvErro)

        // Se já tem token salvo, pula direto pra listagem
        if (tokenManager.isLoggedIn()) {
            irParaListaChamados()
            return
        }

        btnEntrar.setOnClickListener {
            realizarLogin()
        }

        btnEntrarComGoogle.setOnClickListener {
            limparErro()
            iniciarLoginComGoogle()
        }
    }

    private fun realizarLogin() {
        val email = etEmail.text.toString().trim()
        val senha = etSenha.text.toString()

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
                    mostrarErro("E-mail ou senha inválidos.")
                }
            } catch (e: Exception) {
                mostrarErro("Não foi possível conectar ao servidor. Verifique se o backend está rodando.")
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    /**
     * Etapa 3 do roteiro: dispara a requisição de credencial via Credential Manager.
     * O serverClientId precisa ser o Client ID do tipo WEB (não o Android), o mesmo
     * exigido pelo GoogleAuthProvider mais adiante — por isso ele vem do BuildConfig,
     * carregado a partir de local.properties (nunca commitado no repositório).
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
                autenticarNoFirebase(googleIdTokenCredential.idToken)
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
     * Passo intermediário do login social: troca a credencial do Google por uma
     * sessão do Firebase. O backend não valida o token do Google diretamente —
     * ele valida um token do Firebase (via Firebase Admin SDK).
     */
    private fun autenticarNoFirebase(googleIdToken: String) {
        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)

        firebaseAuth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                authResult.user?.getIdToken(false)
                    ?.addOnSuccessListener { tokenResult ->
                        val firebaseIdToken = tokenResult.token
                        if (firebaseIdToken != null) {
                            autenticarNoBackend(firebaseIdToken)
                        } else {
                            mostrarCarregando(false)
                            mostrarErro("Não foi possível obter o token do Firebase.")
                        }
                    }
                    ?.addOnFailureListener {
                        mostrarCarregando(false)
                        mostrarErro("Falha ao obter token do Firebase: ${it.message}")
                    }
            }
            .addOnFailureListener {
                mostrarCarregando(false)
                mostrarErro("Falha ao autenticar no Firebase: ${it.message}")
            }
    }

    /** Envia o ID Token do Firebase para o backend e recebe o JWT da aplicação. */
    private fun autenticarNoBackend(firebaseIdToken: String) {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@LoginActivity)
                val response = api.loginComFirebase(TokenRequestDTO(token = firebaseIdToken))

                if (response.isSuccessful && response.body() != null) {
                    tokenManager.saveToken(response.body()!!.token)
                    irParaListaChamados()
                } else {
                    mostrarErro("Backend recusou o login com Google (erro ${response.code()}).")
                }
            } catch (e: Exception) {
                mostrarErro("Não foi possível conectar ao servidor. Verifique se o backend está rodando.")
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun irParaListaChamados() {
        startActivity(Intent(this, ListaChamadosActivity::class.java))
        finish()
    }

    private fun mostrarCarregando(carregando: Boolean) {
        progressBar.visibility = if (carregando) android.view.View.VISIBLE else android.view.View.GONE
        btnEntrar.isEnabled = !carregando
        btnEntrarComGoogle.isEnabled = !carregando
    }

    private fun mostrarErro(mensagem: String) {
        tvErro.text = mensagem
        tvErro.visibility = android.view.View.VISIBLE
    }

    private fun limparErro() {
        tvErro.visibility = android.view.View.GONE
    }
}
