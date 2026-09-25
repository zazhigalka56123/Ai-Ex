package ru.itmo.aiex.ingest.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.ingest.testing.IngestIntegrationTest
import ru.itmo.aiex.persona.service.PersonaLifecycle
import java.util.UUID

class StaleImportRecoveryIT : IngestIntegrationTest() {
    @Autowired
    private lateinit var recovery: StaleImportRecovery

    @Autowired
    private lateinit var lifecycle: PersonaLifecycle

    private fun stuckImport(owner: UUID, persona: UUID): UUID {
        val importId = Ids.next()
        jdbcTemplate.update(
            """
            INSERT INTO ingest.chat_imports
                (id, persona_id, owner_id, source, original_filename, size_bytes, status,
                 message_count, their_message_count, skipped_count, created_at, version)
            VALUES (?, ?, ?, 'TELEGRAM_JSON', 'result.json', 1024, 'PARSING', 0, 0, 0, now(), 0)
            """.trimIndent(),
            importId,
            persona,
            owner,
        )
        return importId
    }

    @Test
    fun `импорт, зависший в PARSING после рестарта, становится FAILED, персона выходит из TRAINING`() {
        val owner = createUser()
        val persona = createPersona(owner)
        lifecycle.startTraining(persona, owner, Ids.next())
        val importId = stuckImport(owner, persona)

        assertThat(recovery.releaseStuckImports()).isEqualTo(1)

        val row = jdbcTemplate.queryForMap("SELECT status, error_code, error_message FROM ingest.chat_imports WHERE id = ?", importId)
        assertThat(row["status"]).isEqualTo("FAILED")
        assertThat(row["error_code"]).isEqualTo("INTERNAL")
        assertThat(row["error_message"] as String).contains("перезапуском")
        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("DRAFT")
    }

    @Test
    fun `без зависших импортов восстановление ничего не делает`() {
        assertThat(recovery.releaseStuckImports()).isZero()
    }
}
