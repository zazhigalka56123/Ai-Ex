package ru.itmo.aiex.persona.dto

import ru.itmo.aiex.persona.service.TagItem
import ru.itmo.aiex.persona.entity.TraitSource
import java.math.BigDecimal

data class PersonaTagResponse(val code: String, val title: String, val weight: BigDecimal, val source: TraitSource)

fun TagItem.toResponse() = PersonaTagResponse(code, title, weight, source)
