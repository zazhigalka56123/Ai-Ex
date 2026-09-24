package ru.itmo.aiex.dialog.web.chat

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Открытые сокеты по беседам - в пределах одного экземпляра приложения (в лаб. 4 рассылка уйдёт в брокер). */
@Component
class ChatSessionRegistry {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    fun register(conversationId: UUID, session: WebSocketSession) {
        sessions.computeIfAbsent(conversationId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun unregister(conversationId: UUID, session: WebSocketSession) {
        sessions.computeIfPresent(conversationId) { _, set -> set.apply { remove(session) }.takeIf { it.isNotEmpty() } }
    }

    fun subscribers(conversationId: UUID): Int = sessions[conversationId]?.size ?: 0

    fun broadcast(conversationId: UUID, payload: String) {
        val message = TextMessage(payload)
        sessions[conversationId].orEmpty().forEach { send(it, message) }
    }

    fun send(session: WebSocketSession, message: TextMessage) {
        if (!session.isOpen) return
        try {
            session.sendMessage(message)
        } catch (ex: IOException) {
            log.debug("WebSocket {}: не удалось отправить событие: {}", session.id, ex.message)
        } catch (ex: IllegalStateException) {
            log.debug("WebSocket {}: сессия закрыта во время отправки: {}", session.id, ex.message)
        }
    }
}
