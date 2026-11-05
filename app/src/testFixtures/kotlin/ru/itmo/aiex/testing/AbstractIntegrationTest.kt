package ru.itmo.aiex.testing

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.postgresql.PostgreSQLContainer
import ru.itmo.aiex.AiExApplication
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.integration.IdentityClient
import ru.itmo.aiex.integration.NotificationClient
import ru.itmo.aiex.security.JwtTokens
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@SpringBootTest(classes = [AiExApplication::class], properties = ["aiex.security.jwt-secret=test-only-jwt-secret-at-least-32-bytes"])
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

    @MockkBean
    protected lateinit var identityClient: IdentityClient

    @MockkBean
    protected lateinit var notificationClient: NotificationClient

    @Autowired private lateinit var webContext: WebApplicationContext
    @Autowired protected lateinit var tokens: JwtTokens

    private val users = ConcurrentHashMap<UUID, UserView>()

    @BeforeEach
    fun configureRemoteServices() {
        users.clear()
        mockMvc = MockMvcBuilders.webAppContextSetup(webContext)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
            .defaultRequest(MockMvcRequestBuilders.get("/").with(RequestPostProcessor { request ->
                val raw = request.getHeader(USER_HEADER)
                request.removeHeader(USER_HEADER)
                if (raw != null) {
                    val id = runCatching { UUID.fromString(raw) }.getOrNull()
                    val token = id?.let { tokens.userToken(it, users[it]?.roles.orEmpty()) } ?: "invalid"
                    request.addHeader("Authorization", "Bearer $token")
                } else if (request.requestURI.startsWith("/api/v1/__test/") || request.requestURI == "/api/v1/nowhere") {
                    request.addHeader("Authorization", "Bearer ${tokens.userToken(UUID.randomUUID(), setOf(RoleCode.USER))}")
                }
                request
            })).build()
        every { identityClient.findActive(any()) } answers { users[firstArg()] }
        every { identityClient.metrics() } answers { mapOf("users.active" to users.size.toLong(), "users.blocked" to 0L) }
        every { notificationClient.metrics() } returns mapOf("notifications.sent" to 0L, "notifications.pending" to 0L, "notifications.failed" to 0L)
        every { notificationClient.deliver(any(), any()) } answers { firstArg() }
    }

    protected fun createUser(vararg roles: RoleCode = arrayOf(RoleCode.USER), displayName: String = "Тестовый пользователь"): UUID {
        val id = UUID.randomUUID()
        users[id] = UserView(id, displayName, roles.toSet(), true)
        return id
    }

    protected fun blockUser(id: UUID) {
        users.remove(id)
    }

    protected fun createAdmin(): UUID = createUser(RoleCode.ADMIN, RoleCode.USER, displayName = "Администратор")

    protected fun json(value: Any): String = jsonMapper.writeValueAsString(value)

    protected fun MvcResult.json(): JsonNode = jsonMapper.readTree(response.contentAsString)

    protected fun <T> MvcResult.body(type: Class<T>): T = jsonMapper.readValue(response.contentAsString, type)

    protected fun ResultActionsDsl.json(): JsonNode = andReturn().json()

    companion object {
        @JvmField
        @ServiceConnection
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine").apply { start() }

        const val USER_HEADER = "X-Test-User"
    }
}
