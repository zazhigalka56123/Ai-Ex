package ru.itmo.aiex.persona.entity

enum class PersonaStatus {
    DRAFT,
    TRAINING,
    READY,
    ARCHIVED,
    ;

    val allowedTargets: Set<PersonaStatus>
        get() = when (this) {
            DRAFT -> setOf(TRAINING, ARCHIVED)
            TRAINING -> setOf(READY, DRAFT, ARCHIVED)
            READY -> setOf(TRAINING, ARCHIVED)
            ARCHIVED -> emptySet()
        }

    fun canTransitionTo(target: PersonaStatus): Boolean = target in allowedTargets
}
