package ru.itmo.aiex.care.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import ru.itmo.aiex.care.CareIntegrationTest
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.PageQuery
class SpecializationCatalogIT : CareIntegrationTest() {
    @Autowired
    private lateinit var catalog: SpecializationCatalog

    @AfterEach
    fun removeTestSpecializations() {
        jdbcTemplate.update(
            "DELETE FROM care.specialist_specializations WHERE specialization_id IN (SELECT id FROM care.specializations WHERE code LIKE 'it-%')",
        )
        jdbcTemplate.update("DELETE FROM care.specializations WHERE code LIKE 'it-%'")
    }

    @Test
    fun `справочник отдаётся страницами по возрастанию кода, сиды на месте`() {
        val page = catalog.getEntries(PageQuery(0, 50))
        val codes = page.items.map { it.code }
        assertThat(codes).contains("anxiety", "breakup", "grief", "relationships", "self-esteem", "sleep")
        assertThat(codes).isSorted()
        assertThat(page.items.first { it.code == "grief" }.title).isEqualTo("Горе и утрата")
        assertThat(catalog.getEntries(PageQuery(1, 2)).items).hasSize(2)
    }

    @Test
    fun `создание нормализует код, дубликат - DICTIONARY_CODE_TAKEN, мусор - 400`() {
        val created = catalog.createEntry("  IT-Couples ", "  Пары  ")
        assertThat(created.code).isEqualTo("it-couples")
        assertThat(created.title).isEqualTo("Пары")
        assertThat(catalog.getEntry(created.id)).isEqualTo(created)

        assertThatThrownBy { catalog.createEntry("it-couples", "Ещё раз") }
            .isInstanceOf(ConflictException::class.java)
            .extracting { (it as AiExException).code }
            .isEqualTo(ErrorCode.DICTIONARY_CODE_TAKEN)
        assertThatThrownBy { catalog.createEntry("it_bad code", "Мусор") }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { catalog.createEntry("it-" + "x".repeat(60), "Длинный") }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { catalog.createEntry("it-empty-title", "   ") }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `переименование и удаление неиспользуемой специализации`() {
        val created = catalog.createEntry("it-rename", "Было")
        assertThat(catalog.updateEntry(created.id, "Стало").title).isEqualTo("Стало")
        catalog.deleteEntry(created.id)
        assertThatThrownBy { catalog.getEntry(created.id) }
            .isInstanceOf(NotFoundException::class.java)
            .extracting { (it as AiExException).code }
            .isEqualTo(ErrorCode.SPECIALIZATION_NOT_FOUND)
        assertThatThrownBy { catalog.updateEntry(created.id, "Нет") }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `используемую специализацию удалить нельзя - CONSTRAINT_VIOLATED`() {
        val created = catalog.createEntry("it-in-use", "Используется")
        createSpecialist(codes = listOf("it-in-use"))
        assertThatThrownBy { catalog.deleteEntry(created.id) }
            .isInstanceOf(ConflictException::class.java)
            .extracting { (it as AiExException).code }
            .isEqualTo(ErrorCode.CONSTRAINT_VIOLATED)
        assertThat(catalog.getEntry(created.id).code).isEqualTo("it-in-use")
    }
}
