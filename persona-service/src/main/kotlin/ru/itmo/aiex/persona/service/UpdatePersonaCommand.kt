package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.entity.RelationshipKind
data class UpdatePersonaCommand(val name: String? = null, val relationshipKind: RelationshipKind? = null, val description: String? = null)
