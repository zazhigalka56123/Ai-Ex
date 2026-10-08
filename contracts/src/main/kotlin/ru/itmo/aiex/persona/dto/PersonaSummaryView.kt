package ru.itmo.aiex.persona.dto

import java.util.UUID

data class PersonaSummaryView(val id: UUID, val ownerId: UUID, val name: String, val status: PersonaState, val activeProfileId: UUID?) {
    val canChat: Boolean get() = status == PersonaState.READY && activeProfileId != null
}
