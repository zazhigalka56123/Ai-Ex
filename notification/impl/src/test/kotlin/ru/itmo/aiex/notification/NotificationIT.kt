package ru.itmo.aiex.notification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.get
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import ru.itmo.aiex.common.events.ChatImportFailed
import ru.itmo.aiex.common.events.ChatImportParsed
import ru.itmo.aiex.common.events.ConsultationRequested
import ru.itmo.aiex.common.events.ConsultationStatusChanged
import ru.itmo.aiex.common.events.DomainEvent
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.events.ModerationFlagResolved
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.common.events.PersonaProfileActivated
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.time.Instant
import java.util.UUID

class NotificationIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var events: DomainEventPublisher

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var metrics: List<MetricsContributor>

    private val now = Instant.parse("2026-09-22T03:00:00Z")

    private fun publish(event: DomainEvent) = TransactionTemplate(transactionManager).executeWithoutResult { events.publish(event) }

    private fun rows(): List<Map<String, Any?>> = jdbcTemplate.queryForList("SELECT * FROM notification.notifications ORDER BY created_at")

    @Test
    fun `каждое событие даёт уведомление SENT нужному получателю`() {
        val owner = UUID.randomUUID()
        val specialist = UUID.randomUUID()
        val client = UUID.randomUUID()
        val reporter = UUID.randomUUID()
        val personaId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()

        publish(ChatImportParsed(UUID.randomUUID(), personaId, owner, messageCount = 120, occurredAt = now))
        publish(ChatImportFailed(UUID.randomUUID(), personaId, owner, errorCode = "UNSUPPORTED_FORMAT", occurredAt = now))
        publish(PersonaProfileActivated(personaId, owner, UUID.randomUUID(), versionNo = 2, occurredAt = now))
        publish(PersonaArchived(personaId, owner, archivedBy = owner, byAdmin = false, occurredAt = now))
        publish(ConsultationRequested(sessionId, client, specialist, startsAt = now.plusSeconds(3600), occurredAt = now))
        publish(ConsultationStatusChanged(sessionId, client, specialist, status = "CONFIRMED", occurredAt = now))
        publish(ModerationFlagResolved(UUID.randomUUID(), UUID.randomUUID(), status = "RESOLVED", reporterId = reporter, occurredAt = now))
        publish(ModerationFlagResolved(UUID.randomUUID(), UUID.randomUUID(), status = "REJECTED", reporterId = null, occurredAt = now))

        val rows = rows()
        assertThat(rows.map { it["type"] to it["recipient_id"] }).containsExactly(
            "IMPORT_PARSED" to owner,
            "IMPORT_FAILED" to owner,
            "PERSONA_READY" to owner,
            "PERSONA_ARCHIVED" to owner,
            "CONSULTATION_REQUESTED" to specialist,
            "CONSULTATION_STATUS_CHANGED" to client,
            "FLAG_RESOLVED" to reporter,
        )
        assertThat(rows.map { it["status"] }).containsOnly("SENT")
        assertThat(rows.map { it["attempts"] }).containsOnly(1)
        assertThat(rows.map { it["sent_at"] }).doesNotContainNull()
        assertThat(rows[5]["payload"].toString()).contains(sessionId.toString()).contains("CONFIRMED")
    }

    @Test
    fun `GET notifications - только свои, X-Total-Count, фильтр по статусу и payload объектом`() {
        val me = createUser()
        val other = createUser(RoleCode.SPECIALIST)
        val personaId = UUID.randomUUID()
        repeat(3) { publish(PersonaProfileActivated(personaId, me, UUID.randomUUID(), versionNo = it + 1, occurredAt = now)) }
        events.publish(PersonaArchived(personaId, other, archivedBy = other, byAdmin = true, occurredAt = now))

        mockMvc.get("/api/v1/notifications?size=2") { header(USER_HEADER, me) }.andExpect {
            status { isOk() }
            header { string("X-Total-Count", "3") }
            header { string("X-Total-Pages", "2") }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].type") { value("PERSONA_READY") }
            jsonPath("$[0].status") { value("SENT") }
            jsonPath("$[0].payload.personaId") { value(personaId.toString()) }
            jsonPath("$[0].payload.versionNo") { value(3) }
        }
        mockMvc.get("/api/v1/notifications?status=FAILED") { header(USER_HEADER, me) }.andExpect {
            header { string("X-Total-Count", "0") }
        }
        mockMvc.get("/api/v1/notifications") { header(USER_HEADER, other) }.andExpect {
            header { string("X-Total-Count", "1") }
            jsonPath("$[0].type") { value("PERSONA_ARCHIVED") }
            jsonPath("$[0].payload.byAdmin") { value(true) }
        }
        mockMvc.get("/api/v1/notifications?sort=type,asc") { header(USER_HEADER, me) }.andExpect { status { isBadRequest() } }
        mockMvc.get("/api/v1/notifications").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `метрики уведомлений`() {
        publish(PersonaArchived(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), byAdmin = false, occurredAt = now))

        val notificationMetrics = metrics.map { it.metrics() }.first { it.containsKey("notifications.sent") }
        assertThat(notificationMetrics)
            .containsEntry("notifications.sent", 1L)
            .containsEntry("notifications.failed", 0L)
            .containsEntry("notifications.pending", 0L)
    }
}
