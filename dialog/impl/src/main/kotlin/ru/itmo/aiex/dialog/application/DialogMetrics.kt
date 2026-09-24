package ru.itmo.aiex.dialog.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.dialog.domain.ConversationStatus
import ru.itmo.aiex.dialog.domain.port.ConversationRepository
import ru.itmo.aiex.dialog.domain.port.MessageRepository

@Component
@Transactional(readOnly = true)
class DialogMetrics(private val conversations: ConversationRepository, private val messages: MessageRepository) : MetricsContributor {
    override fun metrics(): Map<String, Long> = mapOf(
        "conversations.active" to conversations.countByStatus(ConversationStatus.ACTIVE),
        "messages.total" to messages.count(),
        "messages.flagged" to messages.countFlagged(),
    )
}
