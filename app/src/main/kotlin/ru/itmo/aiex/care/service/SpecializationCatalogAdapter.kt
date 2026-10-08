package ru.itmo.aiex.care.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.care.entity.Specialization
import ru.itmo.aiex.care.repository.SpecializationRepository
import ru.itmo.aiex.common.dictionary.DictionaryEntry
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
@Component
@Transactional(readOnly = true)
class SpecializationCatalogAdapter(private val specializations: SpecializationRepository) : SpecializationCatalog {
    override fun getEntries(page: PageQuery): PageView<DictionaryEntry> = specializations.findPage(page).map { it.toEntry() }

    override fun getEntry(id: Long): DictionaryEntry = findSpecialization(id).toEntry()

    @Transactional
    override fun createEntry(code: String, title: String): DictionaryEntry {
        val normalized = code.trim().lowercase()
        if (normalized.length > Specialization.CODE_MAX_LENGTH || !CODE_REGEX.matches(normalized)) {
            throw ValidationException("code", "pattern", "Код - от 1 до ${Specialization.CODE_MAX_LENGTH} символов: латиница, цифры, дефис")
        }
        val cleanTitle = requireTitle(title)
        if (specializations.existsByCode(normalized)) throw codeTaken(normalized)
        return try {
            specializations.saveAndFlush(Specialization(normalized, cleanTitle)).toEntry()
        } catch (ex: DataIntegrityViolationException) {
            throw codeTaken(normalized).apply { addSuppressed(ex) }
        }
    }

    @Transactional
    override fun updateEntry(id: Long, title: String): DictionaryEntry {
        val specialization = findSpecialization(id)
        specialization.rename(requireTitle(title))
        return specializations.saveAndFlush(specialization).toEntry()
    }

    @Transactional
    override fun deleteEntry(id: Long) {
        val specialization = findSpecialization(id)
        if (specializations.isInUse(id)) throw inUse(specialization.code)
        try {
            specializations.deleteAndFlush(specialization)
        } catch (ex: DataIntegrityViolationException) {
            throw inUse(specialization.code).apply { addSuppressed(ex) }
        }
    }

    private fun findSpecialization(id: Long): Specialization =
        specializations.findById(id) ?: throw NotFoundException.of(ErrorCode.SPECIALIZATION_NOT_FOUND, id)

    private fun requireTitle(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || trimmed.length > Specialization.TITLE_MAX_LENGTH) {
            throw ValidationException("title", "size", "Название - от 1 до ${Specialization.TITLE_MAX_LENGTH} символов")
        }
        return trimmed
    }

    private fun codeTaken(code: String) = ConflictException(ErrorCode.DICTIONARY_CODE_TAKEN, "Специализация с кодом $code уже есть")

    private fun inUse(code: String) = ConflictException(ErrorCode.CONSTRAINT_VIOLATED, "Специализация $code указана в профилях специалистов")

    private fun Specialization.toEntry() = DictionaryEntry(id = checkNotNull(id) { "Специализация не сохранена" }, code = code, title = title)

    private companion object {
        val CODE_REGEX = Regex(Specialization.CODE_PATTERN)
    }
}
