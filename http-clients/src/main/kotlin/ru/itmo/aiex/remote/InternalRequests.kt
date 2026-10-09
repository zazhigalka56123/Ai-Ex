package ru.itmo.aiex.remote

import ru.itmo.aiex.common.web.AiExHeaders
import java.util.UUID

data class DictionaryCreate(val code: String, val title: String)

data class DictionaryUpdate(val title: String)

data class NotificationDelivery(val eventId: UUID, val recipientId: UUID, val type: String, val payload: Map<String, Any?>)

object InternalApi {
    const val TOKEN_HEADER = AiExHeaders.INTERNAL_TOKEN
}
