package ru.itmo.aiex.persona.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.SortDirection
import ru.itmo.aiex.common.paging.SortOrder
import ru.itmo.aiex.persona.api.TagCatalog
import ru.itmo.aiex.persona.testing.PersonaIntegrationTest
import java.util.UUID

class TagCatalogIT : PersonaIntegrationTest() {
    @Autowired
    private lateinit var catalog: TagCatalog

    private val prefix = "t-${UUID.randomUUID().toString().take(8)}"

    @AfterEach
    fun removeCreatedTags() {
        jdbcTemplate.update("DELETE FROM persona.tags WHERE code LIKE ?", "$prefix%")
    }

    @Test
    fun `сид справочника на месте, список отсортирован по коду`() {
        val page = catalog.list(PageQuery(0, 50))
        val codes = page.items.map { it.code }
        assertThat(codes).contains("cold", "jealous", "caps", "emoji", "night-owl", "laconic", "talkative", "slow-replier", "fast-replier")
        assertThat(codes).contains("affectionate", "sarcastic", "caring")
        assertThat(codes).isSorted()
        assertThat(page.totalElements).isGreaterThanOrEqualTo(12)

        val byTitle = catalog.list(PageQuery(0, 50, listOf(SortOrder("title", SortDirection.DESC), SortOrder("password", SortDirection.ASC))))
        assertThat(byTitle.items.map { it.title }).isSortedAccordingTo(reverseOrder())
    }

    @Test
    fun `создание нормализует код, дубликат - 409 DICTIONARY_CODE_TAKEN`() {
        val created = catalog.create("  ${prefix.uppercase()}-Toxic ", " токсичная ")
        assertThat(created.code).isEqualTo("$prefix-toxic")
        assertThat(created.title).isEqualTo("токсичная")
        assertThat(catalog.get(created.id)).isEqualTo(created)

        assertThatThrownBy { catalog.create("$prefix-toxic", "другая") }
            .isInstanceOf(ConflictException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.DICTIONARY_CODE_TAKEN)
    }

    @Test
    fun `невалидные код и название - 400`() {
        assertThatThrownBy { catalog.create("$prefix bad code!", "") }
            .isInstanceOf(ValidationException::class.java)
            .satisfies({ ex -> assertThat((ex as ValidationException).violations.map { it.field }).containsExactlyInAnyOrder("code", "title") })
        assertThatThrownBy { catalog.create("x".repeat(49), "длинный") }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `переименование и 404 на несуществующий тег`() {
        val created = catalog.create("$prefix-kind", "добрая")
        assertThat(catalog.update(created.id, "очень добрая").title).isEqualTo("очень добрая")
        assertThatThrownBy { catalog.update(created.id, " ") }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { catalog.get(Long.MAX_VALUE) }
            .isInstanceOf(NotFoundException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.TAG_NOT_FOUND)
        assertThatThrownBy { catalog.update(Long.MAX_VALUE, "x") }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { catalog.delete(Long.MAX_VALUE) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `используемый тег удалить нельзя - 409 CONSTRAINT_VIOLATED, неиспользуемый удаляется`() {
        val used = catalog.create("$prefix-used", "используемая")
        val unused = catalog.create("$prefix-unused", "свободная")
        createPersona(createUser(), tagCodes = setOf(used.code))

        assertThatThrownBy { catalog.delete(used.id) }
            .isInstanceOf(ConflictException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.CONSTRAINT_VIOLATED)
        catalog.delete(unused.id)
        assertThatThrownBy { catalog.get(unused.id) }.isInstanceOf(NotFoundException::class.java)

        jdbcTemplate.update("DELETE FROM persona.persona_tags WHERE tag_id = ?", used.id)
        catalog.delete(used.id)
    }
}
