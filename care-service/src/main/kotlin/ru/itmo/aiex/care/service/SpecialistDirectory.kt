package ru.itmo.aiex.care.service

import org.springframework.stereotype.Component
import ru.itmo.aiex.care.entity.Specialist
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.iam.service.UserQuery
import java.util.UUID

@Component
class SpecialistDirectory(private val specialists: SpecialistService, private val users: UserQuery) {
    fun create(actor: Actor, command: CreateSpecialistCommand): SpecialistCard = card(specialists.create(actor, command))

    fun catalog(specializationCode: String?, page: PageQuery): PageView<SpecialistCard> = specialists.catalog(specializationCode, page).map(::card)

    fun get(actor: Actor?, id: UUID): SpecialistCard = card(specialists.get(actor, id))

    fun update(actor: Actor, id: UUID, command: UpdateSpecialistCommand): SpecialistCard = card(specialists.update(actor, id, command))

    private fun card(specialist: Specialist) = SpecialistCard(specialist, users.findActive(specialist.userId)?.displayName)
}
