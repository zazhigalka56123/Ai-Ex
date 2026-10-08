package ru.itmo.aiex.distributed

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.common.metrics.MetricsContributor

@RestController
@Profile("microservice")
class LocalMetricsController(private val contributors: List<MetricsContributor>) {
    @GetMapping("/internal/metrics")
    fun getMetrics(): Map<String, Long> = contributors.flatMap { it.collectMetrics().entries }.associate { it.toPair() }
}
