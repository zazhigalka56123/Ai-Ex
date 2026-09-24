package ru.itmo.aiex.web.openapi

import ru.itmo.aiex.common.error.ErrorCode

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiErrors(vararg val value: ErrorCode)
