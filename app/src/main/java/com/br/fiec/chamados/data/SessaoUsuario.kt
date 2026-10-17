package com.br.fiec.chamados.data

import com.br.fiec.chamados.data.model.UserMeDTO

/**
 * Perfil do usuário logado (GET /api/v1/users/me), guardado em memória enquanto o app
 * está aberto, para que as telas não precisem consultar o backend a cada abertura.
 *
 * Escopo do app (ver seção 2.8 do relatório): no celular existem só dois perfis.
 * FUNCIONARIO_ROLE, SUPERVISOR_ROLE e ADMIN_ROLE usam o painel de funcionário;
 * USER_ROLE é o usuário comum.
 */
object SessaoUsuario {

    private val PAPEIS_EQUIPE = setOf("FUNCIONARIO_ROLE", "SUPERVISOR_ROLE", "ADMIN_ROLE")

    var usuario: UserMeDTO? = null

    /** Funcionário de manutenção (ou supervisor/admin, que no app têm o mesmo painel). */
    val ehEquipe: Boolean
        get() = usuario?.role?.let { it in PAPEIS_EQUIPE } == true

    fun limpar() {
        usuario = null
    }
}
