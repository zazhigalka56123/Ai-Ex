package ru.itmo.aiex.dialog.web

import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import ru.itmo.aiex.common.paging.CursorCodec
import ru.itmo.aiex.common.paging.TimeIdPosition
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.dialog.DialogIntegrationTest
import java.time.Duration
import java.time.Instant
import java.util.UUID

class MessageHistoryIT : DialogIntegrationTest() {
    private data class Page(val ids: List<UUID>, val nextCursor: String?)

    private fun page(actor: UUID, conversationId: UUID, cursor: String?, limit: Int): Page {
        val query = buildString {
            append("?limit=").append(limit)
            if (cursor != null) append("&cursor=").append(cursor)
        }
        val body =
            mockMvc
                .get("/api/v1/conversations/$conversationId/messages$query") { header(USER_HEADER, actor) }
                .andExpect {
                    status { isOk() }
                    header { doesNotExist("X-Total-Count") }
                }.json()
        val next = body.get("nextCursor")?.takeUnless { it.isNull }?.asString()
        return Page(body["items"].values().map { UUID.fromString(it["id"].asString()) }, next)
    }

    private fun scrollAll(actor: UUID, conversationId: UUID, limit: Int): List<Page> {
        val pages = mutableListOf(page(actor, conversationId, null, limit))
        while (pages.last().nextCursor != null && pages.size < 100) {
            pages += page(actor, conversationId, pages.last().nextCursor, limit)
        }
        return pages
    }

    @Test
    fun `45 сообщений порциями по 20 - без дублей и пропусков, новые сверху, в конце nextCursor null`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val stored = insertMessages(conversationId, 45)

        val pages = scrollAll(owner, conversationId, limit = 20)

        assertThat(pages.map { it.ids.size }).containsExactly(20, 20, 5)
        assertThat(pages.map { it.nextCursor != null }).containsExactly(true, true, false)
        val all = pages.flatMap { it.ids }
        assertThat(all).doesNotHaveDuplicates().hasSize(45)
        assertThat(all).containsExactlyElementsOf(stored.sortedWith(FEED_ORDER).map { it.id })
    }

    @Test
    fun `одинаковый created_at - составной курсор не теряет и не дублирует сообщения на границе порций`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val sameInstant = Instant.parse("2026-09-10T12:00:00.123456Z")
        val older = insertMessages(conversationId, 5, startAt = Instant.parse("2026-09-10T11:00:00Z"))
        val tied = insertMessages(conversationId, 23, startAt = sameInstant, step = Duration.ZERO)
        val newer = insertMessages(conversationId, 4, startAt = Instant.parse("2026-09-10T13:00:00Z"))

        val pages = scrollAll(owner, conversationId, limit = 7)

        val all = pages.flatMap { it.ids }
        assertThat(all).doesNotHaveDuplicates().hasSize(32)
        assertThat(all).containsExactlyElementsOf((older + tied + newer).sortedWith(FEED_ORDER).map { it.id })
        assertThat(pages.map { it.ids.size }).containsExactly(7, 7, 7, 7, 4)
        assertThat(pages.last().nextCursor).isNull()
    }

    @Test
    fun `курсор кодирует позицию последнего отданного сообщения`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val stored = insertMessages(conversationId, 3).sortedWith(FEED_ORDER)

        val first = page(owner, conversationId, null, limit = 2)

        assertThat(CursorCodec.decodeTimeId(checkNotNull(first.nextCursor))).isEqualTo(TimeIdPosition(stored[1].createdAt, stored[1].id))
    }

    @Test
    fun `пустая беседа - пустая порция без курсора`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        assertThat(page(owner, conversationId, null, limit = 30)).isEqualTo(Page(emptyList(), null))
    }

    @Test
    fun `битый курсор и limit вне диапазона - 400`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        listOf("?cursor=not*base64", "?cursor=bm90LWEtY3Vyc29y", "?limit=51", "?limit=0").forEach { query ->
            mockMvc.get("/api/v1/conversations/$conversationId/messages$query") { header(USER_HEADER, owner) }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
        }
    }

    @Test
    fun `видимость - владелец и расшаренный специалист видят всё, администратор только флагнутые, специалист без доступа 403, посторонний 404`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val stored = insertMessages(conversationId, 10, flagged = { it == 2 || it == 7 })
        val flaggedIds = listOf(stored[7].id, stored[2].id)
        val sharedSpecialist = createUser(RoleCode.SPECIALIST)
        val otherSpecialist = createUser(RoleCode.SPECIALIST)
        val admin = createAdmin()
        val stranger = createUser()
        every { consultations.isConversationSharedWith(conversationId, sharedSpecialist) } returns true

        assertThat(scrollAll(owner, conversationId, 4).flatMap { it.ids }).hasSize(10)
        assertThat(scrollAll(sharedSpecialist, conversationId, 4).flatMap { it.ids }).hasSize(10)
        val adminPages = scrollAll(admin, conversationId, 1)
        assertThat(adminPages.flatMap { it.ids }).containsExactlyElementsOf(flaggedIds)

        mockMvc.get("/api/v1/conversations/$conversationId/messages") { header(USER_HEADER, otherSpecialist) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        mockMvc.get("/api/v1/conversations/$conversationId/messages") { header(USER_HEADER, stranger) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CONVERSATION_NOT_FOUND") }
        }
        mockMvc.get("/api/v1/conversations/${UUID.randomUUID()}/messages") { header(USER_HEADER, owner) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `отдельное сообщение - те же правила видимости`() {
        val owner = createUser()
        val conversationId = createConversation(owner, readyPersona(owner))
        val otherConversation = createConversation(owner, readyPersona(owner))
        val stored = insertMessages(conversationId, 2, flagged = { it == 0 })
        val admin = createAdmin()
        val otherSpecialist = createUser(RoleCode.SPECIALIST)

        mockMvc.get("/api/v1/conversations/$conversationId/messages/${stored[1].id}") { header(USER_HEADER, owner) }.andExpect {
            status { isOk() }
            jsonPath("$.text") { value("сообщение 1") }
            jsonPath("$.sender") { value("USER") }
        }
        mockMvc.get("/api/v1/conversations/$conversationId/messages/${stored[0].id}") { header(USER_HEADER, admin) }.andExpect {
            status { isOk() }
            jsonPath("$.flagged") { value(true) }
        }
        mockMvc.get("/api/v1/conversations/$conversationId/messages/${stored[1].id}") { header(USER_HEADER, admin) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("MESSAGE_NOT_FOUND") }
        }
        mockMvc.get("/api/v1/conversations/$otherConversation/messages/${stored[1].id}") { header(USER_HEADER, owner) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("MESSAGE_NOT_FOUND") }
        }
        mockMvc.get("/api/v1/conversations/$conversationId/messages/${stored[1].id}") { header(USER_HEADER, otherSpecialist) }.andExpect {
            status { isForbidden() }
        }
    }
}
