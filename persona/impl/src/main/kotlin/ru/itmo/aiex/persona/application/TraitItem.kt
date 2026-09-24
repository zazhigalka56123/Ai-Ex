package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.domain.PersonaTrait
import ru.itmo.aiex.persona.domain.TraitSource
import java.math.BigDecimal

data class TraitItem(val key: String, val value: String, val weight: BigDecimal, val source: TraitSource)

internal fun PersonaTrait.toItem() = TraitItem(traitKey, traitValue, weight, source)
