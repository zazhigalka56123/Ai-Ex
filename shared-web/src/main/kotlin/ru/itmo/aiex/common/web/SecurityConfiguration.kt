package ru.itmo.aiex.common.web

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import ru.itmo.aiex.security.JwtSecurityConfiguration
import tools.jackson.databind.json.JsonMapper
import java.net.URI

@Configuration(proxyBeanMethods = false)
@Import(JwtSecurityConfiguration::class)
class SecurityConfiguration(private val jsonMapper: JsonMapper) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }.formLogin { it.disable() }.httpBasic { it.disable() }.logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .authorizeHttpRequests {
                it.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers(
                        "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/", "/index.html", "/app.js", "/app.css", "/favicon.ico",
                    ).permitAll()
                    .requestMatchers("/internal/**").hasAuthority("SCOPE_internal")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/tags",
                        "/api/v1/tags/**",
                        "/api/v1/specializations",
                        "/api/v1/specializations/**",
                        "/api/v1/specialists",
                        "/api/v1/specialists/**",
                    ).permitAll()
                    .requestMatchers("/api/**").hasAuthority("SCOPE_user")
                    .anyRequest().denyAll()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { request, response, _ -> problem(request, response, HttpStatus.UNAUTHORIZED) }
                it.accessDeniedHandler { request, response, _ -> problem(request, response, HttpStatus.FORBIDDEN) }
            }
            .oauth2ResourceServer {
                it.jwt {}
                it.accessDeniedHandler { request, response, _ -> problem(request, response, HttpStatus.FORBIDDEN) }
                it.authenticationEntryPoint { request, response, _ -> problem(request, response, HttpStatus.UNAUTHORIZED) }
            }
        return http.build()
    }

    private fun problem(request: HttpServletRequest, response: HttpServletResponse, status: HttpStatus) {
        response.status = status.value()
        response.contentType = "application/problem+json"
        val unauthorized = status == HttpStatus.UNAUTHORIZED
        if (unauthorized) response.setHeader("WWW-Authenticate", "Bearer")
        val code = if (unauthorized) "UNAUTHENTICATED" else "FORBIDDEN"
        val detail = if (unauthorized) "Требуется действительный Bearer JWT" else "Недостаточно прав"
        val problem = ProblemDetail.forStatusAndDetail(status, detail).apply {
            instance = URI.create(request.requestURI)
            setProperty("code", code)
            setProperty("traceId", request.getAttribute(TraceIdFilter.MDC_KEY))
        }
        jsonMapper.writeValue(response.outputStream, problem)
    }
}
