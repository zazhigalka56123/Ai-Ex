package ru.itmo.aiex.agent.service

import ru.itmo.aiex.agent.dto.DescribePersonaCommand
import ru.itmo.aiex.agent.dto.PersonaDescription

interface PersonaDescriber {
    fun describe(command: DescribePersonaCommand): PersonaDescription
}
