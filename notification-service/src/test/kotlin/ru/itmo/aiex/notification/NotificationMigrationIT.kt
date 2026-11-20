package ru.itmo.aiex.notification

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import reactor.test.StepVerifier

@ActiveProfiles("test")
@SpringBootTest(
    properties = [
        "ai-ex.security.jwt-secret=test-only-jwt-secret-at-least-32-bytes",
        "spring.cloud.config.enabled=false", "spring.config.import=", "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
    ],
)
class NotificationMigrationIT {
    @Autowired lateinit var database: DatabaseClient

    @Test
    fun `Liquibase принимает старую таблицу без потери данных и добавляет ограничения`() {
        val payload = database.sql("SELECT payload FROM notifications")
            .map { row -> row.get("payload", String::class.java)!! }.one()
        StepVerifier.create(payload)
            .expectNext("{\"versionNo\":2}").verifyComplete()
        val migrations = database.sql("SELECT count(*) AS count FROM databasechangelog")
            .map { row -> row.get("count", Long::class.javaObjectType)!! }.one()
        StepVerifier.create(migrations).assertNext { assertThat(it).isEqualTo(3L) }.verifyComplete()
        val columns = database.sql("SELECT column_name FROM information_schema.columns WHERE table_name = 'notifications'")
            .map { row -> row.get("column_name", String::class.java)!! }.all().collectList()
        StepVerifier.create(columns).assertNext {
            assertThat(it).containsExactlyInAnyOrder("id", "recipient_id", "type", "payload", "created_at")
        }.verifyComplete()
        StepVerifier.create(database.sql("UPDATE notifications SET payload = '[]'").fetch().rowsUpdated())
            .expectError(DataIntegrityViolationException::class.java).verify()
    }

    companion object {
        val postgres = PostgreSQLContainer("postgres:17-alpine")
            .waitingFor(Wait.forListeningPort())
            .withInitScript("db/legacy-notifications.sql").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.liquibase.url", postgres::getJdbcUrl)
            registry.add("spring.liquibase.user", postgres::getUsername)
            registry.add("spring.liquibase.password", postgres::getPassword)
        }
    }
}
