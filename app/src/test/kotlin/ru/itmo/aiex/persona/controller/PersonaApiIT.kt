package ru.itmo.aiex.persona.controller

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.persona.service.PersonaLifecycle
import ru.itmo.aiex.persona.testing.PersonaIntegrationTest
import ru.itmo.aiex.persona.testing.TestCorpus
import java.util.UUID

class PersonaApiIT : PersonaIntegrationTest() {
    @Autowired
    private lateinit var lifecycle: PersonaLifecycle

    @Test
    fun `создание персоны - 201, Location, статус DRAFT, ручные теги с весом 1`() {
        val owner = createUser()
        val result =
            mockMvc
                .post("/api/v1/personas") {
                    header(USER_HEADER, owner)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        json(
                            mapOf(
                                "name" to "  Маша ",
                                "relationshipKind" to "EX_PARTNER",
                                "description" to "три года вместе",
                                "tagCodes" to listOf("Jealous", "night-owl"),
                            ),
                        )
                }.andExpect {
                    status { isCreated() }
                    header { string("Location", containsString("/api/v1/personas/")) }
                    jsonPath("$.name") { value("Маша") }
                    jsonPath("$.status") { value("DRAFT") }
                    jsonPath("$.relationshipKind") { value("EX_PARTNER") }
                    jsonPath("$.activeProfile") { doesNotExist() }
                    jsonPath("$.tags.length()") { value(2) }
                    jsonPath("$.tags[0].code") { value("jealous") }
                    jsonPath("$.tags[0].title") { value("ревнивая") }
                    jsonPath("$.tags[0].weight") { value(1.0) }
                    jsonPath("$.tags[0].source") { value("MANUAL") }
                    jsonPath("$.traits.length()") { value(0) }
                }.andReturn()
        val id = result.json()["id"].asString()
        assertThat(result.response.getHeader("Location")).endsWith(id)

        mockMvc.get("/api/v1/personas/$id") { header(USER_HEADER, owner) }.andExpect {
            status { isOk() }
            jsonPath("$.description") { value("три года вместе") }
            jsonPath("$.tags[1].code") { value("night-owl") }
        }
    }

    @Test
    fun `невалидное тело и неизвестный тег - 400 с именами полей`() {
        val owner = createUser()
        mockMvc
            .post("/api/v1/personas") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("name" to "", "relationshipKind" to "EX_PARTNER", "description" to "x".repeat(501)))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[*].field") { value(org.hamcrest.Matchers.hasItems("name", "description")) }
            }
        mockMvc
            .post("/api/v1/personas") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Маша"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("relationshipKind") }
            }
        mockMvc
            .post("/api/v1/personas") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("name" to "Маша", "relationshipKind" to "EX_PARTNER", "tagCodes" to listOf("cold", "no-such-tag")))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("tagCodes") }
                jsonPath("$.errors[0].message") { value(containsString("no-such-tag")) }
            }
    }

    @Test
    fun `создать персону может только роль USER - специалисту 403`() {
        val specialist = createUser(RoleCode.SPECIALIST)
        mockMvc
            .post("/api/v1/personas") {
                header(USER_HEADER, specialist)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("name" to "Маша", "relationshipKind" to "EX_PARTNER"))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
        mockMvc.post("/api/v1/personas") {
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("name" to "Маша", "relationshipKind" to "EX_PARTNER"))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `список - только свои, X-Total-Count и Link, сортировка и фильтр по статусу`() {
        val owner = createUser()
        val other = createUser()
        val names = listOf("Аня", "Вера", "Галя")
        names.forEach { createPersona(owner, it) }
        createPersona(other, "Чужая")

        mockMvc
            .get("/api/v1/personas?page=0&size=2&sort=name,asc") { header(USER_HEADER, owner) }
            .andExpect {
                status { isOk() }
                header { string("X-Total-Count", "3") }
                header { string("X-Total-Pages", "2") }
                header { string("X-Page", "0") }
                header { string("X-Page-Size", "2") }
                header { string("Link", containsString("rel=\"next\"")) }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].name") { value("Аня") }
                jsonPath("$[1].name") { value("Вера") }
            }
        mockMvc.get("/api/v1/personas?page=1&size=2&sort=name,asc") { header(USER_HEADER, owner) }.andExpect {
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].name") { value("Галя") }
            header { string("Link", containsString("rel=\"prev\"")) }
        }
        mockMvc.get("/api/v1/personas") { header(USER_HEADER, owner) }.andExpect {
            jsonPath("$[0].name") { value("Галя") }
        }
        mockMvc.get("/api/v1/personas?status=READY") { header(USER_HEADER, owner) }.andExpect {
            header { string("X-Total-Count", "0") }
        }
        mockMvc.get("/api/v1/personas?size=51&sort=ownerId,asc") { header(USER_HEADER, owner) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[*].field") { value(org.hamcrest.Matchers.containsInAnyOrder("size", "sort")) }
        }
    }

    @Test
    fun `чужая персона неотличима от несуществующей - 404 на чтение, изменение, теги и архивацию`() {
        val owner = createUser()
        val stranger = createUser()
        val persona = createPersona(owner)

        mockMvc.get("/api/v1/personas/$persona") { header(USER_HEADER, stranger) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PERSONA_NOT_FOUND") }
        }
        mockMvc.get("/api/v1/personas/${UUID.randomUUID()}") { header(USER_HEADER, owner) }.andExpect { status { isNotFound() } }
        mockMvc
            .patch("/api/v1/personas/$persona") {
                header(USER_HEADER, stranger)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("name" to "Взлом"))
            }.andExpect { status { isNotFound() } }
        mockMvc
            .put("/api/v1/personas/$persona/tags") {
                header(USER_HEADER, stranger)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("tags" to emptyList<Any>()))
            }.andExpect { status { isNotFound() } }
        mockMvc.delete("/api/v1/personas/$persona") { header(USER_HEADER, stranger) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/personas/$persona/profile") { header(USER_HEADER, stranger) }.andExpect { status { isNotFound() } }
        mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, stranger) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `PATCH меняет переданные поля, пустое описание очищает его, архивную персону менять нельзя`() {
        val owner = createUser()
        val persona = createPersona(owner)
        mockMvc
            .patch("/api/v1/personas/$persona") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("name" to "Мария", "relationshipKind" to "FRIEND", "description" to "подруга"))
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Мария") }
                jsonPath("$.relationshipKind") { value("FRIEND") }
                jsonPath("$.description") { value("подруга") }
            }
        mockMvc
            .patch("/api/v1/personas/$persona") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("description" to ""))
            }.andExpect {
                jsonPath("$.name") { value("Мария") }
                jsonPath("$.description") { doesNotExist() }
            }
        mockMvc
            .patch("/api/v1/personas/$persona") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("name" to "   "))
            }.andExpect { status { isBadRequest() } }

        mockMvc.delete("/api/v1/personas/$persona") { header(USER_HEADER, owner) }.andExpect { status { isNoContent() } }
        mockMvc
            .patch("/api/v1/personas/$persona") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("name" to "Воскресшая"))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("PERSONA_INVALID_STATE") }
            }
        mockMvc
            .put("/api/v1/personas/$persona/tags") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("tags" to listOf(mapOf("code" to "cold"))))
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `замена тегов - ручные заменяются, автотеги остаются, присланный автотег становится ручным`() {
        val owner = createUser()
        val persona = createPersona(owner, tagCodes = setOf("sarcastic", "caring"))
        lifecycle.startTraining(persona, owner, UUID.randomUUID())
        lifecycle.rebuildFrom(persona, TestCorpus.snapshot())

        val before = mockMvc.get("/api/v1/personas/$persona") { header(USER_HEADER, owner) }.json()
        val autoCodes = before["tags"].toList().filter { it["source"].asString() == "AUTO" }.map { it["code"].asString() }
        assertThat(autoCodes).containsExactlyInAnyOrder("cold", "jealous", "caps", "night-owl", "laconic", "slow-replier")

        val after =
            mockMvc
                .put("/api/v1/personas/$persona/tags") {
                    header(USER_HEADER, owner)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("tags" to listOf(mapOf("code" to "jealous", "weight" to 0.5), mapOf("code" to "caring"))))
                }.andExpect { status { isOk() } }
                .json()
        val tags = after["tags"].toList().associate { it["code"].asString() to (it["source"].asString() to it["weight"].asDouble()) }
        assertThat(tags).doesNotContainKey("sarcastic")
        assertThat(tags["jealous"]).isEqualTo("MANUAL" to 0.5)
        assertThat(tags["caring"]).isEqualTo("MANUAL" to 1.0)
        assertThat(tags.filterValues { it.first == "AUTO" }.keys).containsExactlyInAnyOrder("cold", "caps", "night-owl", "laconic", "slow-replier")

        lifecycle.rebuildFrom(persona, TestCorpus.snapshot(stats = TestCorpus.warmStats()))
        val rebuilt = mockMvc.get("/api/v1/personas/$persona") { header(USER_HEADER, owner) }.json()
        val rebuiltTags = rebuilt["tags"].toList().associate { it["code"].asString() to it["source"].asString() }
        assertThat(rebuiltTags).containsEntry("jealous", "MANUAL").containsEntry("caring", "MANUAL")
        assertThat(rebuiltTags.filterValues { it == "AUTO" }.keys).containsExactlyInAnyOrder("emoji", "talkative", "fast-replier", "affectionate")
    }

    @Test
    fun `замена тегов - неизвестный код и слишком много тегов дают 400`() {
        val owner = createUser()
        val persona = createPersona(owner)
        mockMvc
            .put("/api/v1/personas/$persona/tags") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("tags" to listOf(mapOf("code" to "cold"), mapOf("code" to "unknown-tag"))))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("tags") }
            }
        mockMvc
            .put("/api/v1/personas/$persona/tags") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("tags" to (1..11).map { mapOf("code" to "cold") }))
            }.andExpect { status { isBadRequest() } }
        mockMvc
            .put("/api/v1/personas/$persona/tags") {
                header(USER_HEADER, owner)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("tags" to listOf(mapOf("code" to "cold", "weight" to 1.5))))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value(containsString("weight")) }
            }
    }

    @Test
    fun `профиль без собранного корпуса - 409 на чтение и на пересборку`() {
        val owner = createUser()
        val persona = createPersona(owner)
        mockMvc.get("/api/v1/personas/$persona/profile") { header(USER_HEADER, owner) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PERSONA_NOT_READY") }
        }
        mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, owner) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PERSONA_NOT_READY") }
        }
        mockMvc.get("/api/v1/personas/$persona/profile/versions") { header(USER_HEADER, owner) }.andExpect {
            status { isOk() }
            header { string("X-Total-Count", "0") }
        }
    }

    @Test
    fun `администратор архивирует чужую персону, владелец видит её архивной, повтор - тоже 204`() {
        val owner = createUser()
        val admin = createAdmin()
        val persona = createPersona(owner, tagCodes = setOf("cold"))

        mockMvc.delete("/api/v1/personas/$persona") { header(USER_HEADER, admin) }.andExpect { status { isNoContent() } }
        mockMvc.delete("/api/v1/personas/$persona") { header(USER_HEADER, owner) }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/personas/$persona") { header(USER_HEADER, owner) }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ARCHIVED") }
            jsonPath("$.archivedAt") { exists() }
            jsonPath("$.tags.length()") { value(0) }
        }
        mockMvc.get("/api/v1/personas") { header(USER_HEADER, owner) }.andExpect { header { string("X-Total-Count", "0") } }
        mockMvc.get("/api/v1/personas?status=ARCHIVED") { header(USER_HEADER, owner) }.andExpect {
            header { string("X-Total-Count", "1") }
            jsonPath("$[0].id") { value(persona.toString()) }
        }
        mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, owner) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PERSONA_INVALID_STATE") }
        }
    }

    @Test
    fun `обычный пользователь не может архивировать чужую персону`() {
        val owner = createUser()
        val persona = createPersona(owner)
        mockMvc.delete("/api/v1/personas/$persona") { header(USER_HEADER, createUser()) }.andExpect {
            status { isNotFound() }
            header { string("Content-Type", endsWith("problem+json")) }
        }
        mockMvc.get("/api/v1/personas/$persona") { header(USER_HEADER, owner) }.andExpect { jsonPath("$.status") { value("DRAFT") } }
    }
}
