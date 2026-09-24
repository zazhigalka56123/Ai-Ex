package ru.itmo.aiex.care.domain

enum class SessionStatus {
    REQUESTED,
    CONFIRMED,
    DONE,
    CANCELLED,
    ;

    val isActive: Boolean get() = this in ACTIVE

    companion object {
        val ACTIVE: Set<SessionStatus> = setOf(REQUESTED, CONFIRMED)
    }
}
