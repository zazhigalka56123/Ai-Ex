package ru.itmo.aiex.testing

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.itmo.aiex.AiExApplication
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.web.AiExHeaders
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(classes = [AiExApplication::class])
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var jsonMapper: JsonMapper

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        DatabaseCleaner(jdbcTemplate).clean()
    }

    protected fun createUser(vararg roles: RoleCode = arrayOf(RoleCode.USER), displayName: String = "Тестовый пользователь"): UUID {
        val email = "user-${SEQUENCE.incrementAndGet()}-${UUID.randomUUID().toString().take(8)}@test.local"
        val body = mapOf("email" to email, "displayName" to displayName, "roles" to roles.map { it.name })
        val result =
            mockMvc
                .post("/api/v1/users") {
                    contentType = MediaType.APPLICATION_JSON
                    content = json(body)
                }.andExpect { status { isCreated() } }
                .andReturn()
        return UUID.fromString(result.json()["id"].asString())
    }

    protected fun createAdmin(): UUID = createUser(RoleCode.ADMIN, RoleCode.USER, displayName = "Администратор")

    protected fun json(value: Any): String = jsonMapper.writeValueAsString(value)

    protected fun MvcResult.json(): JsonNode = jsonMapper.readTree(response.contentAsString)

    protected fun <T> MvcResult.body(type: Class<T>): T = jsonMapper.readValue(response.contentAsString, type)

    protected fun ResultActionsDsl.json(): JsonNode = andReturn().json()

    companion object {
        private val SEQUENCE = AtomicInteger()

        @JvmField
        @ServiceConnection
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine").apply { start() }

        const val USER_HEADER = AiExHeaders.USER_ID
    }
}
