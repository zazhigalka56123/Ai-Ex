package ru.itmo.aiex.persona.web.dto

import ru.itmo.aiex.persona.application.TraitItem
import ru.itmo.aiex.persona.domain.TraitSource
import java.math.BigDecimal

data class TraitResponse(val key: String, val value: String, val weight: BigDecimal, val source: TraitSource)

fun TraitItem.toResponse() = TraitResponse(key, value, weight, source)
