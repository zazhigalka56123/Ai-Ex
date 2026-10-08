package ru.itmo.aiex.ingest.service

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import ru.itmo.aiex.ingest.entity.ImportErrorCode
import ru.itmo.aiex.persona.service.PersonaLifecycle
@Component
class StaleImportRecovery(private val transactions: ImportTransactions, private val personaLifecycle: PersonaLifecycle) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun releaseStuckImports(): Int {
        val stuck = transactions.findStuckIds()
        if (stuck.isEmpty()) return 0
        log.warn("Найдено {} импортов в статусе PARSING без исполнителя - помечаю FAILED", stuck.size)
        stuck.forEach { importId ->
            val failed = transactions.markFailed(importId, ImportErrorCode.INTERNAL, REASON)
            runCatching { personaLifecycle.trainingFailed(failed.personaId, importId) }
                .onFailure { log.error("Импорт {}: не удалось вернуть персону {} из TRAINING", importId, failed.personaId, it) }
        }
        return stuck.size
    }

    private companion object {
        const val REASON = "Разбор прерван перезапуском приложения. Загрузите выгрузку заново"
    }
}
