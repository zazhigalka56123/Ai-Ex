package ru.itmo.aiex.persona.service

import org.springframework.stereotype.Component
import ru.itmo.aiex.persona.dto.CorpusSnapshot
import ru.itmo.aiex.persona.dto.CorpusStats
import ru.itmo.aiex.persona.dto.StyleView
import tools.jackson.databind.json.JsonMapper

@Component
class PersonaJson(private val mapper: JsonMapper) {
    fun write(value: Any): String = mapper.writeValueAsString(value)

    fun readSnapshot(json: String): CorpusSnapshot = mapper.readValue(json, CorpusSnapshot::class.java)

    fun readStyle(json: String): StyleView = mapper.readValue(json, StyleView::class.java)

    fun readStats(json: String): CorpusStats = mapper.readValue(json, CorpusStats::class.java)
}
