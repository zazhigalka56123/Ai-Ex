package ru.itmo.aiex.care.web.dto

import ru.itmo.aiex.care.domain.Specialization

data class SpecializationRefResponse(val code: String, val title: String)

internal fun Specialization.toRef() = SpecializationRefResponse(code, title)
