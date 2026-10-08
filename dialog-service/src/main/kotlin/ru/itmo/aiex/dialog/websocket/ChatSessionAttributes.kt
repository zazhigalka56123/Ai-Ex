package ru.itmo.aiex.dialog.websocket

import org.springframework.web.socket.WebSocketSession
import ru.itmo.aiex.common.security.Actor
import java.util.UUID

internal const val ACTOR_ATTRIBUTE = "aiex.chat.actor"
internal const val CONVERSATION_ATTRIBUTE = "aiex.chat.conversationId"
internal const val OUTBOX_ATTRIBUTE = "aiex.chat.outbox"

internal val WebSocketSession.actor: Actor get() = attributes[ACTOR_ATTRIBUTE] as Actor

internal val WebSocketSession.conversationId: UUID get() = attributes[CONVERSATION_ATTRIBUTE] as UUID

/** Потокобезопасная обёртка для отправки: в сессию пишут и её собственный поток, и рассылки из чужих. */
internal val WebSocketSession.outbox: WebSocketSession get() = attributes[OUTBOX_ATTRIBUTE] as WebSocketSession
