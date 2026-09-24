package ru.itmo.aiex.admin.web.dto

import ru.itmo.aiex.common.dictionary.DictionaryEntry

data class DictionaryEntryResponse(val id: Long, val code: String, val title: String)

fun DictionaryEntry.toResponse() = DictionaryEntryResponse(id, code, title)
