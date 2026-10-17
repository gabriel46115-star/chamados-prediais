package com.br.fiec.chamados.data.network

import android.content.Context
import com.br.fiec.chamados.BuildConfig
import com.br.fiec.chamados.data.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // O endereço vem do local.properties (chave API_BASE_URL), via BuildConfig:
    //   - em casa:  API_BASE_URL=http://localhost:8080/   + adb reverse tcp:8080 tcp:8080
    //   - servidor: API_BASE_URL=http://10.5.36.6:8080/  (celular na mesma rede)
    // Sem a chave, o padrão é localhost. Depois de trocar, fazer Rebuild.
    // O HTTP sem criptografia só é liberado para esses endereços (res/xml/network_security_config.xml).
    val BASE_URL: String = BuildConfig.API_BASE_URL

    /**
     * O backend devolve a URL dos anexos relativa (ex: "/uploads/<chamado>/<arquivo>.jpg").
     * Aqui ela é completada com o endereço do servidor para poder ser carregada.
     */
    fun urlCompleta(caminho: String): String =
        if (caminho.startsWith("http")) caminho else BASE_URL.trimEnd('/') + "/" + caminho.trimStart('/')

    private var retrofit: Retrofit? = null

    fun getInstance(context: Context): Retrofit {
        if (retrofit == null) {
            val tokenManager = TokenManager(context.applicationContext)

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context, tokenManager))
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    fun getApiService(context: Context): ApiService {
        return getInstance(context).create(ApiService::class.java)
    }
}