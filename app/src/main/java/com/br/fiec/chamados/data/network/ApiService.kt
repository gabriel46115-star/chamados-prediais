package com.br.fiec.chamados.data.network

import com.br.fiec.chamados.data.model.LoginRequestDTO
import com.br.fiec.chamados.data.model.PageResponse
import com.br.fiec.chamados.data.model.RegisterRequestDTO
import com.br.fiec.chamados.data.model.SolicitacaoResponseDTO
import com.br.fiec.chamados.data.model.TokenResponseDTO
import com.br.fiec.chamados.data.model.UserMeDTO
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    // ---- Auth (rotas públicas, não exigem token) ----

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDTO): Response<TokenResponseDTO>

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDTO): Response<Unit>

    // ---- Users (rotas protegidas, exigem token) ----

    @GET("api/v1/users/me")
    suspend fun getMe(): Response<UserMeDTO>

    // ---- Solicitacao / Chamados (rotas protegidas) ----
    // Hoje o backend só tem busca; criar/atualizar status ainda não existem no Kipper.

    @GET("api/v1/solicitacoes/search")
    suspend fun searchSolicitacoes(
        @Query("termo") termo: String? = null,
        @Query("status") status: String? = null,
        @Query("prioridade") prioridade: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10
    ): Response<PageResponse<SolicitacaoResponseDTO>>
}