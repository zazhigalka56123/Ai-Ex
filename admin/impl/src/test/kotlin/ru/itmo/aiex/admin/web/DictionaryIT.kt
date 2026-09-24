package ru.itmo.aiex.admin.web

import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.admin.AdminIntegrationTest
import ru.itmo.aiex.common.dictionary.DictionaryEntry
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.RoleCode
import java.util.UUID

class DictionaryIT : AdminIntegrationTest() {
    @AfterEach
    fun removeTestSpecializations() {
        jdbcTemplate.update(
            "DELETE FROM care.specialist_specializations WHERE specialization_id IN (SELECT id FROM care.specializations WHERE code LIKE 'it-%')",
        )
        jdbcTemplate.update("DELETE FROM care.specializations WHERE code LIKE 'it-%'")
    }

    private fun create(actor: UUID?, path: String, code: String, title: String) = mockMvc.post(path) {
        actor?.let { header(USER_HEADER, it) }
        contentType = MediaType.APPLICATION_JSON
        content = json(mapOf("code" to code, "title" to title))
    }

    private fun specializationId(code: String): Long =
        jdbcTemplate.queryForObject("SELECT id FROM care.specializations WHERE code = ?", Long::class.java, code)!!

    @Test
    fun `специализации читает кто угодно - страница по коду с X-Total-Count`() {
        val total = jdbcTemplate.queryForObject("SELECT count(*) FROM care.specializations", Long::class.java)!!
        mockMvc.get("/api/v1/specializations?size=50").andExpect {
            status { isOk() }
            header { string("X-Total-Count", total.toString()) }
            jsonPath("$[0].code") { value("anxiety") }
        }
        mockMvc.get("/api/v1/specializations/${specializationId("grief")}") { header(USER_HEADER, createUser()) }.andExpect {
            status { isOk() }
            jsonPath("$.code") { value("grief") }
            jsonPath("$.title") { value("Горе и утрата") }
        }
        mockMvc.get("/api/v1/specializations/999999").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SPECIALIZATION_NOT_FOUND") }
        }
        mockMvc.get("/api/v1/specializations?sort=code,asc").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `администратор ведёт специализации - создание, переименование, удаление`() {
        val admin = createAdmin()
        val result =
            create(admin, "/api/v1/specializations", "IT-Insomnia", "Бессонница")
                .andExpect {
                    status { isCreated() }
                    header { string("Location", containsString("/api/v1/specializations/")) }
                    jsonPath("$.code") { value("it-insomnia") }
                }.andReturn()
        val id = result.json()["id"].asLong()
        assertThat(result.response.getHeader("Location")).endsWith("/api/v1/specializations/$id")

        mockMvc
            .patch("/api/v1/specializations/$id") {
                header(USER_HEADER, admin)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("title" to "Нарушения сна"))
            }.andExpect {
                status { isOk() }
                jsonPath("$.title") { value("Нарушения сна") }
                jsonPath("$.code") { value("it-insomnia") }
            }
        mockMvc.delete("/api/v1/specializations/$id") { header(USER_HEADER, admin) }.andExpect { status { isNoContent() } }
        mockMvc.get("/api/v1/specializations/$id").andExpect { status { isNotFound() } }
        mockMvc.delete("/api/v1/specializations/$id") { header(USER_HEADER, admin) }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `дубликат кода - 409, мусорный код и пустое название - 400`() {
        val admin = createAdmin()
        create(admin, "/api/v1/specializations", "BREAKUP", "Дубль").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("DICTIONARY_CODE_TAKEN") }
        }
        create(admin, "/api/v1/specializations", "плохой код", "").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[*].field") { value(org.hamcrest.Matchers.containsInAnyOrder("code", "title")) }
        }
        create(admin, "/api/v1/specializations", "it_underscore", "Подчёркивание").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("code") }
        }
    }

    @Test
    fun `используемую специализацию удалить нельзя - 409 CONSTRAINT_VIOLATED`() {
        val admin = createAdmin()
        val id = create(admin, "/api/v1/specializations", "it-used", "Используется").andReturn().json()["id"].asLong()
        val specialist = createUser(RoleCode.SPECIALIST)
        mockMvc
            .post("/api/v1/specialists") {
                header(USER_HEADER, specialist)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("headline" to "Психолог", "bio" to "Био", "pricePerHour" to "1000", "specializationCodes" to listOf("it-used")))
            }.andExpect { status { isCreated() } }

        mockMvc.delete("/api/v1/specializations/$id") { header(USER_HEADER, admin) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONSTRAINT_VIOLATED") }
        }
    }

    @Test
    fun `менять справочники может только администратор - 403, без заголовка - 401`() {
        val client = createUser()
        val grief = specializationId("grief")
        create(client, "/api/v1/specializations", "it-forbidden", "Нельзя").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        create(null, "/api/v1/specializations", "it-anonymous", "Нельзя").andExpect { status { isUnauthorized() } }
        mockMvc
            .patch("/api/v1/specializations/$grief") {
                header(USER_HEADER, client)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("title" to "Взлом"))
            }.andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/specializations/$grief") { header(USER_HEADER, client) }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/specializations/$grief").andExpect { jsonPath("$.title") { value("Горе и утрата") } }
    }

    @Test
    fun `теги - тот же CRUD поверх TagCatalog из persona`() {
        val admin = createAdmin()
        val cold = DictionaryEntry(1, "cold", "Холодная")
        every { tags.list(any()) } returns PageView(listOf(cold), page = 0, size = 20, totalElements = 1)
        every { tags.get(1) } returns cold
        every { tags.get(404) } throws NotFoundException.of(ErrorCode.TAG_NOT_FOUND, 404)
        every { tags.create("caps", "Пишет капсом") } returns DictionaryEntry(2, "caps", "Пишет капсом")
        every { tags.update(1, "Ледяная") } returns cold.copy(title = "Ледяная")
        every { tags.delete(1) } just runs

        mockMvc.get("/api/v1/tags").andExpect {
            status { isOk() }
            header { string("X-Total-Count", "1") }
            jsonPath("$[0].code") { value("cold") }
        }
        mockMvc.get("/api/v1/tags/1").andExpect { jsonPath("$.title") { value("Холодная") } }
        mockMvc.get("/api/v1/tags/404").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("TAG_NOT_FOUND") }
        }
        create(admin, "/api/v1/tags", "caps", "Пишет капсом").andExpect {
            status { isCreated() }
            header { string("Location", endsWith("/api/v1/tags/2")) }
        }
        mockMvc
            .patch("/api/v1/tags/1") {
                header(USER_HEADER, admin)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("title" to "Ледяная"))
            }.andExpect { jsonPath("$.title") { value("Ледяная") } }
        mockMvc.delete("/api/v1/tags/1") { header(USER_HEADER, admin) }.andExpect { status { isNoContent() } }
        verify(exactly = 1) { tags.delete(1) }

        create(createUser(), "/api/v1/tags", "nope", "Нельзя").andExpect { status { isForbidden() } }
        verify(exactly = 1) { tags.create(any(), any()) }
    }
}
