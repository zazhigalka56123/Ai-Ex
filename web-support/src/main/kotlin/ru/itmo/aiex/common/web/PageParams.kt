package ru.itmo.aiex.common.web

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class PageParams(val sortable: Array<String> = [], val defaultSort: String = "")
