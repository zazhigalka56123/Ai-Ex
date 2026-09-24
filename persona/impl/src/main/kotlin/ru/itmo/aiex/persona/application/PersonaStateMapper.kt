package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.api.PersonaState
import ru.itmo.aiex.persona.domain.PersonaStatus

internal fun PersonaStatus.toState(): PersonaState = PersonaState.valueOf(name)
