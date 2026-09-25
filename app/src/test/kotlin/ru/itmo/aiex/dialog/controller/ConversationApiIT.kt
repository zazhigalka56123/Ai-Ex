package ru.itmo.aiex.dialog.controller

import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.dialog.DialogIntegrationTest
import ru.itmo.aiex.persona.dto.PersonaState
import java.util.UUID

class ConversationApiIT : DialogIntegrationTest() {
    @Test
    fun `создание беседы - 201, Location и название по умолчанию`() {
        val owner = createUser()
        val personaId = readyPersona(owner, name = "Маша")

        val result =
            mockMvc
                .post("/api/v1/conversations") {
                    header(USER_HEADER, owner)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("personaId" to personaId))
                }.andExpect {
                    status { isCreated() }
                    header { string("Location", containsString("/api/v1/conversations/")) }
                    jsonPath("$.title") { value("Беседа с Маша") }
                    jsonPath("$.status") { value("ACTIVE") }
                    jsonPath("$.personaId") { value(personaId.toString()) }
                    jsonPath("$.messageCount") { value(0) }
                    jsonPath("$.lastMessageAt") { value(null as Any?) }
                }.andReturn()
        assertThat(result.response.getHeader("Location")).endsWith(result.json()["id"].asString())
    }

    @Test
    fun `своё название сохраняется, пустое и слишком длинное - 400, без personaId - 400`() {
        val owner = createUser()
        val personaId = readyPersona(owner)
        val id = createConversation(owner, personaId, title = "  Три часа ночи ")
        mockMvc.get("/api/v1/conversations/$id") { header(USER_HEADER, owner) }.andExpect { jsonPath("$.title") { value("Три часа ночи") } }

        listOf(mapOf("personaId" to personaId, "title" to ""), mapOf("personaId" to personaId, "title" to "x".repeat(129))).forEach { body ->
            mockMvc
                .post("/api/v1/conversations") {
                    header(USER_HEADER, owner)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(body)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.errors[0].field") { value("title") }
                }
        }
        mockMvc
            .post("/api/v1/conversations") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("personaId") }
            }
    }

    @Test
    fun `персона не готова - 409 PERSONA_NOT_READY с указанием поля`() {
        val owner = createUser()
        val personaId = UUID.randomUUID()
        stubPersona(personaId, owner, "Маша", PersonaState.TRAINING, activeProfileId = null)

        mockMvc
            .post("/api/v1/conversations") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("personaId" to personaId))
            }.andExpect {
                status { isConflict() }
                content { contentType("application/problem+json") }
                jsonPath("$.code") { value("PERSONA_NOT_READY") }
                jsonPath("$.errors[0].field") { value("personaId") }
                jsonPath("$.errors[0].message") { value(containsString("TRAINING")) }
            }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dialog.conversations", Long::class.javaObjectType)).isZero()
    }

    @Test
    fun `чужая персона - 404 PERSONA_NOT_FOUND, без заголовка - 401`() {
        val owner = createUser()
        val stranger = createUser()
        val personaId = readyPersona(owner)
        mockMvc
            .post("/api/v1/conversations") {
                header(USER_HEADER, stranger)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("personaId" to personaId))
            }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("PERSONA_NOT_FOUND") }
            }
        mockMvc
            .post("/api/v1/conversations") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("personaId" to personaId))
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `список - только свои, X-Total-Count, фильтры и сортировка`() {
        val owner = createUser()
        val other = createUser()
        val masha = readyPersona(owner, name = "Маша")
        val dasha = readyPersona(owner, name = "Даша")
        val first = createConversation(owner, masha, "первая")
        val second = createConversation(owner, masha, "вторая")
        val third = createConversation(owner, dasha, "третья")
        createConversation(other, readyPersona(other))

        send(owner, first, "привет").andExpect { status { isCreated() } }
        mockMvc.delete("/api/v1/conversations/$third") { header(USER_HEADER, owner) }

        mockMvc.get("/api/v1/conversations?size=2") { header(USER_HEADER, owner) }.andExpect {
            status { isOk() }
            header { string("X-Total-Count", "3") }
            header { string("X-Total-Pages", "2") }
            header { string("Link", containsString("rel=\"next\"")) }
            jsonPath("$.length()") { value(2) }
        }
        mockMvc.get("/api/v1/conversations?sort=createdAt,asc") { header(USER_HEADER, owner) }.andExpect {
            jsonPath("$[*].id") { value(contains(first.toString(), second.toString(), third.toString())) }
        }
        mockMvc.get("/api/v1/conversations?status=ACTIVE&personaId=$masha&sort=lastMessageAt,asc") { header(USER_HEADER, owner) }.andExpect {
            header { string("X-Total-Count", "2") }
            jsonPath("$[0].id") { value(first.toString()) }
            jsonPath("$[0].messageCount") { value(2) }
            jsonPath("$[0].lastMessageAt") { exists() }
        }
        mockMvc.get("/api/v1/conversations?status=ARCHIVED") { header(USER_HEADER, owner) }.andExpect {
            header { string("X-Total-Count", "1") }
            jsonPath("$[0].id") { value(third.toString()) }
        }
        mockMvc.get("/api/v1/conversations?personaId=$dasha") { header(USER_HEADER, owner) }.andExpect {
            header { string("X-Total-Count", "1") }
        }
        mockMvc.get("/api/v1/conversations?sort=title,asc&status=NOPE") { header(USER_HEADER, owner) }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `карточка беседы - владельцу и расшаренному специалисту, нерасшаренному специалисту 403, остальным 404`() {
        val owner = createUser()
        val id = createConversation(owner, readyPersona(owner))
        val sharedSpecialist = createUser(RoleCode.SPECIALIST)
        val otherSpecialist = createUser(RoleCode.SPECIALIST)
        val admin = createAdmin()
        val stranger = createUser()
        every { consultations.isConversationSharedWith(id, sharedSpecialist) } returns true

        mockMvc.get("/api/v1/conversations/$id") { header(USER_HEADER, owner) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/conversations/$id") { header(USER_HEADER, sharedSpecialist) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/conversations/$id") { header(USER_HEADER, otherSpecialist) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        listOf(admin, stranger).forEach { actor ->
            mockMvc.get("/api/v1/conversations/$id") { header(USER_HEADER, actor) }.andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("CONVERSATION_NOT_FOUND") }
            }
        }
        mockMvc.get("/api/v1/conversations/${UUID.randomUUID()}") { header(USER_HEADER, owner) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `архивация - 204 и идемпотентна, чужая - 404`() {
        val owner = createUser()
        val stranger = createUser()
        val id = createConversation(owner, readyPersona(owner))

        mockMvc.delete("/api/v1/conversations/$id") { header(USER_HEADER, stranger) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CONVERSATION_NOT_FOUND") }
        }
        repeat(2) { mockMvc.delete("/api/v1/conversations/$id") { header(USER_HEADER, owner) }.andExpect { status { isNoContent() } } }
        mockMvc.get("/api/v1/conversations/$id") { header(USER_HEADER, owner) }.andExpect { jsonPath("$.status") { value("ARCHIVED") } }
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM dialog.conversations WHERE id = ?", Long::class.javaObjectType, id)).isEqualTo(1L)
        mockMvc.get("/api/v1/conversations/$id/messages") { header(USER_HEADER, owner) }.andExpect {
            status { isOk() }
            jsonPath("$.items") { isEmpty() }
        }
    }
}
