package com.br.fiec.chamados.data.network

import com.br.fiec.chamados.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ---- Auth ----

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequestDTO): Response<TokenResponseDTO>

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequestDTO): Response<Unit>

    // Login com Google: envia o idToken do Google e recebe o JWT do servidor
    @POST("api/v1/auth/google")
    suspend fun loginComGoogle(@Body request: GoogleTokenRequestDTO): Response<TokenResponseDTO>

    // ---- Users ----

    @GET("api/v1/users/me")
    suspend fun getMe(): Response<UserMeDTO>

    // ---- Tipos de chamado (cadastrados no banco; leitura liberada a qualquer usuário logado) ----

    @GET("api/v1/admin/tipos")
    suspend fun listarTipos(): Response<List<TipoChamadoDTO>>

    // ---- Solicitacao / Chamados ----

    @GET("api/v1/solicitacoes/search")
    suspend fun searchSolicitacoes(
        @Query("id") id: String? = null,
        @Query("termo") termo: String? = null,
        @Query("status") status: StatusSolicitacao? = null,
        @Query("prioridade") prioridade: PrioridadeSolicitacao? = null,
        @Query("numeroPatrimonio") numeroPatrimonio: String? = null,
        @Query("localizacaoProblema") localizacaoProblema: String? = null,
        @Query("usuarioSolicitanteId") usuarioSolicitanteId: String? = null,
        @Query("tecnicoResponsavelId") tecnicoResponsavelId: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10,
        // Mais recentes primeiro (o padrão do backend é createdAt crescente)
        @Query("sort") sort: String = "createdAt,desc"
    ): Response<PageResponse<SolicitacaoResponseDTO>>

    @GET("api/v1/solicitacoes/{id}")
    suspend fun buscarPorId(
        @Path("id") id: String
    ): Response<SolicitacaoResponseDTO>

    // Comentários do chamado (ordem cronológica, mais antigo primeiro)
    @GET("api/v1/solicitacoes/{id}/comentarios")
    suspend fun listarComentarios(
        @Path("id") id: String
    ): Response<List<ComentarioResponseDTO>>

    @POST("api/v1/solicitacoes/{id}/comentarios")
    suspend fun comentar(
        @Path("id") id: String,
        @Body request: ComentarioRequestDTO
    ): Response<ComentarioResponseDTO>

    // Histórico de movimentações (mais recente primeiro)
    @GET("api/v1/solicitacoes/{id}/historico")
    suspend fun listarHistorico(
        @Path("id") id: String
    ): Response<List<MovimentacaoResponseDTO>>

    // Criação de Chamado com Multipart:
    // "dados" -> RequestBody JSON
    // "anexos" -> Lista de MultipartBody.Part
    @Multipart
    @POST("api/v1/solicitacoes")
    suspend fun criarSolicitacao(
        @Part("dados") dados: RequestBody,
        @Part anexos: List<MultipartBody.Part>? = null
    ): Response<SolicitacaoResponseDTO>

    // Admin / Gestão de Chamados
    @PATCH("api/v1/solicitacoes/{id}/status")
    suspend fun atualizarStatus(
        @Path("id") id: String,
        @Query("status") status: StatusSolicitacao
    ): Response<SolicitacaoResponseDTO>

    @PATCH("api/v1/solicitacoes/{id}/prioridade")
    suspend fun atualizarPrioridade(
        @Path("id") id: String,
        @Query("prioridade") prioridade: PrioridadeSolicitacao
    ): Response<SolicitacaoResponseDTO>

    @PATCH("api/v1/solicitacoes/{id}/tecnico")
    suspend fun atribuirTecnico(
        @Path("id") id: String,
        @Query("tecnicoId") tecnicoId: String
    ): Response<SolicitacaoResponseDTO>
}