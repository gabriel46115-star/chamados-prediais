package com.br.fiec.chamados.data.model

// Espelha IaStatusDTO do back-end (GET /api/v1/ia/status).
// disponivel = false quando o servidor está sem chave de IA configurada.
data class IaStatusDTO(
    val disponivel: Boolean
)

// Espelha OrganizarDescricaoRequestDTO (POST /api/v1/ia/organizar-descricao)
data class OrganizarDescricaoRequestDTO(
    val texto: String,
    val localizacao: String? = null
)

// Espelha OrganizarDescricaoResponseDTO. É só uma sugestão: nada é salvo no servidor,
// o usuário decide se usa ou não. pareceUrgente é apenas informativo.
data class OrganizarDescricaoResponseDTO(
    val descricao: String?,
    val tipoSugeridoId: String?,
    val tipoSugeridoNome: String?,
    val pareceUrgente: Boolean?,
    val motivoUrgencia: String?
)
