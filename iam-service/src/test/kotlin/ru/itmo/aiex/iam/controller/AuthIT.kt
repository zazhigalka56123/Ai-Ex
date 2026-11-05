package ru.itmo.aiex.iam.controller

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.util.UUID

class AuthIT : AbstractIntegrationTest() {
    @Test
    fun `вход супервайзера и создание пользователя сохраняют только хэш`() {
        val admin = createAdmin()
        val token = login(admin).json()["accessToken"].asString()
        val result = resolve(mockMvc.post("/api/v1/users") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("email" to "new@example.com", "displayName" to "Новый", "password" to TEST_PASSWORD))
        }.andReturn())
        assertThat(result.response.status).isEqualTo(201)
        assertThat(result.json().has("password")).isFalse()
        assertThat(result.json().has("passwordHash")).isFalse()
        val hash = jdbcTemplate.queryForObject("SELECT password_hash FROM iam.users WHERE email = 'new@example.com'", String::class.java)!!
        assertThat(hash).isNotEqualTo(TEST_PASSWORD)
        assertThat(BCryptPasswordEncoder().matches(TEST_PASSWORD, hash)).isTrue()
        val id = UUID.fromString(result.json()["id"].asString())
        assertThat(login(id).response.status).isEqualTo(200)
        val profile = resolve(mockMvc.get("/api/v1/users/$id") { header("Authorization", "Bearer $token") }.andReturn())
        assertThat(profile.response.contentAsString).doesNotContain("password", hash)
    }

    @Test
    fun `USER и SPECIALIST не создают пользователей даже с запросом роли ADMIN`() {
        for (role in listOf(RoleCode.USER, RoleCode.SPECIALIST)) {
            val user = createUser(role)
            val token = login(user).json()["accessToken"].asString()
            val response = resolve(mockMvc.post("/api/v1/users") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf(
                    "email" to "escalation@example.com", "displayName" to "X", "password" to TEST_PASSWORD, "roles" to listOf("ADMIN"),
                ))
            }.andReturn())
            assertThat(response.response.status).isEqualTo(403)
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM iam.users WHERE email = 'escalation@example.com'", Long::class.java)).isZero()
    }

    @Test
    fun `старый заголовок и поврежденный JWT не авторизуют запрос`() {
        val admin = createAdmin()
        mockMvc.get("/api/v1/users") { header("X-User-Id", admin) }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/api/v1/users") { header("Authorization", "Bearer broken") }.andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/v1/users") {
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("email" to "anonymous@example.com", "displayName" to "X", "password" to TEST_PASSWORD))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `ошибка входа одинакова для неверного пароля неизвестного и заблокированного пользователя`() {
        val user = createUser()
        val wrong = login(user, "Wrong-password-123!")
        assertThat(wrong.response.status).isEqualTo(401)
        val missing = resolve(mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("email" to "missing@example.com", "password" to TEST_PASSWORD))
        }.andReturn())
        assertThat(missing.response.status).isEqualTo(401)
        assertThat(missing.json()["detail"]).isEqualTo(wrong.json()["detail"])
        val token = login(user).json()["accessToken"].asString()
        jdbcTemplate.update("UPDATE iam.users SET status = 'BLOCKED' WHERE id = ?", user)
        assertThat(login(user).response.status).isEqualTo(401)
        val blocked = resolve(mockMvc.get("/api/v1/users/$user") { header("Authorization", "Bearer $token") }.andReturn())
        assertThat(blocked.response.status).isEqualTo(401)
    }

    @Test
    fun `служебный JWT не подходит публичному API а пользовательский внутреннему`() {
        val user = createAdmin()
        val token = login(user).json()["accessToken"].asString()
        mockMvc.get("/internal/metrics") { header("Authorization", "Bearer $token") }.andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/users") { header("Authorization", "Bearer ${tokens.serviceToken()}") }.andExpect { status { isForbidden() } }
        val metrics = resolve(mockMvc.get("/internal/metrics") { header("Authorization", "Bearer ${tokens.serviceToken()}") }.andReturn())
        assertThat(metrics.response.status).isEqualTo(200)
    }

    private fun login(id: UUID, password: String = TEST_PASSWORD): MvcResult {
        val email = jdbcTemplate.queryForObject("SELECT email FROM iam.users WHERE id = ?", String::class.java, id)!!
        return resolve(mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("email" to email, "password" to password))
        }.andReturn())
    }

    private fun resolve(result: MvcResult): MvcResult =
        if (result.request.isAsyncStarted) mockMvc.perform(asyncDispatch(result)).andReturn() else result
}
