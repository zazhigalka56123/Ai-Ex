package ru.itmo.aiex.ingest.controller

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.common.events.ChatImportFailed
import ru.itmo.aiex.common.events.ChatImportParsed
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.ingest.testing.IngestIntegrationTest
import ru.itmo.aiex.persona.service.PersonaLifecycle
import java.util.UUID

@RecordApplicationEvents
class ImportFlowIT : IngestIntegrationTest() {
    @Autowired
    private lateinit var events: ApplicationEvents

    @Autowired
    private lateinit var lifecycle: PersonaLifecycle

    @Autowired
    private lateinit var metrics: List<MetricsContributor>

    @Test
    fun `выгрузка Telegram - 202 и Location, импорт PARSED, персона READY, в промпте характерные фразы`() {
        val owner = createUser()
        val persona = createPersona(owner)

        val result =
            upload(owner, persona, "telegram/personal_chat.json")
                .andExpect {
                    status { isAccepted() }
                    header { string("Location", containsString("/api/v1/imports/")) }
                    jsonPath("$.status") { value("PARSED") }
                    jsonPath("$.source") { value("TELEGRAM_JSON") }
                    jsonPath("$.personaId") { value(persona.toString()) }
                    jsonPath("$.originalFilename") { value("personal_chat.json") }
                    jsonPath("$.messageCount") { value(25) }
                    jsonPath("$.theirMessageCount") { value(15) }
                    jsonPath("$.skippedCount") { value(3) }
                    jsonPath("$.theirName") { value("Маша") }
                    jsonPath("$.errorCode") { doesNotExist() }
                    jsonPath("$.finishedAt") { exists() }
                }.andReturn()
        val importId = result.json()["id"].asString()
        assertThat(result.response.getHeader("Location")).endsWith(importId)

        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("READY")
        mockMvc.get("/api/v1/personas/$persona/profile") { header(USER_HEADER, owner) }.andExpect {
            status { isOk() }
            jsonPath("$.versionNo") { value(1) }
            jsonPath("$.systemPrompt") { value(containsString("- «неа»")) }
            jsonPath("$.systemPrompt") { value(containsString("- «с кем?»")) }
            jsonPath("$.style.replySpeed") { value("slow") }
            jsonPath("$.corpusStats.avgReplyDelaySeconds") { value(7780) }
        }
        val tags = persona(owner, persona)["tags"].toList().map { it["code"].asString() }
        assertThat(tags).contains("jealous", "night-owl", "laconic", "slow-replier")

        assertThat(events.stream(ChatImportParsed::class.java).toList().map { it.importId.toString() }).containsExactly(importId)
        assertThat(countRows("SELECT count(*) FROM ingest.imported_messages WHERE import_id = ?::uuid", importId)).isEqualTo(25)
    }

    @Test
    fun `сообщения импорта - курсорные страницы без дублей и пропусков, без X-Total-Count`() {
        val owner = createUser()
        val importId = upload(owner, createPersona(owner), "telegram/personal_chat.json").json()["id"].asString()

        val ordinals = mutableListOf<Int>()
        var cursor: String? = null
        var pages = 0
        do {
            val page =
                mockMvc
                    .get("/api/v1/imports/$importId/messages?limit=10${cursor?.let { "&cursor=$it" } ?: ""}") { header(USER_HEADER, owner) }
                    .andExpect {
                        status { isOk() }
                        header { doesNotExist("X-Total-Count") }
                    }.json()
            ordinals += page["items"].toList().map { it["ordinal"].asInt() }
            cursor = page["nextCursor"].takeUnless { it.isNull }?.asString()
            pages++
        } while (cursor != null)

        assertThat(pages).isEqualTo(3)
        assertThat(ordinals).containsExactlyElementsOf(0 until 25)
        mockMvc.get("/api/v1/imports/$importId/messages?limit=2") { header(USER_HEADER, owner) }.andExpect {
            jsonPath("$.items[0].author") { value("ME") }
            jsonPath("$.items[0].body") { value("привет, спишь?") }
            jsonPath("$.items[0].sentAt") { value("2024-03-01T20:10:00Z") }
            jsonPath("$.items[1].author") { value("THEM") }
        }
        mockMvc.get("/api/v1/imports/$importId/messages?cursor=@@@") { header(USER_HEADER, owner) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("cursor") }
        }
        mockMvc.get("/api/v1/imports/$importId/messages?limit=51") { header(USER_HEADER, owner) }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `выгрузки WhatsApp и простого текста - автор персоны определяется по имени персоны или по theirName`() {
        val owner = createUser()
        val whatsapp = createPersona(owner)
        upload(owner, whatsapp, "whatsapp/android_ru.txt").andExpect {
            status { isAccepted() }
            jsonPath("$.source") { value("WHATSAPP_TXT") }
            jsonPath("$.status") { value("PARSED") }
            jsonPath("$.messageCount") { value(7) }
            jsonPath("$.theirMessageCount") { value(4) }
            jsonPath("$.skippedCount") { value(3) }
        }
        assertThat(persona(owner, whatsapp)["status"].asString()).isEqualTo("READY")

        val plain = createPersona(owner, name = "Мария")
        upload(owner, plain, "plain/chat.txt", params = mapOf("source" to "PLAIN_TEXT", "theirName" to "маша")).andExpect {
            status { isAccepted() }
            jsonPath("$.source") { value("PLAIN_TEXT") }
            jsonPath("$.theirName") { value("Маша") }
            jsonPath("$.theirMessageCount") { value(2) }
        }
        assertThat(persona(owner, plain)["status"].asString()).isEqualTo("READY")
    }

    @Test
    fun `автор не определён - FAILED с перечнем авторов, повтор с theirName проходит`() {
        val owner = createUser()
        val persona = createPersona(owner, name = "Катя")
        upload(owner, persona, "whatsapp/ios.txt").andExpect {
            status { isAccepted() }
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.errorCode") { value("AUTHOR_NOT_DETECTED") }
            jsonPath("$.errorMessage") { value(containsString("Алексей, Маша")) }
        }
        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("DRAFT")
        upload(owner, persona, "whatsapp/ios.txt", params = mapOf("theirName" to "Маша")).andExpect { jsonPath("$.status") { value("PARSED") } }
        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("READY")
    }

    @Test
    fun `неподдерживаемый формат - 415 до создания импорта, пустой файл и отсутствие файла - 400`() {
        val owner = createUser()
        val persona = createPersona(owner)
        upload(owner, persona, "other/document.pdf").andExpect {
            status { isUnsupportedMediaType() }
            jsonPath("$.code") { value("UNSUPPORTED_FORMAT") }
        }
        upload(owner, persona, "other/notes.txt").andExpect { status { isUnsupportedMediaType() } }
        upload(owner, persona, "other/document.pdf", params = mapOf("source" to "TELEGRAM_JSON")).andExpect { status { isUnsupportedMediaType() } }
        uploadBytes(owner, persona, ByteArray(0), "empty.json").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("file") }
        }
        mockMvc.multipart("/api/v1/personas/$persona/imports") { header(USER_HEADER, owner) }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("file") }
        }
        upload(owner, persona, "telegram/personal_chat.json", params = mapOf("source" to "FAX")).andExpect { status { isBadRequest() } }

        assertThat(countRows("SELECT count(*) FROM ingest.chat_imports WHERE persona_id = ?", persona)).isZero()
        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("DRAFT")
    }

    @Test
    fun `битый JSON - импорт FAILED с MALFORMED_FILE, персона возвращается в DRAFT, событие ChatImportFailed`() {
        val owner = createUser()
        val persona = createPersona(owner)
        val importId =
            upload(owner, persona, "telegram/broken.json")
                .andExpect {
                    status { isAccepted() }
                    jsonPath("$.status") { value("FAILED") }
                    jsonPath("$.errorCode") { value("MALFORMED_FILE") }
                    jsonPath("$.errorMessage") { value(startsWith("Файл не является корректным JSON")) }
                }.json()["id"]
                .asString()
        assertThat(import(owner, importId)["errorCode"].asString()).isEqualTo("MALFORMED_FILE")
        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("DRAFT")
        val failed = events.stream(ChatImportFailed::class.java).toList().single()
        assertThat(failed.errorCode).isEqualTo("MALFORMED_FILE")
        assertThat(failed.personaId).isEqualTo(persona)
    }

    @Test
    fun `пустая выгрузка - EMPTY_CORPUS, выгрузка аккаунта - MALFORMED_FILE`() {
        val owner = createUser()
        val persona = createPersona(owner)
        upload(owner, persona, "telegram/empty.json").andExpect {
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.errorCode") { value("EMPTY_CORPUS") }
        }
        upload(owner, persona, "telegram/full_account.json").andExpect {
            jsonPath("$.errorCode") { value("MALFORMED_FILE") }
            jsonPath("$.errorMessage") { value(containsString("всего аккаунта")) }
        }
        assertThat(persona(owner, persona)["status"].asString()).isEqualTo("DRAFT")
    }

    @Test
    fun `новая выгрузка во время обучения - 409, отклонённый импорт помечен PERSONA_BUSY`() {
        val owner = createUser()
        val persona = createPersona(owner)
        lifecycle.startTraining(persona, owner, UUID.randomUUID())

        upload(owner, persona, "telegram/personal_chat.json").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PERSONA_INVALID_STATE") }
        }
        mockMvc.get("/api/v1/personas/$persona/imports") { header(USER_HEADER, owner) }.andExpect {
            header { string("X-Total-Count", "1") }
            jsonPath("$[0].status") { value("FAILED") }
            jsonPath("$[0].errorCode") { value("PERSONA_BUSY") }
        }
    }

    @Test
    fun `две успешные выгрузки - версии профиля 1 и 2, активна ровно одна`() {
        val owner = createUser()
        val persona = createPersona(owner)
        upload(owner, persona, "telegram/personal_chat.json").andExpect { jsonPath("$.status") { value("PARSED") } }
        upload(owner, persona, "whatsapp/android_ru.txt").andExpect { jsonPath("$.status") { value("PARSED") } }

        mockMvc.get("/api/v1/personas/$persona/profile/versions") { header(USER_HEADER, owner) }.andExpect {
            header { string("X-Total-Count", "2") }
            jsonPath("$[0].versionNo") { value(2) }
            jsonPath("$[0].active") { value(true) }
            jsonPath("$[1].versionNo") { value(1) }
            jsonPath("$[1].active") { value(false) }
        }
        assertThat(countRows("SELECT count(*) FROM persona.persona_profile_versions WHERE persona_id = ? AND active", persona)).isEqualTo(1)
        mockMvc.get("/api/v1/personas/$persona/profile") { header(USER_HEADER, owner) }.andExpect {
            jsonPath("$.versionNo") { value(2) }
            jsonPath("$.systemPrompt") { value(containsString("- «ну ок»")) }
        }
        mockMvc.get("/api/v1/personas/$persona/imports?sort=createdAt,asc") { header(USER_HEADER, owner) }.andExpect {
            header { string("X-Total-Count", "2") }
            jsonPath("$[0].source") { value("TELEGRAM_JSON") }
            jsonPath("$[1].source") { value("WHATSAPP_TXT") }
        }
    }

    @Test
    fun `profile_rebuild после импорта идемпотентен`() {
        val owner = createUser()
        val persona = createPersona(owner)
        upload(owner, persona, "telegram/personal_chat.json")
        repeat(2) {
            mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, owner) }.andExpect {
                status { isAccepted() }
                jsonPath("$.versionNo") { value(1) }
            }
        }
        assertThat(countRows("SELECT count(*) FROM persona.persona_profile_versions WHERE persona_id = ?", persona)).isEqualTo(1)
    }

    @Test
    fun `архивация - профиль неактивен, теги сняты, сырые сообщения удалены, запись импорта осталась`() {
        val owner = createUser()
        val persona = createPersona(owner)
        val importId = upload(owner, persona, "telegram/personal_chat.json").json()["id"].asString()

        mockMvc.delete("/api/v1/personas/$persona") { header(USER_HEADER, owner) }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/personas/$persona/profile") { header(USER_HEADER, owner) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PERSONA_NOT_READY") }
        }
        assertThat(persona(owner, persona)["tags"].size()).isZero()
        assertThat(countRows("SELECT count(*) FROM persona.persona_profile_versions WHERE persona_id = ? AND active", persona)).isZero()
        assertThat(countRows("SELECT count(*) FROM ingest.imported_messages WHERE import_id = ?::uuid", importId)).isZero()
        assertThat(import(owner, importId)["messageCount"].asInt()).isEqualTo(25)
        mockMvc.get("/api/v1/imports/$importId/messages") { header(USER_HEADER, owner) }.andExpect {
            jsonPath("$.items.length()") { value(0) }
            jsonPath("$.nextCursor") { doesNotExist() }
        }
        upload(owner, persona, "telegram/personal_chat.json").andExpect { status { isConflict() } }
    }

    @Test
    fun `чужие импорты и персоны - 404, специалисту загрузка запрещена - 403`() {
        val owner = createUser()
        val stranger = createUser()
        val persona = createPersona(owner)
        val importId = upload(owner, persona, "telegram/personal_chat.json").json()["id"].asString()

        mockMvc.get("/api/v1/imports/$importId") { header(USER_HEADER, stranger) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("IMPORT_NOT_FOUND") }
        }
        mockMvc.get("/api/v1/imports/$importId/messages") { header(USER_HEADER, stranger) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/imports/${UUID.randomUUID()}") { header(USER_HEADER, owner) }.andExpect { status { isNotFound() } }
        mockMvc.get("/api/v1/personas/$persona/imports") { header(USER_HEADER, stranger) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PERSONA_NOT_FOUND") }
        }
        upload(stranger, persona, "telegram/personal_chat.json").andExpect { status { isNotFound() } }
        upload(createUser(RoleCode.SPECIALIST), persona, "telegram/personal_chat.json").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `метрики импорта для администратора`() {
        val owner = createUser()
        val persona = createPersona(owner)
        upload(owner, persona, "telegram/personal_chat.json")
        upload(owner, persona, "telegram/broken.json")
        val values = metrics.flatMap { it.metrics().entries }.associate { it.key to it.value }
        assertThat(values).containsEntry("imports.total", 2L).containsEntry("imports.parsed", 1L).containsEntry("imports.failed", 1L)
        assertThat(values).containsEntry("imports.messages", 25L)
    }
}
