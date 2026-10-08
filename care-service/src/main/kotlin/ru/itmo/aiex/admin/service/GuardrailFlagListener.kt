package ru.itmo.aiex.admin.service

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionalEventListener
import ru.itmo.aiex.common.events.MessageAutoFlagged
import ru.itmo.aiex.dialog.service.DialogQuery
@Component
class GuardrailFlagListener(private val moderation: ModerationService, @param:Lazy private val dialogs: DialogQuery) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(fallbackExecution = true)
    fun on(event: MessageAutoFlagged) {
        val message = dialogs.findMessage(event.messageId)
        if (message == null) {
            log.warn("Сообщение {} из события {} не найдено - системный флаг не создан", event.messageId, event.eventId)
            return
        }
        try {
            moderation.raiseGuardrailFlag(message.toFlagged(), event.reason, event.details)
        } catch (ex: DataIntegrityViolationException) {
            log.info("Системный флаг на сообщение {} ({}) уже создан параллельно: {}", event.messageId, event.reason, ex.mostSpecificCause.message)
        }
    }
}
