package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.domain.RelationshipKind

data class CreatePersonaCommand(val name: String, val relationshipKind: RelationshipKind, val description: String?, val tagCodes: Set<String>)
