package ru.itmo.aiex.gateway

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@RestController
class FallbackController {
    @RequestMapping("/fallback")
    fun unavailable(exchange: ServerWebExchange): Mono<ProblemDetail> = Mono.just(
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Сервис временно недоступен").apply {
            setProperty("code", "SERVICE_UNAVAILABLE")
            setProperty("traceId", exchange.request.headers.getFirst(TraceIdWebFilter.HEADER))
        },
    )
}
