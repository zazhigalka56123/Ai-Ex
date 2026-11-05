package ru.itmo.aiex.security

import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import ru.itmo.aiex.common.security.RoleCode
import java.time.Instant
import java.util.UUID

class JwtTokens(private val encoder: JwtEncoder) {
    fun userToken(id: UUID, roles: Set<RoleCode>): String = issue(id.toString(), "user", roles, USER_TTL_SECONDS)

    fun serviceToken(): String = issue("service", "internal", emptySet(), 60)

    private fun issue(subject: String, scope: String, roles: Set<RoleCode>, ttl: Long): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder().issuer(ISSUER).audience(listOf(AUDIENCE)).subject(subject)
            .issuedAt(now).expiresAt(now.plusSeconds(ttl)).id(UUID.randomUUID().toString())
            .claim("scope", scope).claim("roles", roles.map { it.name }).build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    companion object {
        const val ISSUER = "ai-ex-iam"
        const val AUDIENCE = "ai-ex"
        const val USER_TTL_SECONDS = 900L
    }
}
