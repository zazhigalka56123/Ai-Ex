package ru.itmo.aiex.common.error

class ValidationException(violations: List<FieldViolation>, message: String = violations.joinToString("; ") { "${it.field}: ${it.message}" }) :
    AiExException(ErrorCode.VALIDATION_FAILED, message, violations) {
    constructor(field: String, code: String, message: String) : this(listOf(FieldViolation(field, code, message)))
}
