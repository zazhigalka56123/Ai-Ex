package ru.itmo.aiex.admin.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import ru.itmo.aiex.admin.AdminIntegrationTest
class MetricsIT : AdminIntegrationTest() {
    @Test
    fun `администратор видит агрегаты всех модулей, отсортированные по ключу`() {
        val admin = createAdmin()
        val client = createUser()
        reportOk(client, message(ownerId = client))

        val body =
            mockMvc
                .get("/api/v1/admin/metrics") { header(USER_HEADER, admin) }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.generatedAt") { exists() }
                    jsonPath("$.metrics['users.active']") { value(2) }
                    jsonPath("$.metrics['flags.open']") { value(1) }
                    jsonPath("$.metrics['flags.resolved']") { value(0) }
                    jsonPath("$.metrics['specialists.active']") { value(0) }
                    jsonPath("$.metrics['consultations.requested']") { value(0) }
                }.andReturn()
                .json()
        val keys = body["metrics"].propertyNames().toList()
        assertThat(keys).isSorted()
        assertThat(keys).contains("users.blocked", "flags.in_review", "flags.rejected", "consultations.done", "consultations.cancelled")
    }

    @Test
    fun `метрики - только администратору`() {
        mockMvc.get("/api/v1/admin/metrics") { header(USER_HEADER, createUser()) }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        mockMvc.get("/api/v1/admin/metrics").andExpect { status { isUnauthorized() } }
    }
}
