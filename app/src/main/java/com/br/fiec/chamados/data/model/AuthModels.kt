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

// Espelha TokenResponseDTO do backend
data class TokenResponseDTO(
    val token: String
)

// Espelha UserMeDTO do backend (GET /api/v1/users/me)
data class UserMeDTO(
    val email: String,
    val nome: String
)
