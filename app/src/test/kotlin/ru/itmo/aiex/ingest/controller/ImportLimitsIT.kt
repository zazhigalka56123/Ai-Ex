package ru.itmo.aiex.ingest.controller

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import ru.itmo.aiex.ingest.testing.IngestIntegrationTest
import java.time.Duration

@TestPropertySource(properties = ["aiex.import.max-file-mb=1", "aiex.import.async-threshold-kb=1", "aiex.import.max-messages=20"])
class ImportLimitsIT : IngestIntegrationTest() {
    @Test
    fun `файл больше лимита - 413 FILE_TOO_LARGE, импорт не создаётся`() {
        val owner = createUser()
        val persona = createPersona(owner)
        val content = ("31.12.2023, 23:41 - Маша: " + "а".repeat(100) + "\n").repeat(10_000).toByteArray()
        assertThat(content.size).isGreaterThan(1024 * 1024)

        uploadBytes(owner, persona, content, "big.txt").andExpect {
            status { isEqualTo(413) }
            jsonPath("$.code") { value("FILE_TOO_LARGE") }
        }
        assertThat(countRows("SELECT count(*) FROM ingest.chat_imports WHERE persona_id = ?", persona)).isZero()
    }

    @Test
    fun `крупный файл разбирается асинхронно - 202 сразу, READY позже`() {
        val owner = createUser()
        val persona = createPersona(owner)
        val pair = "31.12.2023, 23:41 - Алексей: привет, спишь?\n31.12.2023, 23:50 - Маша: ну привет, что надо? я вообще-то уже почти сплю\n"
        val content = pair.repeat(8).toByteArray()
        assertThat(content.size).isGreaterThan(1024)

        val importId =
            uploadBytes(owner, persona, content, "chat.txt")
                .andExpect { status { isAccepted() } }
                .json()["id"]
                .asString()

        await atMost Duration.ofSeconds(20) untilAsserted {
            assertThat(persona(owner, persona)["status"].asString()).isEqualTo("READY")
        }
        val parsed = import(owner, importId)
        assertThat(parsed["status"].asString()).isEqualTo("PARSED")
        assertThat(parsed["messageCount"].asInt()).isEqualTo(16)
    }

    @Test
    fun `сообщений больше лимита - TOO_MANY_MESSAGES, персона возвращается в DRAFT`() {
        val owner = createUser()
        val persona = createPersona(owner)
        val importId = upload(owner, persona, "telegram/personal_chat.json").andExpect { status { isAccepted() } }.json()["id"].asString()

        await atMost Duration.ofSeconds(20) untilAsserted {
            assertThat(import(owner, importId)["status"].asString()).isEqualTo("FAILED")
        }
        assertThat(import(owner, importId)["errorCode"].asString()).isEqualTo("TOO_MANY_MESSAGES")
        await atMost Duration.ofSeconds(20) untilAsserted {
            assertThat(persona(owner, persona)["status"].asString()).isEqualTo("DRAFT")
        }
    }
}
