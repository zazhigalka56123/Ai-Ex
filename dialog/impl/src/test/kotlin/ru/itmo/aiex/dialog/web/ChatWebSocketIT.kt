package ru.itmo.aiex.dialog.web

import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.fail
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import ru.itmo.aiex.AiExApplication
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.dialog.DialogIntegrationTest
import ru.itmo.aiex.dialog.web.chat.ChatSessionRegistry
import tools.jackson.databind.JsonNode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@SpringBootTest(classes = [AiExApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatWebSocketIT : DialogIntegrationTest() {
    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var registry: ChatSessionRegistry

    private val http = HttpClient.newHttpClient()
    private val opened = mutableListOf<ChatClient>()

    @AfterEach
    fun closeSockets() {
        opened.forEach { it.close() }
    }

    @Test
    fun `сообщение через сокет - эхо пользователя сразу, typing на время генерации, затем ответ персоны`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val client = connect(owner, conversationId)

        client.send("привет, спишь", requestId = "r-1")

        val accepted = client.next()
        assertThat(accepted["type"].asString()).isEqualTo("message")
        assertThat(accepted["requestId"].asString()).isEqualTo("r-1")
        assertThat(accepted["message"]["sender"].asString()).isEqualTo("USER")
        assertThat(accepted["message"]["text"].asString()).isEqualTo("привет, спишь")
        assertThat(client.next()["active"].asBoolean()).isTrue()
        assertThat(client.next()["message"]["id"]).isEqualTo(accepted["message"]["id"])
        val reply = client.next()
        assertThat(reply["message"]["sender"].asString()).isEqualTo("PERSONA")
        assertThat(reply["message"]["text"].asString()).isIn(phrases)
        assertThat(reply.has("requestId")).isFalse()
        val typing = client.next()
        assertThat(typing["type"].asString()).isEqualTo("typing")
        assertThat(typing["active"].asBoolean()).isFalse()

        assertThat(messageRows(conversationId).map { it["sender"] }).containsExactly("USER", "PERSONA")
        assertThat(agentRuns().single()["status"]).isEqualTo("SUCCESS")
    }

    @Test
    fun `вторая вкладка той же беседы получает сообщения, отправленные из первой`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val first = connect(owner, conversationId)
        val second = connect(owner, conversationId)

        first.send("где ты была?")

        val seen = second.untilTypingStops().filter { it["type"].asString() == "message" }.map { it["message"]["sender"].asString() }
        assertThat(seen).containsExactly("USER", "USER", "PERSONA")
        assertThat(registry.subscribers(conversationId)).isEqualTo(2)
    }

    @Test
    fun `LLM недоступен - отправителю error LLM_UNAVAILABLE, сообщение пользователя уже сохранено и разослано`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val client = connect(owner, conversationId)

        client.send("ты тут? $LLM_DOWN", requestId = "r-down")

        val events = client.untilTypingStops()
        assertThat(events.map { it["type"].asString() }).containsExactly("message", "typing", "error", "typing")
        val error = events[2]
        assertThat(error["code"].asString()).isEqualTo("LLM_UNAVAILABLE")
        assertThat(error["requestId"].asString()).isEqualTo("r-down")
        assertThat(error["detail"].asString()).contains("Сообщение сохранено")
        assertThat(messageRows(conversationId).map { it["sender"] }).containsExactly("USER")
    }

    @Test
    fun `guardrails - сообщение пользователя приходит повторно уже помеченным, вместо ответа поддержка`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val client = connect(owner, conversationId)

        client.send("я не хочу жить")

        val messages = client.untilTypingStops().filter { it["type"].asString() == "message" }.map { it["message"] }
        assertThat(messages.map { it["flagged"].asBoolean() }).containsExactly(false, true, false)
        assertThat(messages.last()["text"].asString()).contains("специалист")
    }

    @Test
    fun `невалидные команды - VALIDATION_FAILED, сокет остаётся открытым`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val client = connect(owner, conversationId)

        client.sendRaw("{не json")
        assertThat(client.next()["code"].asString()).isEqualTo("VALIDATION_FAILED")
        client.sendRaw(json(mapOf("type" to "send", "text" to "   ", "requestId" to "blank")))
        val blank = client.next()
        assertThat(blank["code"].asString()).isEqualTo("VALIDATION_FAILED")
        assertThat(blank["requestId"].asString()).isEqualTo("blank")
        client.sendRaw(json(mapOf("type" to "delete")))
        assertThat(client.next()["detail"].asString()).contains("Неизвестная команда")
        client.send("x".repeat(2001))
        assertThat(client.next()["detail"].asString()).contains("2000")

        client.send("а теперь нормально")
        assertThat(client.untilTypingStops().last()["active"].asBoolean()).isFalse()
        assertThat(messageRows(conversationId)).hasSize(2)
    }

    @Test
    fun `рукопожатие - без пользователя 401, чужому и администратору 404`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))

        assertHandshakeStatus(null, conversationId, 401)
        assertHandshakeStatus(UUID.randomUUID(), conversationId, 401)
        assertHandshakeStatus(createUser(), conversationId, 404)
        assertHandshakeStatus(createAdmin(), conversationId, 404)
        assertHandshakeStatus(owner, UUID.randomUUID(), 404)
        assertHandshakeStatus(createUser(RoleCode.SPECIALIST), conversationId, 403)
    }

    @Test
    fun `специалист с расшаренной беседой видит её вживую, но писать в неё не может`() {
        val owner = createUser()
        val specialist = createUser(RoleCode.SPECIALIST)
        val conversationId = createConversation(owner, readyPersona(owner))
        every { consultations.isConversationSharedWith(conversationId, specialist) } returns true
        val client = connect(owner, conversationId)
        val observer = connect(specialist, conversationId)

        client.send("привет")
        assertThat(observer.untilTypingStops().count { it["type"].asString() == "message" }).isEqualTo(3)

        observer.send("я специалист")
        assertThat(observer.next()["code"].asString()).isEqualTo("CONVERSATION_NOT_FOUND")
        assertThat(messageRows(conversationId)).hasSize(2)
    }

    @Test
    fun `закрытый сокет убирается из рассылки`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val client = connect(owner, conversationId)
        assertThat(registry.subscribers(conversationId)).isEqualTo(1)

        client.close()

        await atMost Duration.ofSeconds(5) untilAsserted { assertThat(registry.subscribers(conversationId)).isZero() }
    }

    private fun uri(userId: UUID?, conversationId: UUID): URI {
        val query = userId?.let { "?userId=$it" }.orEmpty()
        return URI("ws://localhost:$port/api/v1/conversations/$conversationId/ws$query")
    }

    private fun connect(userId: UUID, conversationId: UUID): ChatClient {
        val events = LinkedBlockingQueue<JsonNode>()
        val socket = http.newWebSocketBuilder().buildAsync(uri(userId, conversationId), Collector(events)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return ChatClient(socket, events).also { opened += it }
    }

    private fun assertHandshakeStatus(userId: UUID?, conversationId: UUID, status: Int) {
        val handshake = http.newWebSocketBuilder().buildAsync(uri(userId, conversationId), Collector(LinkedBlockingQueue()))
        assertThatThrownBy { handshake.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .isInstanceOf(ExecutionException::class.java)
            .satisfies({ assertThat((it.cause as WebSocketHandshakeException).response.statusCode()).isEqualTo(status) })
    }

    private inner class Collector(private val events: LinkedBlockingQueue<JsonNode>) : WebSocket.Listener {
        private val buffer = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                events.add(jsonMapper.readTree(buffer.toString()))
                buffer.setLength(0)
            }
            webSocket.request(1)
            return null
        }
    }

    private inner class ChatClient(private val socket: WebSocket, private val events: LinkedBlockingQueue<JsonNode>) {
        fun send(text: String, requestId: String = "r") = sendRaw(json(mapOf("type" to "send", "text" to text, "requestId" to requestId)))

        fun sendRaw(payload: String) {
            socket.sendText(payload, true).get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }

        fun next(): JsonNode = events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: fail("Событие не пришло за $TIMEOUT_SECONDS секунд")

        fun untilTypingStops(): List<JsonNode> {
            val seen = mutableListOf<JsonNode>()
            do {
                seen.add(next())
            } while (!(seen.last()["type"].asString() == "typing" && !seen.last()["active"].asBoolean()))
            return seen
        }

        fun close() {
            if (!socket.isOutputClosed) socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private companion object {
        // С запасом на холодный старт сервера: первое рукопожатие в прогоне бывает медленным.
        const val TIMEOUT_SECONDS = 15L
    }
}
