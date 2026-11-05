package ru.itmo.aiex.gateway

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class FallbackController {
    @RequestMapping("/fallback")
    fun unavailable(): Mono<ProblemDetail> = Mono.just(
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Сервис временно недоступен"),
    )
}
