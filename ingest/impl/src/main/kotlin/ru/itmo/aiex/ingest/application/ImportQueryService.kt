package ru.itmo.aiex.ingest.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.CursorCodec
import ru.itmo.aiex.common.paging.CursorPage
import ru.itmo.aiex.common.paging.CursorQuery
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.ingest.domain.ChatImport
import ru.itmo.aiex.ingest.domain.ImportedMessage
import ru.itmo.aiex.ingest.domain.port.ChatImportRepository
import ru.itmo.aiex.ingest.domain.port.ImportedMessageRepository
import ru.itmo.aiex.persona.api.PersonaAccess
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ImportQueryService(
    private val imports: ChatImportRepository,
    private val messages: ImportedMessageRepository,
    private val personaAccess: PersonaAccess,
) {
    fun get(actor: Actor, importId: UUID): ChatImport =
        imports.findById(importId)?.takeIf { it.ownerId == actor.userId } ?: throw NotFoundException.of(ErrorCode.IMPORT_NOT_FOUND, importId)

    fun listByPersona(actor: Actor, personaId: UUID, page: PageQuery): PageView<ChatImport> {
        personaAccess.assertOwned(personaId, actor.userId)
        return imports.findPageByPersona(personaId, page)
    }

    fun messages(actor: Actor, importId: UUID, cursor: CursorQuery): CursorPage<ImportedMessage> {
        get(actor, importId)
        val after = cursor.cursor?.let(::decodeOrdinal) ?: -1
        val rows = messages.findAfter(importId, after, cursor.limit + 1)
        return CursorPage.fromOverfetch(rows, cursor.limit) { CursorCodec.encodeOrdinal(it.ordinal.toLong()) }
    }

    private fun decodeOrdinal(cursor: String): Int {
        val ordinal = CursorCodec.decodeOrdinal(cursor)
        if (ordinal !in
            -1L..Int.MAX_VALUE.toLong()
        ) {
            throw ValidationException("cursor", "cursor.invalid", "Курсор повреждён или получен не от этого API")
        }
        return ordinal.toInt()
    }
}
