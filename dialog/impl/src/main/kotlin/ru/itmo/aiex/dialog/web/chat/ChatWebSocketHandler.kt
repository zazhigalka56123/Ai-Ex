package ru.itmo.aiex.dialog.web.chat

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.dialog.application.MessageExchangeService
import ru.itmo.aiex.dialog.web.dto.ChatCommand
import ru.itmo.aiex.dialog.web.dto.ChatEvent
import ru.itmo.aiex.dialog.web.dto.toResponse
import tools.jackson.core.JacksonException
import tools.jackson.databind.json.JsonMapper

/**
 * Живой чат беседы. Клиент шлёт `{"type":"send","text":"…","requestId":"…"}`, все сокеты беседы получают
 * `message` (сообщение пользователя - сразу после фиксации, затем ответ персоны), `typing` на время генерации,
 * а отправитель - `error` в формате кода [ErrorCode]. Транзакции и guardrails те же, что у REST (TX-2).
 */
@Component
class ChatWebSocketHandler(
    private val exchanges: MessageExchangeService,
    private val registry: ChatSessionRegistry,
    private val jsonMapper: JsonMapper,
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val outbox = ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES)
        session.attributes[OUTBOX_ATTRIBUTE] = outbox
        registry.register(session.conversationId, outbox)
        log.info("WebSocket {} открыт: беседа {}, пользователь {}", session.id, session.conversationId, session.actor.userId)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        registry.unregister(session.conversationId, session.outbox)
        log.info("WebSocket {} закрыт: {}", session.id, status.code)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val command =
            try {
                jsonMapper.readValue(message.payload, ChatCommand::class.java)
            } catch (ex: JacksonException) {
                log.debug("WebSocket {}: нечитаемое сообщение: {}", session.id, ex.message)
                return reply(session, ChatEvent.error(ErrorCode.VALIDATION_FAILED, "Сообщение не читается как JSON команды чата", null))
            }
        val requestId = command.requestId?.take(ChatCommand.MAX_REQUEST_ID_LENGTH)
        val text = command.text?.trim().orEmpty()
        val problem =
            when {
                command.type != ChatCommand.SEND -> "Неизвестная команда «${command.type}»: поддерживается только «${ChatCommand.SEND}»"
                text.isEmpty() -> "Текст сообщения не может быть пустым"
                text.length > ChatCommand.MAX_TEXT_LENGTH -> "Текст длиннее ${ChatCommand.MAX_TEXT_LENGTH} символов"
                else -> null
            }
        if (problem != null) return reply(session, ChatEvent.error(ErrorCode.VALIDATION_FAILED, problem, requestId))
        exchange(session, text, requestId)
    }

    private fun exchange(session: WebSocketSession, text: String, requestId: String?) {
        val conversationId = session.conversationId
        var accepted = false
        try {
            val exchange =
                exchanges.send(session.actor, conversationId, text) { userMessage ->
                    accepted = true
                    broadcast(session, ChatEvent.message(userMessage.toResponse(), requestId))
                    broadcast(session, ChatEvent.typing(true))
                }
            // Сообщение пользователя - ещё раз: guardrails могли пометить его уже после фиксации.
            broadcast(session, ChatEvent.message(exchange.userMessage.toResponse(), requestId))
            broadcast(session, ChatEvent.message(exchange.reply.toResponse()))
        } catch (ex: AiExException) {
            reply(session, ChatEvent.error(ex.code, ex.message, requestId))
        } catch (ex: Exception) {
            log.error("WebSocket {}: сбой отправки в беседу {}", session.id, conversationId, ex)
            reply(session, ChatEvent.error(ErrorCode.INTERNAL_ERROR, "Внутренняя ошибка сервера, сообщение могло не сохраниться", requestId))
        } finally {
            if (accepted) broadcast(session, ChatEvent.typing(false))
        }
    }

    private fun broadcast(session: WebSocketSession, event: ChatEvent) {
        registry.broadcast(session.conversationId, jsonMapper.writeValueAsString(event))
    }

    private fun reply(session: WebSocketSession, event: ChatEvent) {
        registry.send(session.outbox, TextMessage(jsonMapper.writeValueAsString(event)))
    }

    private companion object {
        const val SEND_TIME_LIMIT_MS = 10_000
        const val SEND_BUFFER_LIMIT_BYTES = 512 * 1024
    }
}
