package ru.itmo.aiex.dialog.web.chat

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import ru.itmo.aiex.common.ExcludeFromCoverage
import ru.itmo.aiex.web.ApiPaths

@ExcludeFromCoverage
@Configuration(proxyBeanMethods = false)
@EnableWebSocket
class ChatWebSocketConfiguration(private val handler: ChatWebSocketHandler, private val handshake: ChatHandshakeInterceptor) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // Без setAllowedOrigins браузеру разрешён только тот же origin - фронт раздаёт само приложение.
        registry.addHandler(handler, "${ApiPaths.V1}/conversations/*/ws").addInterceptors(handshake)
    }
}
