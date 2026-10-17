package com.br.fiec.chamados.data.model

// Espelha LoginRequestDTO do backend (features.auth.models.dto)
data class LoginRequestDTO(
    val email: String,
    val password: String
)

// Espelha RegisterRequestDTO do backend
data class RegisterRequestDTO(
    val nome: String,
    val email: String,
    val password: String,
    val fcmToken: String? = null
)

// Espelha TokenResponseDTO do backend — devolvido tanto pelo login normal
// quanto pelo login com Google
data class TokenResponseDTO(
    val token: String
)

// Espelha GoogleTokenRequestDTO do backend (features.auth.models.dto) — usado no
// POST /api/v1/auth/google, carregando o idToken emitido pelo Google
data class GoogleTokenRequestDTO(
    val idToken: String
)

// Espelha UserMeDTO do backend (GET /api/v1/users/me).
// role: ADMIN_ROLE, SUPERVISOR_ROLE, FUNCIONARIO_ROLE ou USER_ROLE
data class UserMeDTO(
    val id: String?,
    val email: String,
    val nome: String,
    val role: String?
)

// Espelha FcmTokenRequestDTO do backend (PATCH /api/v1/users/me/fcm-token)
data class FcmTokenRequestDTO(
    val fcmToken: String
)
