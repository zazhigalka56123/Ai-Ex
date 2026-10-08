package ru.itmo.aiex.care.dto

import ru.itmo.aiex.care.entity.Specialization
data class SpecializationRefResponse(val code: String, val title: String)

internal fun Specialization.toRef() = SpecializationRefResponse(code, title)
