package com.br.fiec.chamados.data.network

import android.content.Context
import com.br.fiec.chamados.data.TokenManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // 10.0.2.2 é o endereço que o EMULADOR usa para enxergar o "localhost" da máquina host.
    // Se um dia testar em celular físico na mesma rede Wi-Fi, troque pelo IP local do PC
    // (ex: "http://192.168.0.10:8080/") e libere a porta 8080 no firewall.
    private const val BASE_URL = "http://localhost:8080/"

    private var retrofit: Retrofit? = null

    fun getInstance(context: Context): Retrofit {
        if (retrofit == null) {
            val tokenManager = TokenManager(context.applicationContext)

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(tokenManager))
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