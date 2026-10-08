package ru.itmo.aiex.remote

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import ru.itmo.aiex.common.dictionary.DictionaryEntry
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.persona.dto.PersonaProfileView
import ru.itmo.aiex.persona.dto.PersonaSummaryView
import java.util.UUID

@FeignClient(name = "persona-service", configuration = [InternalFeignConfiguration::class])
interface PersonaClient {
    @GetMapping("/internal/personas/{id}")
    fun findSummary(@PathVariable("id") id: UUID): PersonaSummaryView?

    @GetMapping("/internal/personas/{id}/owned")
    fun assertOwned(@PathVariable("id") id: UUID, @RequestParam("userId") userId: UUID): PersonaSummaryView

    @GetMapping("/internal/personas/{id}/active-profile")
    fun findActiveProfile(@PathVariable("id") id: UUID): PersonaProfileView?

    @PostMapping("/internal/personas/{id}/archive")
    fun archivePersona(@PathVariable("id") id: UUID, @RequestBody actor: Actor)

    @PostMapping("/internal/tags/search")
    fun getEntries(@RequestBody page: PageQuery): PageView<DictionaryEntry>

    @GetMapping("/internal/tags/{id}")
    fun getEntry(@PathVariable("id") id: Long): DictionaryEntry

    @PostMapping("/internal/tags")
    fun createEntry(@RequestBody request: DictionaryCreate): DictionaryEntry

    @PutMapping("/internal/tags/{id}")
    fun updateEntry(@PathVariable("id") id: Long, @RequestBody request: DictionaryUpdate): DictionaryEntry

    @DeleteMapping("/internal/tags/{id}")
    fun deleteEntry(@PathVariable("id") id: Long)

    @GetMapping("/internal/metrics")
    fun getMetrics(): Map<String, Long>
}
