package com.br.fiec.chamados.data.network

import android.content.Context
import com.br.fiec.chamados.data.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // Usa "localhost" junto com o túnel do adb: antes de rodar o app, execute
    //     adb reverse tcp:8080 tcp:8080
    // (repetir sempre que o emulador ou o celular reiniciar). Assim a porta 8080 do aparelho
    // aponta para o backend na máquina host, sem mexer em firewall e sem exceção de tráfego
    // HTTP no manifest. Funciona tanto no emulador quanto em celular físico via USB.
    private const val BASE_URL = "http://localhost:8080/"

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