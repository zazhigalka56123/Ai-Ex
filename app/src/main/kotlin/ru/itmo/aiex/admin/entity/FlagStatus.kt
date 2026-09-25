package ru.itmo.aiex.admin.entity

enum class FlagStatus {
    OPEN,
    IN_REVIEW,
    RESOLVED,
    REJECTED,
    ;

    val isTerminal: Boolean get() = this == RESOLVED || this == REJECTED

    fun canTransitionTo(target: FlagStatus): Boolean = when (this) {
        OPEN -> target != OPEN
        IN_REVIEW -> target.isTerminal
        RESOLVED, REJECTED -> false
    }
}
