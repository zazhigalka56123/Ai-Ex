package ru.itmo.aiex.agent.api

interface PersonaDescriber {
    fun describe(command: DescribePersonaCommand): PersonaDescription
}
