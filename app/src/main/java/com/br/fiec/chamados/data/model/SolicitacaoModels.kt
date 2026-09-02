package com.br.fiec.chamados.data.model

import java.util.UUID

// Espelha StatusSolicitacao do backend (features.solicitacao.model.enums)
enum class StatusSolicitacao {
    ABERTO,
    EM_ANDAMENTO,
    CONCLUIDO,
    CANCELADO
}

// Espelha PrioridadeSolicitacao do backend
enum class PrioridadeSolicitacao {
    BAIXA,
    MEDIA,
    ALTA
}

// Espelha SolicitacaoResponseDTO do backend
data class SolicitacaoResponseDTO(
    val id: String, // UUID vem como String no JSON
    val titulo: String,
    val descricao: String,
    val status: StatusSolicitacao,
    val prioridade: PrioridadeSolicitacao,
    val usuarioSolicitanteId: String?,
    val usuarioSolicitanteNome: String?,
    val tecnicoResponsavelId: String?,
    val tecnicoResponsavelNome: String?,
    val createdAt: String // LocalDateTime vem como String ISO
)

// Espelha a estrutura de paginação do Spring (Page<T>) retornada por /solicitacoes/search
data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int, // página atual (0-indexed)
    val size: Int,
    val last: Boolean
)
