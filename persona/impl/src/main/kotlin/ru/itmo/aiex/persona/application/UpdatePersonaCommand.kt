package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.domain.RelationshipKind

data class UpdatePersonaCommand(val name: String? = null, val relationshipKind: RelationshipKind? = null, val description: String? = null)
