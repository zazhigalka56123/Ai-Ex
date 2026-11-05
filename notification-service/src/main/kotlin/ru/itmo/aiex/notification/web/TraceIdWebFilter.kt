package ru.itmo.aiex.notification.web

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val traceId = exchange.request.headers.getFirst(HEADER)
            ?.takeIf { TRACE_ID_PATTERN.matches(it) } ?: UUID.randomUUID().toString()
        exchange.attributes[CONTEXT_KEY] = traceId
        exchange.response.headers.set(HEADER, traceId)
        return chain.filter(exchange).contextWrite { it.put(CONTEXT_KEY, traceId) }
    }

    companion object {
        const val HEADER = "X-Trace-Id"
        const val CONTEXT_KEY = "traceId"
        private val TRACE_ID_PATTERN = Regex("[A-Za-z0-9-]{8,64}")
    }
}
