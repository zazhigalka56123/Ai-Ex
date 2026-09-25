package ru.itmo.aiex.dialog.websocket

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.util.UriComponentsBuilder
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.dialog.service.ConversationService
import ru.itmo.aiex.common.web.ActorLookup
import ru.itmo.aiex.common.web.AiExHeaders
import java.util.UUID

/**
 * Пускает в сокет беседы тех же, кому REST отдаёт её целиком: владельца и специалиста, которому беседу расшарили.
 * Браузер не умеет ставить заголовки на WebSocket, поэтому пользователь берётся из `X-User-Id` или из `?userId=` (лаб. 1).
 */
@Component
class ChatHandshakeInterceptor(private val actorLookup: ObjectProvider<ActorLookup>, private val conversations: ConversationService) :
    HandshakeInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val conversationId = conversationIdOf(request) ?: return reject(response, HttpStatus.NOT_FOUND)
        val actor = userIdOf(request)?.let { actorLookup.getObject().findActor(it) } ?: return reject(response, HttpStatus.UNAUTHORIZED)
        try {
            conversations.get(actor, conversationId)
        } catch (ex: AiExException) {
            log.info("WebSocket беседы {} для {} отклонён: {}", conversationId, actor.userId, ex.code)
            return reject(response, HttpStatus.valueOf(ex.code.httpStatus))
        }
        attributes[ACTOR_ATTRIBUTE] = actor
        attributes[CONVERSATION_ATTRIBUTE] = conversationId
        return true
    }

    override fun afterHandshake(request: ServerHttpRequest, response: ServerHttpResponse, wsHandler: WebSocketHandler, exception: Exception?) = Unit

    private fun conversationIdOf(request: ServerHttpRequest): UUID? =
        PATH.matchEntire(request.uri.path)?.groupValues?.get(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun userIdOf(request: ServerHttpRequest): UUID? {
        val raw =
            request.headers.getFirst(AiExHeaders.USER_ID)
                ?: UriComponentsBuilder.fromUri(request.uri).build().queryParams.getFirst(USER_ID_PARAM)
        return raw?.trim()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    private fun reject(response: ServerHttpResponse, status: HttpStatus): Boolean {
        response.setStatusCode(status)
        return false
    }

    companion object {
        const val USER_ID_PARAM = "userId"
        private val PATH = Regex(".*/conversations/([^/]+)/ws/?")
    }
}
