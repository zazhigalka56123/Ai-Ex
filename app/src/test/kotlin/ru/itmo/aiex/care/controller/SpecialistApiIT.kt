package ru.itmo.aiex.care.controller

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.care.CareIntegrationTest
import ru.itmo.aiex.common.security.RoleCode
import java.util.UUID

class SpecialistApiIT : CareIntegrationTest() {
    @Test
    fun `специалист создаёт свой профиль - 201, Location, имя из iam и специализации из справочника`() {
        val userId = createUser(RoleCode.SPECIALIST, displayName = "Анна Психолог")
        val result =
            mockMvc
                .post("/api/v1/specialists") {
                    header(USER_HEADER, userId)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(specialistBody(codes = listOf("Relationships", "breakup")))
                }.andExpect {
                    status { isCreated() }
                    header { string("Location", containsString("/api/v1/specialists/")) }
                    jsonPath("$.userId") { value(userId.toString()) }
                    jsonPath("$.displayName") { value("Анна Психолог") }
                    jsonPath("$.status") { value("ACTIVE") }
                    jsonPath("$.bookedCount") { value(0) }
                    jsonPath("$.pricePerHour") { value(2500.0) }
                    jsonPath("$.specializations[*].code") { value(contains("breakup", "relationships")) }
                    jsonPath("$.specializations[0].title") { value("Расставание") }
                }.andReturn()
        assertThat(result.response.getHeader("Location")).endsWith(result.json()["id"].asString())
    }

    @Test
    fun `профиль создаёт только роль SPECIALIST - 403`() {
        val client = createUser()
        mockMvc
            .post("/api/v1/specialists") {
                header(USER_HEADER, client)
                contentType = MediaType.APPLICATION_JSON
                content = json(specialistBody())
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
    }

    @Test
    fun `второй профиль того же пользователя - 409 SPECIALIST_PROFILE_EXISTS`() {
        val specialist = createSpecialist()
        mockMvc
            .post("/api/v1/specialists") {
                header(USER_HEADER, specialist.userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(specialistBody())
            }.andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("SPECIALIST_PROFILE_EXISTS") }
            }
    }

    @Test
    fun `неизвестная специализация, отрицательная цена и пустой набор - 400 с именами полей`() {
        val userId = createUser(RoleCode.SPECIALIST)
        mockMvc
            .post("/api/v1/specialists") {
                header(USER_HEADER, userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(specialistBody(codes = listOf("breakup", "astrology")))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("specializationCodes") }
                jsonPath("$.errors[0].code") { value("unknown") }
                jsonPath("$.errors[0].message") { value(containsString("astrology")) }
            }
        mockMvc
            .post("/api/v1/specialists") {
                header(USER_HEADER, userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(specialistBody(codes = emptyList(), price = "-1.00"))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[*].field") { value(org.hamcrest.Matchers.containsInAnyOrder("pricePerHour", "specializationCodes")) }
            }
    }

    @Test
    fun `каталог - только активные, фильтр по специализации, X-Total-Count и сортировка по цене`() {
        val cheap = createSpecialist(codes = listOf("breakup"), price = "1500.00")
        val expensive = createSpecialist(codes = listOf("breakup", "relationships"), price = "4000.00")
        createSpecialist(codes = listOf("anxiety"), price = "1000.00")
        val hidden = createSpecialist(codes = listOf("breakup"), price = "500.00")
        mockMvc
            .patch("/api/v1/specialists/${hidden.id}") {
                header(USER_HEADER, hidden.userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("status" to "INACTIVE"))
            }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/specialists?specialization=BREAKUP&sort=pricePerHour,asc").andExpect {
            status { isOk() }
            header { string("X-Total-Count", "2") }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].id") { value(cheap.id.toString()) }
            jsonPath("$[1].id") { value(expensive.id.toString()) }
            jsonPath("$[0].displayName") { value("Анна Психолог") }
        }
        mockMvc.get("/api/v1/specialists?size=2&sort=pricePerHour,desc").andExpect {
            status { isOk() }
            header { string("X-Total-Count", "3") }
            header { string("X-Total-Pages", "2") }
            header { string("Link", containsString("rel=\"next\"")) }
            jsonPath("$[0].id") { value(expensive.id.toString()) }
        }
        mockMvc.get("/api/v1/specialists?specialization=grief").andExpect {
            header { string("X-Total-Count", "0") }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `сортировка не из whitelist - 400`() {
        mockMvc.get("/api/v1/specialists?sort=bookedCount,desc").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("sort") }
        }
    }

    @Test
    fun `свой профиль правит владелец, чужой - 403, администратор - любой`() {
        val owner = createSpecialist()
        val other = createSpecialist()
        val admin = createAdmin()
        mockMvc
            .patch("/api/v1/specialists/${owner.id}") {
                header(USER_HEADER, owner.userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("headline" to "  Новый заголовок ", "pricePerHour" to "3000.50", "specializationCodes" to listOf("grief")))
            }.andExpect {
                status { isOk() }
                jsonPath("$.headline") { value("Новый заголовок") }
                jsonPath("$.pricePerHour") { value(3000.5) }
                jsonPath("$.specializations[*].code") { value(contains("grief")) }
            }
        mockMvc
            .patch("/api/v1/specialists/${owner.id}") {
                header(USER_HEADER, other.userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("headline" to "Чужой"))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("FORBIDDEN") }
            }
        mockMvc
            .patch("/api/v1/specialists/${owner.id}") {
                header(USER_HEADER, admin)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("status" to "INACTIVE"))
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("INACTIVE") }
            }
    }

    @Test
    fun `неактивный профиль виден владельцу и администратору, остальным - 404`() {
        val owner = createSpecialist()
        val client = createUser()
        val admin = createAdmin()
        mockMvc
            .patch("/api/v1/specialists/${owner.id}") {
                header(USER_HEADER, owner.userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("status" to "INACTIVE"))
            }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/specialists/${owner.id}") { header(USER_HEADER, owner.userId) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/specialists/${owner.id}") { header(USER_HEADER, admin) }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/specialists/${owner.id}") { header(USER_HEADER, client) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SPECIALIST_NOT_FOUND") }
        }
        mockMvc.get("/api/v1/specialists/${owner.id}").andExpect { status { isNotFound() } }
        mockMvc
            .patch("/api/v1/specialists/${owner.id}") {
                header(USER_HEADER, createSpecialist().userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("headline" to "Чужой"))
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `пустой заголовок в PATCH - 400, несуществующий профиль - 404`() {
        val owner = createSpecialist()
        mockMvc
            .patch("/api/v1/specialists/${owner.id}") {
                header(USER_HEADER, owner.userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("headline" to "   "))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("headline") }
            }
        mockMvc.get("/api/v1/specialists/${UUID.randomUUID()}").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SPECIALIST_NOT_FOUND") }
        }
    }

    @Test
    fun `имя заблокированного пользователя в профиле не раскрывается`() {
        val specialist = createSpecialist()
        val admin = createAdmin()
        mockMvc
            .patch("/api/v1/users/${specialist.userId}") {
                header(USER_HEADER, admin)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("status" to "BLOCKED"))
            }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/specialists/${specialist.id}").andExpect {
            status { isOk() }
            jsonPath("$.displayName") { value(org.hamcrest.Matchers.nullValue()) }
        }
    }
}
