package ru.itmo.aiex.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.HexFormat
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val traceId = request.getHeader(AiExHeaders.TRACE_ID)?.takeIf { TRACE_ID_PATTERN.matches(it) } ?: newTraceId()
        MDC.put(MDC_KEY, traceId)
        response.setHeader(AiExHeaders.TRACE_ID, traceId)
        val startedAt = System.nanoTime()
        try {
            filterChain.doFilter(request, response)
        } finally {
            if (request.requestURI.startsWith(ApiPaths.V1)) {
                val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                log.info("{} {} -> {} ({} мс)", request.method, request.requestURI, response.status, tookMs)
            }
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        const val MDC_KEY = "traceId"
        private val TRACE_ID_PATTERN = Regex("[A-Za-z0-9-]{8,64}")
        private const val TRACE_ID_BYTES = 8

        fun newTraceId(): String {
            val bytes = ByteArray(TRACE_ID_BYTES).also { ThreadLocalRandom.current().nextBytes(it) }
            return HexFormat.of().formatHex(bytes)
        }
    }
}
