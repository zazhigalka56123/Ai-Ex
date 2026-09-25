package ru.itmo.aiex.admin.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import ru.itmo.aiex.admin.entity.FlagStatus

import ru.itmo.aiex.admin.repository.ModerationFlagRepository
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.ForbiddenException
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.events.MessageAutoFlagged
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.dialog.service.DialogQuery
import ru.itmo.aiex.dialog.dto.MessageView
import ru.itmo.aiex.dialog.dto.SenderKind
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AdminServicesTest {
    private val now = Instant.parse("2026-10-01T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val admin = Actor(UUID.randomUUID(), setOf(RoleCode.ADMIN))
    private val client = Actor(UUID.randomUUID(), setOf(RoleCode.USER))

    private fun view(id: UUID = UUID.randomUUID()) =
        MessageView(id, UUID.randomUUID(), UUID.randomUUID(), client.userId, SenderKind.PERSONA, "текст", now, flagged = false)

    @Test
    fun `метрики всех модулей сливаются в один отсортированный снимок - только для администратора`() {
        val board =
            MetricsBoard(
                listOf(
                    MetricsContributor {
                        mapOf("users.active" to 3L)
                    },
                    MetricsContributor { mapOf("flags.open" to 1L, "b.x" to 0L) },
                ),
                clock,
            )
        val snapshot = board.snapshot(admin)
        assertThat(snapshot.metrics.keys).containsExactly("b.x", "flags.open", "users.active")
        assertThat(snapshot.generatedAt).isEqualTo(now)
        assertThatThrownBy { board.snapshot(client) }.isInstanceOf(ForbiddenException::class.java)
    }

    @Test
    fun `гонка двух одинаковых жалоб - FLAG_ALREADY_REPORTED`() {
        val flags = mockk<ModerationFlagRepository>()
        val service = ModerationService(flags, DomainEventPublisher { }, clock)
        val message = FlaggedMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        every { flags.existsByMessageAndReporter(message.messageId, client.userId) } returns false
        every { flags.saveAndFlush(any()) } throws DataIntegrityViolationException("uq_moderation_flags_message_reporter")

        assertThatThrownBy { service.report(client, message, ReportMessageCommand(message.messageId, FlagReason.ABUSE)) }
            .isInstanceOf(ConflictException::class.java)
            .extracting { (it as AiExException).code }
            .isEqualTo(ErrorCode.FLAG_ALREADY_REPORTED)
    }

    @Test
    fun `обработчик guardrails - пропавшее сообщение пропускает, гонку дубликатов гасит индекс`() {
        val moderation = mockk<ModerationService>()
        val dialogs = mockk<DialogQuery>()
        val listener = GuardrailFlagListener(moderation, dialogs)
        val known = view()
        val missing = UUID.randomUUID()
        every { dialogs.findMessage(missing) } returns null
        every { dialogs.findMessage(known.id) } returns known
        every { moderation.raiseGuardrailFlag(any(), any(), any()) } throws DataIntegrityViolationException("ux_moderation_flags_guardrail")

        listener.on(MessageAutoFlagged(missing, UUID.randomUUID(), FlagReason.ABUSE, "мат", now))
        listener.on(MessageAutoFlagged(known.id, known.conversationId, FlagReason.ABUSE, "мат", now))

        verify(exactly = 1) {
            moderation.raiseGuardrailFlag(FlaggedMessage(known.id, known.conversationId, known.personaId), FlagReason.ABUSE, "мат")
        }
    }

    @Test
    fun `повтор события guardrails не создаёт второй флаг`() {
        val flags = mockk<ModerationFlagRepository>()
        val service = ModerationService(flags, DomainEventPublisher { }, clock)
        val message = FlaggedMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        every { flags.existsGuardrailFlag(message.messageId, FlagReason.SELF_HARM) } returns true

        assertThat(service.raiseGuardrailFlag(message, FlagReason.SELF_HARM, "повтор")).isNull()
        verify(exactly = 0) { flags.saveAndFlush(any()) }
    }

    @Test
    fun `справочники меняет только администратор, читает кто угодно`() {
        val tags = mockk<ru.itmo.aiex.persona.service.TagCatalog>()
        val specializations = mockk<ru.itmo.aiex.care.service.SpecializationCatalog>()
        val dictionaries = DictionaryAdministration(tags, specializations)
        every { specializations.get(1) } returns ru.itmo.aiex.common.dictionary.DictionaryEntry(1, "grief", "Горе")

        assertThat(dictionaries.get(DictionaryKind.SPECIALIZATIONS, 1).code).isEqualTo("grief")
        assertThatThrownBy { dictionaries.create(client, DictionaryKind.TAGS, "cold", "Холодная") }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { dictionaries.update(client, DictionaryKind.SPECIALIZATIONS, 1, "x") }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { dictionaries.delete(client, DictionaryKind.TAGS, 1) }.isInstanceOf(ForbiddenException::class.java)
        verify(exactly = 0) { tags.create(any(), any()) }
        assertThat(FlagStatus.entries).hasSize(4)
    }
}
