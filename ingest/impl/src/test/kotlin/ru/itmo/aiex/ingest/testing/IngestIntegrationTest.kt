package ru.itmo.aiex.ingest.testing

import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.testing.AbstractIntegrationTest
import tools.jackson.databind.JsonNode
import java.util.UUID

abstract class IngestIntegrationTest : AbstractIntegrationTest() {
    protected fun createPersona(owner: UUID, name: String = "Маша"): UUID {
        val result =
            mockMvc
                .post("/api/v1/personas") {
                    header(USER_HEADER, owner)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("name" to name, "relationshipKind" to "EX_PARTNER"))
                }.andExpect { status { isCreated() } }
                .andReturn()
        return UUID.fromString(result.json()["id"].asString())
    }

    protected fun upload(
        owner: UUID,
        persona: UUID,
        fixture: String,
        filename: String = fixture.substringAfterLast('/'),
        params: Map<String, String> = emptyMap(),
    ): ResultActionsDsl = uploadBytes(owner, persona, Fixtures.bytes(fixture), filename, params)

    protected fun uploadBytes(
        owner: UUID,
        persona: UUID,
        content: ByteArray,
        filename: String,
        params: Map<String, String> = emptyMap(),
    ): ResultActionsDsl = mockMvc.multipart("/api/v1/personas/$persona/imports") {
        file(MockMultipartFile("file", filename, MediaType.APPLICATION_OCTET_STREAM_VALUE, content))
        params.forEach { (name, value) -> param(name, value) }
        header(USER_HEADER, owner)
    }

    protected fun persona(owner: UUID, persona: UUID): JsonNode = mockMvc.get("/api/v1/personas/$persona") { header(USER_HEADER, owner) }.json()

    protected fun import(owner: UUID, importId: String): JsonNode = mockMvc.get("/api/v1/imports/$importId") { header(USER_HEADER, owner) }.json()

    protected fun countRows(sql: String, arg: Any): Int = jdbcTemplate.queryForObject(sql, Int::class.java, arg) ?: 0
}
