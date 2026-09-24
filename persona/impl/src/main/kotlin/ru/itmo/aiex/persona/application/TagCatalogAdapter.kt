package ru.itmo.aiex.persona.application

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.dictionary.DictionaryEntry
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.FieldViolation
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.paging.SortDirection
import ru.itmo.aiex.common.paging.SortOrder
import ru.itmo.aiex.persona.api.TagCatalog
import ru.itmo.aiex.persona.domain.Tag
import ru.itmo.aiex.persona.domain.port.PersonaTagRepository
import ru.itmo.aiex.persona.domain.port.TagRepository

@Component
@Transactional(readOnly = true)
class TagCatalogAdapter(private val tags: TagRepository, private val personaTags: PersonaTagRepository) : TagCatalog {
    override fun list(page: PageQuery): PageView<DictionaryEntry> {
        val sort = page.sort.filter { it.property in SORTABLE }.ifEmpty { listOf(SortOrder("code", SortDirection.ASC)) }
        return tags.findPage(page.copy(sort = sort)).map { it.toEntry() }
    }

    override fun get(id: Long): DictionaryEntry = find(id).toEntry()

    @Transactional
    override fun create(code: String, title: String): DictionaryEntry {
        val normalized = code.trim().lowercase()
        validate(normalized, title)
        if (tags.existsByCode(normalized)) throw codeTaken(normalized)
        return try {
            tags.saveAndFlush(Tag(normalized, title.trim())).toEntry()
        } catch (ex: DataIntegrityViolationException) {
            throw codeTaken(normalized).apply { initCause(ex) }
        }
    }

    @Transactional
    override fun update(id: Long, title: String): DictionaryEntry {
        validate(code = null, title = title)
        val tag = find(id)
        tag.rename(title.trim())
        return tags.saveAndFlush(tag).toEntry()
    }

    @Transactional
    override fun delete(id: Long) {
        val tag = find(id)
        if (personaTags.existsByTag(id)) throw inUse(tag)
        try {
            tags.deleteAndFlush(tag)
        } catch (ex: DataIntegrityViolationException) {
            throw inUse(tag).apply { initCause(ex) }
        }
    }

    private fun find(id: Long): Tag = tags.findById(id) ?: throw NotFoundException.of(ErrorCode.TAG_NOT_FOUND, id)

    private fun validate(code: String?, title: String) {
        val violations = mutableListOf<FieldViolation>()
        if (code != null && !isValidCode(code)) {
            violations += FieldViolation("code", "pattern", "Код тега - 1..${Tag.CODE_MAX} символов: латиница в нижнем регистре, цифры и дефис")
        }
        if (title.isBlank() || title.trim().length > Tag.TITLE_MAX) {
            violations += FieldViolation("title", "size", "Название тега - 1..${Tag.TITLE_MAX} символов")
        }
        if (violations.isNotEmpty()) throw ValidationException(violations)
    }

    private fun isValidCode(code: String): Boolean = code.length in 1..Tag.CODE_MAX && CODE_REGEX.matches(code)

    private fun codeTaken(code: String) = ConflictException(ErrorCode.DICTIONARY_CODE_TAKEN, "Тег с кодом '$code' уже существует")

    private fun inUse(tag: Tag) = ConflictException(ErrorCode.CONSTRAINT_VIOLATED, "Тег '${tag.code}' используется персонами, удалить его нельзя")

    private fun Tag.toEntry() = DictionaryEntry(requireNotNull(id), code, title)

    private companion object {
        val SORTABLE = setOf("code", "title", "id")
        val CODE_REGEX = Regex(Tag.CODE_PATTERN)
    }
}
