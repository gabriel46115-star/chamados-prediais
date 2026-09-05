package com.br.fiec.chamados.ui.chamados

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.br.fiec.chamados.R
import com.br.fiec.chamados.data.network.RetrofitClient
import kotlinx.coroutines.launch

class ListaChamadosActivity : AppCompatActivity() {

    private lateinit var rvChamados: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvVazioOuErro: TextView
    private val adapter = ChamadoAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_chamados)

        rvChamados = findViewById(R.id.rvChamados)
        progressBar = findViewById(R.id.progressBar)
        tvVazioOuErro = findViewById(R.id.tvVazioOuErro)

        rvChamados.layoutManager = LinearLayoutManager(this)
        rvChamados.adapter = adapter
        // Divisor simples entre os itens
        rvChamados.addItemDecoration(
            androidx.recyclerview.widget.DividerItemDecoration(
                this,
                LinearLayoutManager.VERTICAL
            )
        )

        carregarChamados()
    }

    private fun carregarChamados() {
        mostrarCarregando(true)

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getApiService(this@ListaChamadosActivity)
                // Sem filtros por enquanto: traz os chamados mais recentes (o backend
                // já ordena por createdAt e pagina de 20 em 20)
                val response = api.searchSolicitacoes()

                if (response.isSuccessful && response.body() != null) {
                    val pagina = response.body()!!
                    if (pagina.content.isEmpty()) {
                        mostrarMensagem("Nenhum chamado encontrado.")
                    } else {
                        adapter.atualizarLista(pagina.content)
                        rvChamados.visibility = android.view.View.VISIBLE
                        tvVazioOuErro.visibility = android.view.View.GONE
                    }
                } else if (response.code() == 401 || response.code() == 403) {
                    // Token expirado (dura 30 min) ou inválido
                    mostrarMensagem("Sua sessão expirou. Faça login novamente.")
                } else {
                    mostrarMensagem("Não foi possível carregar os chamados (erro ${response.code()}).")
                }
            } catch (e: Exception) {
                mostrarMensagem("Falha de conexão. Verifique se o backend está no ar.")
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        progressBar.visibility = if (carregando) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun mostrarMensagem(mensagem: String) {
        tvVazioOuErro.text = mensagem
        tvVazioOuErro.visibility = android.view.View.VISIBLE
        rvChamados.visibility = android.view.View.GONE
    }
}
