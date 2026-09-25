package ru.itmo.aiex.persona.testing

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.util.UUID

abstract class PersonaIntegrationTest : AbstractIntegrationTest() {
    protected fun createPersona(owner: UUID, name: String = "Маша", tagCodes: Set<String> = emptySet(), kind: String = "EX_PARTNER"): UUID {
        val result =
            mockMvc
                .post("/api/v1/personas") {
                    header(USER_HEADER, owner)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(mapOf("name" to name, "relationshipKind" to kind, "tagCodes" to tagCodes))
                }.andExpect { status { isCreated() } }
                .andReturn()
        return UUID.fromString(result.json()["id"].asString())
    }

    protected fun countRows(sql: String, arg: Any): Int = jdbcTemplate.queryForObject(sql, Int::class.java, arg) ?: 0
}
