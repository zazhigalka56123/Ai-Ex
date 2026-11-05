package ru.itmo.aiex.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwtException
import ru.itmo.aiex.common.security.RoleCode
import java.time.Instant
import java.util.UUID

class JwtTokensTest {
    private val config = JwtSecurityConfiguration()
    private val key = config.jwtKey("test-only-jwt-secret-at-least-32-bytes")
    private val encoder = config.jwtEncoder(key)
    private val decoder = config.jwtDecoder(key)
    private val tokens = JwtTokens(encoder)

    @Test
    fun `пользовательский и служебный токены имеют разные области доступа`() {
        val user = UUID.randomUUID()
        val jwt = decoder.decode(tokens.userToken(user, setOf(RoleCode.ADMIN)))
        assertThat(jwt.subject).isEqualTo(user.toString())
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("user")
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN")
        assertThat(decoder.decode(tokens.serviceToken()).getClaimAsString("scope")).isEqualTo("internal")
    }

    @Test
    fun `поддельная подпись и поврежденный токен отклоняются`() {
        val other = JwtTokens(config.jwtEncoder(config.jwtKey("different-secret-at-least-32-bytes-long")))
        for (token in listOf(other.userToken(UUID.randomUUID(), setOf(RoleCode.ADMIN)), "invalid.jwt.token")) {
            assertThatThrownBy { decoder.decode(token) }.isInstanceOf(JwtException::class.java)
        }
    }

    @Test
    fun `истекший токен и неверные issuer audience subject scope отклоняются`() {
        val now = Instant.now()
        val variants = listOf<(JwtClaimsSet.Builder) -> Unit>(
            { it.issuedAt(now.minusSeconds(600)).expiresAt(now.minusSeconds(120)) },
            { it.issuer("another-issuer") },
            { it.audience(listOf("another-api")) },
            { it.subject("not-a-uuid") },
            { it.claim("scope", "admin") },
            { it.claim("scope", "internal") },
            { it.claims { claims -> claims.remove("exp") } },
        )
        variants.forEach { customize ->
            val claims = JwtClaimsSet.builder().issuer(JwtTokens.ISSUER).audience(listOf(JwtTokens.AUDIENCE))
                .subject(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("scope", "user")
            customize(claims)
            val token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).tokenValue
            assertThatThrownBy { decoder.decode(token) }.isInstanceOf(JwtException::class.java)
        }
    }

    @Test
    fun `короткий секрет не принимается`() {
        assertThatThrownBy { config.jwtKey("short") }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
