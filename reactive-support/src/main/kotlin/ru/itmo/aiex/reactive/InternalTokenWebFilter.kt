package ru.itmo.aiex.reactive

import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import ru.itmo.aiex.common.web.AiExHeaders
import java.security.MessageDigest

/** Пути `/internal/...` доступны только соседним сервисам, передающим общий токен. */
class InternalTokenWebFilter(token: String) : WebFilter {
    private val expected = token.toByteArray()

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        if (!exchange.request.path.value().startsWith("/internal/")) return chain.filter(exchange)
        val supplied = exchange.request.headers.getFirst(AiExHeaders.INTERNAL_TOKEN)?.toByteArray()
        if (supplied == null || !MessageDigest.isEqual(expected, supplied)) {
            exchange.response.statusCode = HttpStatus.FORBIDDEN
            return exchange.response.setComplete()
        }
        return chain.filter(exchange)
    }
}
