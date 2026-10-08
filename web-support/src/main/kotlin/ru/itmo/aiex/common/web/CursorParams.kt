package ru.itmo.aiex.common.web

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CursorParams(val defaultLimit: Int = 0)
