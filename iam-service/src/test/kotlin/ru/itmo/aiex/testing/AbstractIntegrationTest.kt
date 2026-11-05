package ru.itmo.aiex.testing

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.IamApplication
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest(
    classes = [IamApplication::class],
    properties = [
        "spring.cloud.config.enabled=false", "spring.config.import=", "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false", "spring.liquibase.contexts=test", "spring.jpa.hibernate.ddl-auto=validate",
    ],
)
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {
    @Autowired protected lateinit var mockMvc: MockMvc

    @Autowired protected lateinit var jsonMapper: JsonMapper

    @Autowired protected lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE iam.users CASCADE")
    }

    protected fun createUser(vararg roles: RoleCode = arrayOf(RoleCode.USER), displayName: String = "Пользователь"): UUID {
        val result = mockMvc.post("/api/v1/users") {
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("email" to "${UUID.randomUUID()}@test.local", "displayName" to displayName, "roles" to roles))
        }.andReturn()
        val resolved = if (result.request.isAsyncStarted) {
            mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result),
            ).andReturn()
        } else {
            result
        }
        return UUID.fromString(resolved.json()["id"].asString())
    }

    protected fun createAdmin(): UUID = createUser(RoleCode.ADMIN, RoleCode.USER)
    protected fun json(value: Any): String = jsonMapper.writeValueAsString(value)
    protected fun MvcResult.json(): JsonNode = jsonMapper.readTree(response.contentAsString)
    protected fun ResultActionsDsl.json(): JsonNode = andReturn().json()

    companion object {
        const val USER_HEADER = "X-User-Id"
        val postgres = PostgreSQLContainer("postgres:17-alpine").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
