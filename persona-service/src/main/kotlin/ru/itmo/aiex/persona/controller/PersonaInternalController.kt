package ru.itmo.aiex.persona.controller

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.persona.service.PersonaAccess
import ru.itmo.aiex.persona.service.PersonaLifecycle
import ru.itmo.aiex.persona.service.PersonaProfileQuery
import ru.itmo.aiex.persona.service.TagCatalog
import ru.itmo.aiex.remote.DictionaryCreate
import ru.itmo.aiex.remote.DictionaryUpdate
import java.util.UUID

@RestController
@Profile("microservice")
class PersonaInternalController(
    private val personas: PersonaAccess,
    private val profiles: PersonaProfileQuery,
    private val lifecycle: PersonaLifecycle,
    private val tags: TagCatalog,
) {
    @GetMapping("/internal/personas/{id}")
    fun findPersona(@PathVariable id: UUID) = personas.findSummary(id)

    @GetMapping("/internal/personas/{id}/owned")
    fun findOwnedPersona(@PathVariable id: UUID, @RequestParam userId: UUID) = personas.assertOwned(id, userId)

    @GetMapping("/internal/personas/{id}/active-profile")
    fun findActiveProfile(@PathVariable id: UUID) = profiles.findActiveProfile(id)

    @PostMapping("/internal/personas/{id}/archive")
    fun archivePersona(@PathVariable id: UUID, @RequestBody actor: Actor) = lifecycle.archive(id, actor)

    @PostMapping("/internal/tags/search")
    fun getTags(@RequestBody page: PageQuery) = tags.getEntries(page)

    @GetMapping("/internal/tags/{id}")
    fun getTag(@PathVariable id: Long) = tags.get(id)

    @PostMapping("/internal/tags")
    fun createTag(@RequestBody request: DictionaryCreate) = tags.create(request.code, request.title)

    @PutMapping("/internal/tags/{id}")
    fun updateTag(@PathVariable id: Long, @RequestBody request: DictionaryUpdate) = tags.update(id, request.title)

    @DeleteMapping("/internal/tags/{id}")
    fun deleteTag(@PathVariable id: Long) = tags.delete(id)
}
