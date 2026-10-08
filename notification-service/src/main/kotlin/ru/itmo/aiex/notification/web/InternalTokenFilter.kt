package ru.itmo.aiex.notification.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.MessageDigest

@Component
class InternalTokenFilter(@Value("\${aiex.internal-token:ai-ex-local-token}") private val token: String) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (exchange.request.path.value().startsWith("/internal/")) {
            val supplied = exchange.request.headers.getFirst("X-Internal-Token").orEmpty()
            if (!MessageDigest.isEqual(token.toByteArray(), supplied.toByteArray())) {
                exchange.response.statusCode = HttpStatus.FORBIDDEN
                return exchange.response.setComplete()
            }
        }
        return chain.filter(exchange)
    }
}
