package ru.itmo.aiex.common.web

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import ru.itmo.aiex.security.JwtSecurityConfiguration

@Configuration(proxyBeanMethods = false)
@Import(JwtSecurityConfiguration::class)
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }.formLogin { it.disable() }.httpBasic { it.disable() }.logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }
            .authorizeHttpRequests {
                it.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/", "/index.html", "/app.js", "/app.css", "/favicon.ico").permitAll()
                    .requestMatchers("/internal/**").hasAuthority("SCOPE_internal")
                    .requestMatchers(HttpMethod.GET, "/api/v1/tags", "/api/v1/tags/**", "/api/v1/specializations", "/api/v1/specializations/**",
                        "/api/v1/specialists", "/api/v1/specialists/**").permitAll()
                    .requestMatchers("/api/**").hasAuthority("SCOPE_user")
                    .anyRequest().denyAll()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> problem(response, 401) }
                it.accessDeniedHandler { _, response, _ -> problem(response, 403) }
            }
            .oauth2ResourceServer {
                it.jwt {}
                it.authenticationEntryPoint { _, response, _ -> problem(response, 401) }
            }
        return http.build()
    }

    private fun problem(response: HttpServletResponse, status: Int) {
        response.status = status
        response.characterEncoding = "UTF-8"
        response.contentType = "application/problem+json"
        val unauthorized = status == 401
        if (unauthorized) response.setHeader("WWW-Authenticate", "Bearer")
        val code = if (unauthorized) "UNAUTHENTICATED" else "FORBIDDEN"
        val detail = if (unauthorized) "Требуется действительный Bearer JWT" else "Недостаточно прав"
        response.writer.write("""{"status":$status,"code":"$code","detail":"$detail"}""")
    }
}
