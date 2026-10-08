package ru.itmo.aiex.care.client

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.itmo.aiex.common.dictionary.DictionaryEntry
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.persona.service.PersonaArchiver
import ru.itmo.aiex.persona.service.TagCatalog
import ru.itmo.aiex.remote.DictionaryCreate
import ru.itmo.aiex.remote.DictionaryUpdate
import ru.itmo.aiex.remote.PersonaClient
import java.util.UUID

@Component
@Profile("microservice")
class RemotePersonaAdministration(private val personas: PersonaClient) :
    PersonaArchiver,
    TagCatalog {
    override fun archivePersona(personaId: UUID, actor: Actor) = personas.archivePersona(personaId, actor)

    override fun getEntries(page: PageQuery): PageView<DictionaryEntry> = personas.getEntries(page)

    override fun getEntry(id: Long): DictionaryEntry = personas.getEntry(id)

    override fun createEntry(code: String, title: String): DictionaryEntry = personas.createEntry(DictionaryCreate(code, title))

    override fun updateEntry(id: Long, title: String): DictionaryEntry = personas.updateEntry(id, DictionaryUpdate(title))

    override fun deleteEntry(id: Long) = personas.deleteEntry(id)
}
