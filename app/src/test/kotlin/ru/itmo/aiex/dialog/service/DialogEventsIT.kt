package ru.itmo.aiex.dialog.service

import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import ru.itmo.aiex.common.events.DomainEvent
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.events.ModerationFlagRaised
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.dialog.DialogIntegrationTest

import ru.itmo.aiex.dialog.dto.SenderKind
import java.time.Instant
import java.util.UUID

class DialogEventsIT : DialogIntegrationTest() {
    @Autowired
    private lateinit var events: DomainEventPublisher

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dialogQuery: DialogQuery

    @Autowired
    private lateinit var metrics: List<MetricsContributor>

    private val now = Instant.parse("2026-09-22T03:00:00Z")

    private fun publishInTransaction(event: DomainEvent) = TransactionTemplate(transactionManager).executeWithoutResult { events.publish(event) }

    private fun status(conversationId: UUID): String? =
        jdbcTemplate.queryForObject("SELECT status FROM dialog.conversations WHERE id = ?", String::class.java, conversationId)

    private fun flagged(messageId: UUID): Boolean? =
        jdbcTemplate.queryForObject("SELECT flagged FROM dialog.messages WHERE id = ?", Boolean::class.javaObjectType, messageId)

    @Test
    fun `PersonaArchived - все беседы персоны уходят в архив, писать в них больше нельзя`() {
        val owner = createUser()
        val personaId = readyPersona(owner)
        val otherPersona = readyPersona(owner)
        val first = createConversation(owner, personaId)
        val second = createConversation(owner, personaId)
        val untouched = createConversation(owner, otherPersona)
        val archived = PersonaArchived(personaId, owner, archivedBy = owner, byAdmin = false, occurredAt = now)

        publishInTransaction(archived)
        publishInTransaction(archived)

        assertThat(listOf(first, second).map(::status)).containsOnly("ARCHIVED")
        assertThat(status(untouched)).isEqualTo("ACTIVE")
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM dialog.conversations WHERE id = ?", Long::class.javaObjectType, first))
            .isEqualTo(1L)
        send(owner, first, "ты тут?").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONVERSATION_INVALID_STATE") }
        }
        mockMvc.get("/api/v1/conversations/$first/messages") { header(USER_HEADER, owner) }.andExpect { status { isOk() } }
    }

    @Test
    fun `ModerationFlagRaised - сообщение помечается, администратор начинает его видеть`() {
        val owner = createUser()
        val admin = createAdmin()
        val conversationId = createConversation(owner, readyPersona(owner))
        val (target, other) = insertMessages(conversationId, 2)

        publishInTransaction(ModerationFlagRaised(UUID.randomUUID(), target.id, FlagReason.ABUSE, now))
        events.publish(ModerationFlagRaised(UUID.randomUUID(), target.id, FlagReason.OTHER, now))
        events.publish(ModerationFlagRaised(UUID.randomUUID(), UUID.randomUUID(), FlagReason.SPAM, now))

        assertThat(flagged(target.id)).isTrue()
        assertThat(flagged(other.id)).isFalse()
        mockMvc.get("/api/v1/conversations/$conversationId/messages") { header(USER_HEADER, admin) }.andExpect {
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].id") { value(target.id.toString()) }
        }
    }

    @Test
    fun `DialogQuery - сообщение с владельцем и персоной, видимость по тем же правилам`() {
        val owner = createUser()
        val personaId = readyPersona(owner)
        val conversationId = createConversation(owner, personaId)
        val (plain, flaggedMessage) = insertMessages(conversationId, 2, flagged = { it == 1 })
        val admin = Actor(UUID.randomUUID(), setOf(RoleCode.ADMIN))
        val sharedSpecialist = Actor(UUID.randomUUID(), setOf(RoleCode.SPECIALIST))
        val otherSpecialist = Actor(UUID.randomUUID(), setOf(RoleCode.SPECIALIST))
        val stranger = Actor(UUID.randomUUID(), setOf(RoleCode.USER))
        every { consultations.isConversationSharedWith(conversationId, sharedSpecialist.userId) } returns true

        val view = checkNotNull(dialogQuery.findMessage(plain.id))
        assertThat(view.conversationId).isEqualTo(conversationId)
        assertThat(view.personaId).isEqualTo(personaId)
        assertThat(view.ownerId).isEqualTo(owner)
        assertThat(view.sender).isEqualTo(SenderKind.USER)
        assertThat(view.body).isEqualTo("сообщение 0")
        assertThat(dialogQuery.findMessage(UUID.randomUUID())).isNull()

        assertThat(dialogQuery.findMessageVisibleTo(plain.id, Actor(owner, setOf(RoleCode.USER)))).isNotNull()
        assertThat(dialogQuery.findMessageVisibleTo(plain.id, sharedSpecialist)).isNotNull()
        assertThat(dialogQuery.findMessageVisibleTo(plain.id, admin)).isNull()
        assertThat(dialogQuery.findMessageVisibleTo(flaggedMessage.id, admin)?.flagged).isTrue()
        assertThat(dialogQuery.findMessageVisibleTo(plain.id, otherSpecialist)).isNull()
        assertThat(dialogQuery.findMessageVisibleTo(plain.id, stranger)).isNull()
        assertThat(dialogQuery.findMessageVisibleTo(UUID.randomUUID(), admin)).isNull()

        assertThat(dialogQuery.isConversationOwnedBy(conversationId, owner)).isTrue()
        assertThat(dialogQuery.isConversationOwnedBy(conversationId, stranger.userId)).isFalse()
    }

    @Test
    fun `метрики dialog - активные беседы, сообщения и флаги`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        createConversation(owner, readyPersona(owner))
        send(owner, conversationId, "ты дура").andExpect { status { isCreated() } }

        val dialogMetrics = metrics.map { it.metrics() }.first { it.containsKey("messages.total") }
        assertThat(dialogMetrics)
            .containsEntry("conversations.active", 2L)
            .containsEntry("messages.total", 2L)
            .containsEntry("messages.flagged", 1L)
    }
}
