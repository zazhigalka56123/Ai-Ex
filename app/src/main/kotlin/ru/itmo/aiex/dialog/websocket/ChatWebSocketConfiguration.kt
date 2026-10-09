package ru.itmo.aiex.dialog.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import ru.itmo.aiex.common.ExcludeFromCoverage
import ru.itmo.aiex.common.web.ApiProperties
@ExcludeFromCoverage
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class ChatWebSocketConfiguration(
    private val handler: ChatWebSocketHandler,
    private val handshake: ChatHandshakeInterceptor,
    private val api: ApiProperties,
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // Без setAllowedOrigins браузеру разрешён только тот же origin - фронт раздаёт само приложение.
        registry.addHandler(handler, "${api.basePath}/conversations/*/ws").addInterceptors(handshake)
    }
}
