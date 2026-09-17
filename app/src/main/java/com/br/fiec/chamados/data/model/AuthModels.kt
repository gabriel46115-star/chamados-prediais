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
// quanto (agora) pelo login via Firebase/Google
data class TokenResponseDTO(
    val token: String
)

// Espelha TokenRequestDTO do backend (features.user.model.dto) — usado no
// POST /api/v1/users/auth/firebase, carregando o ID Token do Firebase
data class TokenRequestDTO(
    val token: String
)

// Espelha UserMeDTO do backend (GET /api/v1/users/me)
data class UserMeDTO(
    val email: String,
    val nome: String
)