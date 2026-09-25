package ru.itmo.aiex.ingest.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("aiex.import")
data class ImportProperties(val maxFileMb: Int = 20, val maxMessages: Int = 50_000, val asyncThresholdKb: Int = 512) {
    init {
        require(maxFileMb > 0) { "aiex.import.max-file-mb должен быть > 0" }
        require(maxMessages > 0) { "aiex.import.max-messages должен быть > 0" }
        require(asyncThresholdKb >= 0) { "aiex.import.async-threshold-kb должен быть ≥ 0" }
    }

    val maxFileBytes: Long get() = maxFileMb.toLong() * BYTES_IN_KB * BYTES_IN_KB

    val asyncThresholdBytes: Long get() = asyncThresholdKb.toLong() * BYTES_IN_KB

    private companion object {
        const val BYTES_IN_KB = 1024L
    }
}
