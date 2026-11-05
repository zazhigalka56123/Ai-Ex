package ru.itmo.aiex.iam.service

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.error.UnauthenticatedException
import ru.itmo.aiex.iam.dto.UserResponse
import ru.itmo.aiex.iam.dto.toResponse
import ru.itmo.aiex.iam.repository.UserRepository
import ru.itmo.aiex.security.JwtTokens
import java.util.UUID

@Service
class AuthService(private val users: UserRepository, private val passwords: PasswordEncoder, private val tokens: JwtTokens) {
    private val dummyHash = passwords.encode(UUID.randomUUID().toString())

    @Transactional(readOnly = true)
    fun login(email: String, password: String): TokenResponse {
        val user = users.findByEmail(email.trim().lowercase())
        val hash = user?.passwordHash?.takeUnless { it == "!" } ?: dummyHash
        val matches = password.toByteArray(Charsets.UTF_8).size <= 72 && passwords.matches(password, hash)
        if (!matches || user == null || !user.isActive || user.passwordHash == "!") {
            throw UnauthenticatedException("Неверный email или пароль")
        }
        return TokenResponse(tokens.userToken(user.id, user.roleCodes), "Bearer", JwtTokens.USER_TTL_SECONDS, user.toResponse())
    }
}

data class TokenResponse(val accessToken: String, val tokenType: String, val expiresIn: Long, val user: UserResponse)
