package com.br.fiec.chamados.data.model

// Espelha StatusSolicitacao do backend (versão V4)
enum class StatusSolicitacao(val rotulo: String) {
    ABERTO("Aberto"),
    EM_ANDAMENTO("Em andamento"),
    AGUARDANDO_ASSISTENCIA_EXTERNA("Aguardando assistência externa"),
    CONCLUIDO("Concluído"),
    FINALIZADA("Finalizada"),
    REJEITADA("Rejeitada"),
    CANCELADO("Cancelado")
}

// Espelha PrioridadeSolicitacao do backend (versão V4)
enum class PrioridadeSolicitacao(val rotulo: String) {
    BAIXA("Baixa"),
    MEDIA("Média"),
    ALTA("Alta"),
    URGENTE("Urgente")
}

// O Gson não chama construtores: se o backend mandar um valor de enum que o app
// ainda não conhece, o campo chega como null mesmo sendo declarado não-nulo.
// Por isso status/prioridade são anuláveis e a tela usa estas funções para exibir.
fun StatusSolicitacao?.rotuloOuPadrao(): String = this?.rotulo ?: "Status desconhecido"
fun PrioridadeSolicitacao?.rotuloOuPadrao(): String = this?.rotulo ?: "Não definida"

// Espelha a entidade TipoChamado devolvida por GET /api/v1/admin/tipos.
// Os tipos agora são cadastrados no banco (com SLA), não mais um enum fixo.
data class TipoChamadoDTO(
    val id: String,
    val nome: String,
    val icone: String?,
    val slaHoras: Int?
) {
    // Usado pelo ArrayAdapter do Spinner para exibir o item
    override fun toString(): String = if (icone.isNullOrBlank()) nome else "$icone $nome"
}

// Espelha CreateSolicitacaoRequestDTO do backend (versão V4)
data class CreateSolicitacaoRequestDTO(
    val tipoId: String,
    val localizacaoProblema: String,
    val descricao: String,
    val numeroPatrimonio: String? = null,
    val localId: String? = null
)

// Espelha AnexoResponseDTO do backend. A url é relativa (ex: "/uploads/...");
// para exibir a imagem é preciso concatenar com a BASE_URL.
data class AnexoResponseDTO(
    val id: String,
    val nomeArquivo: String,
    val url: String,
    val createdAt: String?
)

// Espelha AjudanteDTO do backend
data class AjudanteDTO(
    val id: String,
    val nome: String?
)

// Espelha SolicitacaoResponseDTO do backend (versão V4).
// UUIDs e LocalDateTime chegam como String no JSON.
data class SolicitacaoResponseDTO(
    val id: String,
    val titulo: String,
    val descricao: String,
    val status: StatusSolicitacao?,
    val prioridade: PrioridadeSolicitacao?,
    val tipoId: String?,
    val tipoNome: String?,
    val tipoIcone: String?,
    val localId: String?,
    val localNome: String?,
    val setorId: String?,
    val setorNome: String?,
    val numeroPatrimonio: String?,
    val localizacaoProblema: String,
    val urgente: Boolean?,
    val usuarioSolicitanteId: String?,
    val usuarioSolicitanteNome: String?,
    val tecnicoResponsavelId: String?,
    val tecnicoResponsavelNome: String?,
    val ajudantes: List<AjudanteDTO>?,
    val dataAbertura: String?,
    val dataAtualizacao: String?,
    val dataConclusao: String?,
    val dataFinalizacao: String?,
    val motivoRejeicao: String?,
    val anexos: List<AnexoResponseDTO>?
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

// Espelha ComentarioRequestDTO do backend (POST /solicitacoes/{id}/comentarios)
data class ComentarioRequestDTO(
    val texto: String
)

// Espelha ComentarioResponseDTO do backend
data class ComentarioResponseDTO(
    val id: String,
    val texto: String,
    val autorId: String?,
    val autorNome: String?,
    val criadoEm: String?
)

// Espelha MovimentacaoResponseDTO do backend (GET /solicitacoes/{id}/historico).
// Movimentações automáticas (ex: SLA estourado) não têm autor.
data class MovimentacaoResponseDTO(
    val id: String,
    val titulo: String,
    val descricao: String?,
    val autorId: String?,
    val autorNome: String?,
    val criadoEm: String?
)
