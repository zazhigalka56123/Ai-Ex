package ru.itmo.aiex.common.error

data class FieldViolation(val field: String, val code: String, val message: String)
