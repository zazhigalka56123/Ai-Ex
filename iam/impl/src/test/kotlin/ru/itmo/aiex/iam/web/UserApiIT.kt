package ru.itmo.aiex.iam.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.util.UUID

class UserApiIT : AbstractIntegrationTest() {
    @Test
    fun `создание пользователя отдаёт 201 и Location, роль по умолчанию USER`() {
        val result =
            mockMvc
                .post("/api/v1/users") {
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("email" to "Masha@Example.com", "displayName" to "Маша"))
                }.andExpect {
                    status { isCreated() }
                    header { string("Location", org.hamcrest.Matchers.containsString("/api/v1/users/")) }
                    jsonPath("$.email") { value("masha@example.com") }
                    jsonPath("$.roles[0]") { value("USER") }
                    jsonPath("$.status") { value("ACTIVE") }
                }.andReturn()
        assertThat(result.response.getHeader("Location")).endsWith(result.json()["id"].asString())
    }

    @Test
    fun `дубликат email без учёта регистра - 409 EMAIL_TAKEN`() {
        val body = json(mapOf("email" to "dup@example.com", "displayName" to "Первый"))
        mockMvc.post("/api/v1/users") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }
        mockMvc
            .post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("email" to "DUP@example.com", "displayName" to "Второй"))
            }.andExpect {
                status { isConflict() }
                content { contentType("application/problem+json") }
                jsonPath("$.code") { value("EMAIL_TAKEN") }
                jsonPath("$.traceId") { exists() }
            }
    }

    @Test
    fun `невалидное тело - 400 со списком полей`() {
        mockMvc
            .post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("email" to "not-an-email", "displayName" to ""))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[*].field") { value(org.hamcrest.Matchers.containsInAnyOrder("email", "displayName", "displayName")) }
            }
    }

    @Test
    fun `тело без обязательного поля - 400 с именем поля`() {
        mockMvc
            .post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"displayName":"Без почты"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("email") }
            }
    }

    @Test
    fun `список пользователей - только администратору, с X-Total-Count и Link`() {
        val admin = createAdmin()
        repeat(3) { createUser() }
        mockMvc
            .get("/api/v1/users?page=0&size=2&sort=createdAt,asc") { header(USER_HEADER, admin) }
            .andExpect {
                status { isOk() }
                header { string("X-Total-Count", "4") }
                header { string("X-Total-Pages", "2") }
                header { string("X-Page-Size", "2") }
                header { string("Link", org.hamcrest.Matchers.containsString("rel=\"next\"")) }
                jsonPath("$.length()") { value(2) }
            }
    }

    @Test
    fun `список пользователей без заголовка - 401, обычному пользователю - 403`() {
        val user = createUser()
        mockMvc.get("/api/v1/users").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHENTICATED") }
        }
        mockMvc.get("/api/v1/users") { header(USER_HEADER, user) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `size больше 50 и неизвестное поле сортировки - 400`() {
        val admin = createAdmin()
        mockMvc.get("/api/v1/users?size=51&sort=password,asc") { header(USER_HEADER, admin) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[*].field") { value(org.hamcrest.Matchers.containsInAnyOrder("size", "sort")) }
        }
    }

    @Test
    fun `чужой профиль - 404, свой - 200`() {
        val me = createUser()
        val other = createUser()
        mockMvc.get("/api/v1/users/$me") { header(USER_HEADER, me) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/users/$other") { header(USER_HEADER, me) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_NOT_FOUND") }
        }
    }

    @Test
    fun `администратор блокирует пользователя - тот перестаёт быть актором`() {
        val admin = createAdmin()
        val user = createUser()
        mockMvc
            .patch("/api/v1/users/$user") {
                header(USER_HEADER, admin)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("status" to "BLOCKED", "roles" to listOf("USER", "SPECIALIST")))
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("BLOCKED") }
                jsonPath("$.roles.length()") { value(2) }
            }
        mockMvc.get("/api/v1/users/$user") { header(USER_HEADER, user) }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `пользователь меняет своё имя, но не свою роль`() {
        val user = createUser()
        mockMvc
            .patch("/api/v1/users/$user") {
                header(USER_HEADER, user)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("displayName" to "Новое имя"))
            }.andExpect { jsonPath("$.displayName") { value("Новое имя") } }
        mockMvc
            .patch("/api/v1/users/$user") {
                header(USER_HEADER, user)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("roles" to listOf(RoleCode.ADMIN.name)))
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `администратор не может заблокировать сам себя`() {
        val admin = createAdmin()
        mockMvc
            .patch("/api/v1/users/$admin") {
                header(USER_HEADER, admin)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("status" to "BLOCKED"))
            }.andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("USER_INVALID_STATE") }
            }
    }

    @Test
    fun `некорректный X-User-Id - 401`() {
        mockMvc.get("/api/v1/users/${UUID.randomUUID()}") { header(USER_HEADER, "not-a-uuid") }.andExpect {
            status { isUnauthorized() }
        }
        mockMvc.get("/api/v1/users/${UUID.randomUUID()}") { header(USER_HEADER, UUID.randomUUID()) }.andExpect {
            status { isUnauthorized() }
        }
    }
}
