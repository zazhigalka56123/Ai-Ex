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
    fun createSpecialist(actor: Actor, command: CreateSpecialistCommand): SpecialistCard = toCard(specialists.createSpecialist(actor, command))

    fun getSpecialists(specializationCode: String?, page: PageQuery): PageView<SpecialistCard> =
        specialists.getSpecialists(specializationCode, page).map(::toCard)

    fun getSpecialist(actor: Actor?, id: UUID): SpecialistCard = toCard(specialists.getSpecialist(actor, id))

    fun updateSpecialist(actor: Actor, id: UUID, command: UpdateSpecialistCommand): SpecialistCard =
        toCard(specialists.updateSpecialist(actor, id, command))

    private fun toCard(specialist: Specialist) = SpecialistCard(specialist, users.findActive(specialist.userId)?.displayName)
}
