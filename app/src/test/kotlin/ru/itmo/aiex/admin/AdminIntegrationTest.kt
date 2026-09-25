package ru.itmo.aiex.admin

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.dialog.service.DialogQuery
import ru.itmo.aiex.dialog.dto.MessageView
import ru.itmo.aiex.dialog.dto.SenderKind
import ru.itmo.aiex.persona.service.PersonaLifecycle
import ru.itmo.aiex.persona.service.TagCatalog
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

abstract class AdminIntegrationTest : AbstractIntegrationTest() {
    @MockkBean
    protected lateinit var dialogs: DialogQuery

    @MockkBean
    protected lateinit var personas: PersonaLifecycle

    @MockkBean
    protected lateinit var tags: TagCatalog

    protected fun message(ownerId: UUID = UUID.randomUUID(), body: String = "Ты мне больше не нужен") = MessageView(
        id = UUID.randomUUID(),
        conversationId = UUID.randomUUID(),
        personaId = UUID.randomUUID(),
        ownerId = ownerId,
        sender = SenderKind.PERSONA,
        body = body,
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
        flagged = false,
    )

    protected fun report(reporter: UUID, message: MessageView, reason: String = "ABUSE", comment: String? = null): ResultActionsDsl {
        every { dialogs.findMessageVisibleTo(message.id, match { it.userId == reporter }) } returns message
        return mockMvc.post("/api/v1/moderation/flags") {
            header(USER_HEADER, reporter)
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("messageId" to message.id, "reason" to reason, "comment" to comment))
        }
    }

    protected fun reportOk(reporter: UUID, message: MessageView, reason: String = "ABUSE"): UUID {
        val result = report(reporter, message, reason).andExpect { status { isCreated() } }.andReturn()
        return UUID.fromString(result.json()["id"].asString())
    }
}
