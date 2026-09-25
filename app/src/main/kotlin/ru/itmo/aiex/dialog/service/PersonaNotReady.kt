package ru.itmo.aiex.dialog.service

import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.FieldViolation
import ru.itmo.aiex.persona.dto.PersonaSummaryView
internal fun personaNotReady(persona: PersonaSummaryView) = AiExException(
    ErrorCode.PERSONA_NOT_READY,
    "Персона «${persona.name}» ещё не готова к диалогу",
    listOf(FieldViolation("personaId", "state.invalid", "ожидался статус READY с активным профилем, фактический ${persona.status}")),
)
