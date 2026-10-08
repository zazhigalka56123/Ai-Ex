package ru.itmo.aiex.remote

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

@Component
@Profile("microservice")
class InternalTokenFilter(@Value("\${aiex.internal-token:ai-ex-local-token}") token: String) : OncePerRequestFilter() {
    private val expected = token.toByteArray()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/internal/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val supplied = request.getHeader(InternalApi.TOKEN_HEADER)?.toByteArray()
        if (supplied == null || !MessageDigest.isEqual(expected, supplied)) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/problem+json"
            response.writer.write("{\"status\":403,\"code\":\"FORBIDDEN\",\"detail\":\"Internal token required\"}")
            return
        }
        filterChain.doFilter(request, response)
    }
}
