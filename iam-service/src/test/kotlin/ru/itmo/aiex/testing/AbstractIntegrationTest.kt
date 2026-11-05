package ru.itmo.aiex.testing

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.IamApplication
import ru.itmo.aiex.iam.repository.UserRepository
import ru.itmo.aiex.iam.service.RegisterUserCommand
import ru.itmo.aiex.iam.service.UserService
import ru.itmo.aiex.security.JwtTokens
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest(
    classes = [IamApplication::class],
    properties = [
        "aiex.security.jwt-secret=test-only-jwt-secret-at-least-32-bytes",
        "spring.cloud.config.enabled=false", "spring.config.import=", "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false", "spring.liquibase.contexts=test", "spring.jpa.hibernate.ddl-auto=validate",
    ],
)
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {
    @Autowired protected lateinit var mockMvc: MockMvc

    @Autowired protected lateinit var jsonMapper: JsonMapper

    @Autowired protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired protected lateinit var tokens: JwtTokens
    @Autowired private lateinit var webContext: WebApplicationContext
    @Autowired private lateinit var userService: UserService
    @Autowired private lateinit var userRepository: UserRepository

    @BeforeEach
    fun cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE iam.users CASCADE")
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
            .defaultRequest(MockMvcRequestBuilders.get("/").with(RequestPostProcessor { request ->
                val raw = request.getHeader(USER_HEADER)
                request.removeHeader(USER_HEADER)
                if (raw != null) {
                    val id = runCatching { UUID.fromString(raw) }.getOrNull()
                    val token = id?.let { tokens.userToken(it, userRepository.findById(it)?.roleCodes.orEmpty()) } ?: "invalid"
                    request.addHeader("Authorization", "Bearer $token")
                }
                request
            })).build()
    }

    protected fun createUser(vararg roles: RoleCode = arrayOf(RoleCode.USER), displayName: String = "Пользователь"): UUID {
        return userService.registerUser(
            Actor(UUID(0, 0), setOf(RoleCode.ADMIN)),
            RegisterUserCommand("${UUID.randomUUID()}@test.local", displayName, roles.toSet(), TEST_PASSWORD),
        ).id
    }

    protected fun createAdmin(): UUID = createUser(RoleCode.ADMIN, RoleCode.USER)
    protected fun json(value: Any): String = jsonMapper.writeValueAsString(value)
    protected fun MvcResult.json(): JsonNode = jsonMapper.readTree(response.contentAsString)
    protected fun ResultActionsDsl.json(): JsonNode = andReturn().json()

    companion object {
        const val USER_HEADER = "X-Test-User"
        const val TEST_PASSWORD = "Test-password-123!"
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
