package ru.itmo.aiex.common.web

import ru.itmo.aiex.common.error.FieldViolation
internal fun parseInt(raw: String?, field: String, default: Int, violations: MutableList<FieldViolation>): Int? {
    if (raw.isNullOrBlank()) return default
    return raw.trim().toIntOrNull().also {
        if (it == null) violations += FieldViolation(field, "$field.type", "$field должен быть целым числом")
    }
}
