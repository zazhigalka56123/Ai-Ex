package ru.itmo.aiex.persona.domain

enum class RelationshipKind(val promptLabel: String) {
    EX_PARTNER("бывший партнёр"),
    EX_CRUSH("бывшая симпатия"),
    FRIEND("друг"),
    OTHER("знакомый человек"),
}
