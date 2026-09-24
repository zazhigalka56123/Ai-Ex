package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.domain.PersonaTag
import ru.itmo.aiex.persona.domain.TraitSource
import java.math.BigDecimal

data class TagItem(val code: String, val title: String, val weight: BigDecimal, val source: TraitSource)

internal fun PersonaTag.toItem() = TagItem(tag.code, tag.title, weight, source)
