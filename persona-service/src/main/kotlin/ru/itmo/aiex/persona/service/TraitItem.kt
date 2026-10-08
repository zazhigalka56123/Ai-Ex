package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.entity.PersonaTrait
import ru.itmo.aiex.persona.entity.TraitSource
import java.math.BigDecimal

data class TraitItem(val key: String, val value: String, val weight: BigDecimal, val source: TraitSource)

internal fun PersonaTrait.toItem() = TraitItem(traitKey, traitValue, weight, source)
