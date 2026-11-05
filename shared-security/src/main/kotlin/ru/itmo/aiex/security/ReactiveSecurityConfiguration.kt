package ru.itmo.aiex.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Import(JwtSecurityConfiguration::class)
class ReactiveSecurityConfiguration {
    @Bean
    fun reactiveJwtDecoder(decoder: JwtDecoder): ReactiveJwtDecoder = ReactiveJwtDecoder { token -> Mono.fromCallable { decoder.decode(token) } }

    @Bean
    fun reactiveSecurity(http: ServerHttpSecurity, @Value("\${spring.application.name}") application: String): SecurityWebFilterChain = http
        .csrf { it.disable() }.formLogin { it.disable() }.httpBasic { it.disable() }.logout { it.disable() }
        .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
        .requestCache { it.requestCache(NoOpServerRequestCache.getInstance()) }
        .authorizeExchange {
            it.pathMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .pathMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/fallback",
                    "/", "/index.html", "/app.js", "/app.css", "/favicon.ico").permitAll()
            if (application == "gateway") it.pathMatchers("/internal/**").denyAll()
            else it.pathMatchers("/internal/**").hasAuthority("SCOPE_internal")
            it.pathMatchers(HttpMethod.GET, "/api/v1/tags", "/api/v1/tags/**", "/api/v1/specializations", "/api/v1/specializations/**",
                "/api/v1/specialists", "/api/v1/specialists/**").permitAll()
                .pathMatchers("/api/**").hasAuthority("SCOPE_user").anyExchange().denyAll()
        }
        .exceptionHandling {
            it.authenticationEntryPoint { exchange, _ -> problem(exchange, HttpStatus.UNAUTHORIZED) }
            it.accessDeniedHandler { exchange, _ -> problem(exchange, HttpStatus.FORBIDDEN) }
        }
        .oauth2ResourceServer {
            it.jwt {}
            it.authenticationEntryPoint { exchange, _ -> problem(exchange, HttpStatus.UNAUTHORIZED) }
        }.build()

    private fun problem(exchange: ServerWebExchange, status: HttpStatus): Mono<Void> {
        val unauthorized = status == HttpStatus.UNAUTHORIZED
        val code = if (unauthorized) "UNAUTHENTICATED" else "FORBIDDEN"
        val detail = if (unauthorized) "Требуется действительный Bearer JWT" else "Недостаточно прав"
        exchange.response.statusCode = status
        exchange.response.headers.contentType = MediaType.APPLICATION_PROBLEM_JSON
        if (unauthorized) exchange.response.headers.set("WWW-Authenticate", "Bearer")
        val body = """{"status":${status.value()},"code":"$code","detail":"$detail"}""".toByteArray(Charsets.UTF_8)
        return exchange.response.writeWith(Mono.just(exchange.response.bufferFactory().wrap(body)))
    }
}
