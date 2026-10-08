package ru.itmo.aiex.common.metrics

fun interface MetricsContributor {
    fun collectMetrics(): Map<String, Long>
}
