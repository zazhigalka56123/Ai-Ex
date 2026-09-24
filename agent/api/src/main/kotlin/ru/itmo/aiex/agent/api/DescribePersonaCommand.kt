package ru.itmo.aiex.agent.api

import java.util.UUID

data class DescribePersonaCommand(val personaId: UUID, val personaName: String, val traits: List<PersonaTraitLine>, val samplePhrases: List<String>)
