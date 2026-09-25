package ru.itmo.aiex.dialog.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import java.time.Instant
import java.util.UUID

class ConversationTest {
    private val now = Instant.parse("2026-09-22T03:00:00Z")

    private fun conversation() = Conversation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Беседа", now)

    @Test
    fun `архивация идемпотентна, в архивную беседу писать нельзя`() {
        val conversation = conversation()
        conversation.ensureAcceptsMessages()
        assertThat(conversation.archive()).isTrue()
        assertThat(conversation.archive()).isFalse()
        assertThat(conversation.status).isEqualTo(ConversationStatus.ARCHIVED)
        assertThatThrownBy { conversation.ensureAcceptsMessages() }
            .isInstanceOf(ConflictException::class.java)
            .extracting("code")
            .isEqualTo(ErrorCode.CONVERSATION_INVALID_STATE)
    }

    @Test
    fun `название по умолчанию укладывается в лимит колонки`() {
        assertThat(Conversation.defaultTitle("Маша")).isEqualTo("Беседа с Маша")
        assertThat(Conversation.defaultTitle("x".repeat(300))).hasSize(Conversation.TITLE_MAX_LENGTH)
    }

    @Test
    fun `сообщения - фабрики, флаг идемпотентен, позиция курсора из времени и id`() {
        val conversation = conversation()
        val user = Message.fromUser(conversation, "привет", now)
        val reply = Message.fromPersona(conversation, "ну привет", now.plusSeconds(1), UUID.randomUUID())
        assertThat(user.sender).isEqualTo(MessageSender.USER)
        assertThat(user.agentRunId).isNull()
        assertThat(user.conversationId).isEqualTo(conversation.id)
        assertThat(reply.sender).isEqualTo(MessageSender.PERSONA)
        assertThat(reply.agentRunId).isNotNull()
        assertThat(user.flag()).isTrue()
        assertThat(user.flag()).isFalse()
        assertThat(user.flagged).isTrue()
        assertThat(user.position.timestamp).isEqualTo(now)
        assertThat(user.position.id).isEqualTo(user.id)
        assertThat(user).isEqualTo(user).isNotEqualTo(reply)
        assertThat(conversation.isOwnedBy(conversation.userId)).isTrue()
    }
}
