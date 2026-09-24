package ru.itmo.aiex.persona.web.dto

import ru.itmo.aiex.persona.application.TagItem
import ru.itmo.aiex.persona.domain.TraitSource
import java.math.BigDecimal

data class PersonaTagResponse(val code: String, val title: String, val weight: BigDecimal, val source: TraitSource)

fun TagItem.toResponse() = PersonaTagResponse(code, title, weight, source)
