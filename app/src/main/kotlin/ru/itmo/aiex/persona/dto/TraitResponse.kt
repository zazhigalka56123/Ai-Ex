package ru.itmo.aiex.persona.dto

import ru.itmo.aiex.persona.service.TraitItem
import ru.itmo.aiex.persona.entity.TraitSource
import java.math.BigDecimal

data class TraitResponse(val key: String, val value: String, val weight: BigDecimal, val source: TraitSource)

fun TraitItem.toResponse() = TraitResponse(key, value, weight, source)
