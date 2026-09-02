package com.br.fiec.chamados.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.br.fiec.chamados.MainActivity
import com.br.fiec.chamados.R
import com.br.fiec.chamados.data.TokenManager
import com.br.fiec.chamados.data.model.LoginRequestDTO
import com.br.fiec.chamados.data.network.RetrofitClient
import kotlinx.coroutines.launch
import retrofit2.Response

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: EditText
    private lateinit var etSenha: EditText
    private lateinit var btnEntrar: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvErro: TextView
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        tokenManager = TokenManager(this)

        etEmail = findViewById(R.id.etEmail)
        etSenha = findViewById(R.id.etSenha)
        btnEntrar = findViewById(R.id.btnEntrar)
        progressBar = findViewById(R.id.progressBar)
        tvErro = findViewById(R.id.tvErro)

        // Se já tem token salvo, pula direto pra tela principal
        if (tokenManager.isLoggedIn()) {
            irParaMain()
            return
        }

        btnEntrar.setOnClickListener {
            realizarLogin()
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
                    irParaMain()
                } else {
                    // Backend retorna 401/403 quando as credenciais estão erradas
                    mostrarErro("E-mail ou senha inválidos.")
                }
            } catch (e: Exception) {
                // Erro de rede: backend fora do ar, IP errado, sem conexão, etc.
                mostrarErro("Não foi possível conectar ao servidor. Verifique se o backend está rodando.")
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun irParaMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun mostrarCarregando(carregando: Boolean) {
        progressBar.visibility = if (carregando) android.view.View.VISIBLE else android.view.View.GONE
        btnEntrar.isEnabled = !carregando
    }

    private fun mostrarErro(mensagem: String) {
        tvErro.text = mensagem
        tvErro.visibility = android.view.View.VISIBLE
    }
}