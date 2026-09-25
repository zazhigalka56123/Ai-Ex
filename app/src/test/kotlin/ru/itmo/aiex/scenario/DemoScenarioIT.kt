package ru.itmo.aiex.scenario

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.testing.AbstractIntegrationTest
import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class DemoScenarioIT : AbstractIntegrationTest() {
    private val demoExport: ByteArray =
        Files.readAllBytes(Path.of(System.getProperty("aiex.rootDir", "..")).resolve("docs/demo/telegram-masha.json"))

    @Test
    fun `полный сценарий демонстрации проходит от начала до конца`() {
        val admin = createAdmin()
        val client = createUser(RoleCode.USER, displayName = "Клиент")
        val psychologist = createUser(RoleCode.SPECIALIST, displayName = "Психолог")
        val otherSpecialist = createUser(RoleCode.SPECIALIST, displayName = "Чужой психолог")

        val personaId =
            mockMvc
                .post("/api/v1/personas") {
                    header(USER_HEADER, client)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("name" to "Маша", "relationshipKind" to "EX_PARTNER", "tagCodes" to listOf("sarcastic")))
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("DRAFT") }
                }.json()["id"]
                .asString()

        val importResult =
            mockMvc
                .multipart("/api/v1/personas/$personaId/imports") {
                    header(USER_HEADER, client)
                    file(MockMultipartFile("file", "telegram-masha.json", "application/json", demoExport))
                }.andExpect {
                    status { isAccepted() }
                    header { exists("Location") }
                }.andReturn()
        val importId = importResult.json()["id"].asString()

        mockMvc.get("/api/v1/imports/$importId") { header(USER_HEADER, client) }.andExpect {
            jsonPath("$.status") { value("PARSED") }
            jsonPath("$.source") { value("TELEGRAM_JSON") }
            jsonPath("$.theirName") { value("Маша") }
        }
        val persona = mockMvc.get("/api/v1/personas/$personaId") { header(USER_HEADER, client) }.json()
        assertThat(persona["status"].asString()).isEqualTo("READY")
        assertThat(persona["activeProfile"]["versionNo"].asInt()).isEqualTo(1)
        assertThat(persona["tags"].toList().map { it["source"].asString() }).contains("MANUAL", "AUTO")

        val profile = mockMvc.get("/api/v1/personas/$personaId/profile") { header(USER_HEADER, client) }.json()
        assertThat(profile["systemPrompt"].asString()).contains("- «")
        mockMvc.get("/api/v1/personas?size=1") { header(USER_HEADER, client) }.andExpect { header { string("X-Total-Count", "1") } }

        val conversationId =
            mockMvc
                .post("/api/v1/conversations") {
                    header(USER_HEADER, client)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("personaId" to personaId))
                }.andExpect { status { isCreated() } }
                .json()["id"]
                .asString()

        val replies =
            listOf("привет, спишь?", "как ты?", "помнишь фонтан?", "ну скажи что-нибудь").map { text ->
                send(client, conversationId, text).andExpect { status { isCreated() } }.json()["reply"]
            }
        assertThat(replies).allSatisfy { assertThat(it["sender"].asString()).isEqualTo("PERSONA") }
        assertThat(replies.map { it["text"].asString() }).allSatisfy { assertThat(it).isNotBlank() }

        send(client, conversationId, "эй [[llm:down]]").andExpect {
            status { isServiceUnavailable() }
            jsonPath("$.code") { value("LLM_UNAVAILABLE") }
        }

        val safety = send(client, conversationId, "я не хочу жить").andExpect { status { isCreated() } }.json()
        assertThat(safety["userMessage"]["flagged"].asBoolean()).isTrue()
        assertThat(safety["reply"]["text"].asString()).containsIgnoringCase("специалист")

        val history = readWholeHistory(client, conversationId, pageSize = 3)
        assertThat(history).hasSize(11).doesNotHaveDuplicates()

        val specialistId =
            mockMvc
                .post("/api/v1/specialists") {
                    header(USER_HEADER, psychologist)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "headline" to "Психолог",
                                "bio" to "Работаю с расставаниями",
                                "pricePerHour" to 2500,
                                "specializationCodes" to listOf("breakup"),
                            ),
                        )
                }.andExpect { status { isCreated() } }
                .json()["id"]
                .asString()
        val slotStart = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS).plus(10, ChronoUnit.HOURS)
        val slotId =
            mockMvc
                .post("/api/v1/specialists/$specialistId/slots") {
                    header(USER_HEADER, psychologist)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("startsAt" to slotStart.toString(), "durationMin" to 50))
                }.andExpect { status { isCreated() } }
                .json()["id"]
                .asString()
        val consultationId =
            mockMvc
                .post("/api/v1/consultations") {
                    header(USER_HEADER, client)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("slotId" to slotId, "sharedConversationId" to conversationId))
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("REQUESTED") }
                }.json()["id"]
                .asString()
        mockMvc
            .patch("/api/v1/consultations/$consultationId") {
                header(USER_HEADER, psychologist)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("status" to "CONFIRMED"))
            }.andExpect { jsonPath("$.status") { value("CONFIRMED") } }

        mockMvc.get("/api/v1/conversations/$conversationId/messages") { header(USER_HEADER, psychologist) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/conversations/$conversationId/messages") { header(USER_HEADER, otherSpecialist) }.andExpect { status { isForbidden() } }

        val flagId =
            mockMvc
                .post("/api/v1/moderation/flags") {
                    header(USER_HEADER, client)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("messageId" to replies.first()["id"].asString(), "reason" to "ABUSE", "comment" to "грубит"))
                }.andExpect { status { isCreated() } }
                .json()["id"]
                .asString()
        val adminView = mockMvc.get("/api/v1/conversations/$conversationId/messages") { header(USER_HEADER, admin) }.json()["items"].toList()
        assertThat(adminView).hasSize(2).allSatisfy { assertThat(it["flagged"].asBoolean()).isTrue() }

        val queue = mockMvc.get("/api/v1/moderation/flags?status=OPEN") { header(USER_HEADER, admin) }
        queue.andExpect { header { string("X-Total-Count", "2") } }
        assertThat(queue.json().toList().map { it["source"].asString() }).containsExactlyInAnyOrder("USER", "GUARDRAIL")

        mockMvc
            .patch("/api/v1/moderation/flags/$flagId") {
                header(USER_HEADER, admin)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("status" to "RESOLVED", "resolution" to "персона архивирована", "archivePersona" to true))
            }.andExpect {
                status { isOk() }
                jsonPath("$.personaArchived") { value(true) }
            }
        mockMvc.get("/api/v1/personas/$personaId") { header(USER_HEADER, client) }.andExpect { jsonPath("$.status") { value("ARCHIVED") } }
        mockMvc.get("/api/v1/personas/$personaId/profile") { header(USER_HEADER, client) }.andExpect { status { isConflict() } }
        send(client, conversationId, "ты тут?").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONVERSATION_INVALID_STATE") }
        }
        mockMvc.get("/api/v1/imports/$importId/messages") { header(USER_HEADER, client) }.andExpect { jsonPath("$.items.length()") { value(0) } }

        val notifications = mockMvc.get("/api/v1/notifications?size=50") { header(USER_HEADER, client) }.json().toList()
        assertThat(notifications.map { it["type"].asString() }).contains("IMPORT_PARSED", "PERSONA_READY", "FLAG_RESOLVED")
        val metrics = mockMvc.get("/api/v1/admin/metrics") { header(USER_HEADER, admin) }.json()["metrics"]
        assertThat(metrics["personas.archived"].asLong()).isEqualTo(1)
        assertThat(metrics["agent.runs.failed"].asLong()).isEqualTo(1)
        assertThat(metrics["consultations.confirmed"].asLong()).isEqualTo(1)
        assertThat(metrics["flags.resolved"].asLong()).isEqualTo(1)
    }

    private fun send(actor: UUID, conversationId: String, text: String) = mockMvc.post("/api/v1/conversations/$conversationId/messages") {
        header(USER_HEADER, actor)
        contentType = MediaType.APPLICATION_JSON
        content = json(mapOf("text" to text))
    }

    private fun readWholeHistory(actor: UUID, conversationId: String, pageSize: Int): List<String> {
        val ids = mutableListOf<String>()
        var cursor: String? = null
        do {
            val query = "limit=$pageSize" + (cursor?.let { "&cursor=$it" } ?: "")
            val page: JsonNode = mockMvc.get("/api/v1/conversations/$conversationId/messages?$query") { header(USER_HEADER, actor) }.json()
            ids += page["items"].toList().map { it["id"].asString() }
            cursor = page["nextCursor"].takeUnless { it == null || it.isNull }?.asString()
        } while (cursor != null)
        return ids
    }
}
