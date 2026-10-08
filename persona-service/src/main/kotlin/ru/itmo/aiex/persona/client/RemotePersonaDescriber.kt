package ru.itmo.aiex.persona.client

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import ru.itmo.aiex.agent.dto.DescribePersonaCommand
import ru.itmo.aiex.agent.dto.PersonaDescription
import ru.itmo.aiex.agent.service.PersonaDescriber
import ru.itmo.aiex.remote.DialogClient

@Component
@Profile("microservice")
class RemotePersonaDescriber(private val dialogs: DialogClient) : PersonaDescriber {
    override fun describePersona(command: DescribePersonaCommand): PersonaDescription = dialogs.describePersona(command)
}
