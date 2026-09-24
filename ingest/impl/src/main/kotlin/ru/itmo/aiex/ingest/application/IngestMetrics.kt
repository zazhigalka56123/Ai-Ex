package ru.itmo.aiex.ingest.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.ingest.domain.ImportStatus
import ru.itmo.aiex.ingest.domain.port.ChatImportRepository
import ru.itmo.aiex.ingest.domain.port.ImportedMessageRepository

@Component
@Transactional(readOnly = true)
class IngestMetrics(private val imports: ChatImportRepository, private val messages: ImportedMessageRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = mapOf(
        "imports.total" to imports.count(),
        "imports.parsed" to imports.countByStatus(ImportStatus.PARSED),
        "imports.failed" to imports.countByStatus(ImportStatus.FAILED),
        "imports.messages" to messages.count(),
    )
}
