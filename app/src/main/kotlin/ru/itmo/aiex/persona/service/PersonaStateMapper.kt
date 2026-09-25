package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.dto.PersonaState
import ru.itmo.aiex.persona.entity.PersonaStatus
internal fun PersonaStatus.toState(): PersonaState = PersonaState.valueOf(name)
