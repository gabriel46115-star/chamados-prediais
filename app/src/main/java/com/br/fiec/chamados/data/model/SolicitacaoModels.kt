package com.br.fiec.chamados.data.model

// Espelha StatusSolicitacao do backend
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

// Espelha TipoSolicitacao do backend
enum class TipoSolicitacao {
    HARDWARE,
    SOFTWARE,
    REDE,
    ACESSO,
    MANUTENCAO,
    OUTROS
}

// Espelha AnexoResponseDTO do backend
data class AnexoResponseDTO(
    val id: String,
    val nomeArquivo: String,
    val url: String
)

// Espelha SolicitacaoResponseDTO do backend
data class SolicitacaoResponseDTO(
    val id: String, // UUID vem como String no JSON
    val titulo: String,
    val descricao: String,
    val status: StatusSolicitacao,
    val prioridade: PrioridadeSolicitacao,
    val tipo: TipoSolicitacao,
    val numeroPatrimonio: String?,
    val localizacaoProblema: String,
    val usuarioSolicitanteId: String?,
    val usuarioSolicitanteNome: String?,
    val tecnicoResponsavelId: String?,
    val tecnicoResponsavelNome: String?,
    val dataAbertura: String, // LocalDateTime vem como String ISO
    val dataFinalizacao: String?,
    val anexos: List<AnexoResponseDTO> = emptyList()
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
