package ru.itmo.aiex.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import feign.RequestInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

@Configuration(proxyBeanMethods = false)
class JwtSecurityConfiguration {
    @Bean
    fun jwtKey(@Value("\${aiex.security.jwt-secret}") secret: String): SecretKeySpec {
        require(secret.toByteArray(Charsets.UTF_8).size >= 32) { "JWT_SECRET должен содержать не менее 32 байт" }
        return SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    }

    @Bean
    fun jwtDecoder(key: SecretKeySpec): JwtDecoder = NimbusJwtDecoder.withSecretKey(key).build().apply {
        setJwtValidator(DelegatingOAuth2TokenValidator(JwtValidators.createDefaultWithIssuer(JwtTokens.ISSUER), OAuth2TokenValidator<Jwt> { jwt ->
            val scope = jwt.getClaimAsString("scope")
            val valid = jwt.expiresAt != null && jwt.audience.orEmpty().contains(JwtTokens.AUDIENCE) &&
                ((scope == "user" && runCatching { UUID.fromString(jwt.subject) }.isSuccess) ||
                    (scope == "internal" && jwt.subject == "service"))
            if (valid) OAuth2TokenValidatorResult.success()
            else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Некорректный JWT", null))
        }))
    }

    @Bean
    fun jwtEncoder(key: SecretKeySpec): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<com.nimbusds.jose.proc.SecurityContext>(key))

    @Bean
    fun jwtTokens(encoder: JwtEncoder): JwtTokens = JwtTokens(encoder)

    @Bean
    fun internalAuthorization(tokens: JwtTokens): RequestInterceptor = RequestInterceptor { request ->
        request.removeHeader("Authorization")
        request.header("Authorization", "Bearer ${tokens.serviceToken()}")
    }
}
