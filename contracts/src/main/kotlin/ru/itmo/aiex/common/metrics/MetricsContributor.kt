package ru.itmo.aiex.common.metrics

fun interface MetricsContributor {
    fun metrics(): Map<String, Long>
}
