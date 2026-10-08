package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.entity.RelationshipKind
data class CreatePersonaCommand(val name: String, val relationshipKind: RelationshipKind, val description: String?, val tagCodes: Set<String>)
