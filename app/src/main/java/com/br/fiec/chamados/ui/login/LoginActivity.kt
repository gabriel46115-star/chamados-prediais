package com.br.fiec.chamados.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.br.fiec.chamados.R
import com.br.fiec.chamados.data.TokenManager
import com.br.fiec.chamados.data.model.LoginRequestDTO
import com.br.fiec.chamados.data.model.TokenRequestDTO
import com.br.fiec.chamados.data.network.RetrofitClient
import com.br.fiec.chamados.ui.chamados.ListaChamadosActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etSenha: EditText
    private lateinit var btnEntrar: Button
    private lateinit var btnEntrarComGoogle: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvErro: TextView
    private lateinit var tokenManager: TokenManager

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth

    // Substitui o antigo startActivityForResult — recebe o resultado da tela de
    // escolha de conta do Google quando o usuário volta pro app.
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            autenticarNoFirebase(account.idToken)
        } catch (e: ApiException) {
            // Códigos comuns: 12501 = usuário cancelou; 10 = SHA-1/config errada no Firebase Console
            mostrarErro("Login com Google cancelado ou falhou (código ${e.statusCode}).")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tokenManager = TokenManager(this)
        firebaseAuth = FirebaseAuth.getInstance()

        etEmail = findViewById(R.id.etEmail)
        etSenha = findViewById(R.id.etSenha)
        btnEntrar = findViewById(R.id.btnEntrar)
        btnEntrarComGoogle = findViewById(R.id.btnEntrarComGoogle)
        progressBar = findViewById(R.id.progressBar)
        tvErro = findViewById(R.id.tvErro)

        // ATENÇÃO: R.string.default_web_client_id só existe depois que o google-services.json
        // estiver na pasta app/ e o plugin com.google.gms.google-services estiver aplicado.
        // Veja o guia LOGIN_GOOGLE.md para os detalhes de configuração.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

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
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
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
                    val token = response.body()!!.token
                    tokenManager.saveToken(token)
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
     * Passo intermediário do login social: troca a credencial do Google Sign-In
     * por uma sessão do Firebase. O backend não entende o token "cru" do Google —
     * ele só valida tokens do Firebase (via Firebase Admin SDK).
     */
    private fun autenticarNoFirebase(googleIdToken: String?) {
        if (googleIdToken == null) {
            mostrarErro("Não foi possível obter o token do Google.")
            return
        }

        mostrarCarregando(true)
        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)

        firebaseAuth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                // Login no Firebase deu certo. Agora pega o ID Token DO FIREBASE
                // (diferente do ID Token do Google usado acima) para mandar ao backend.
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
