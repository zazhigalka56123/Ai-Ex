package ru.itmo.aiex.care.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.care.domain.CareFixtures.NOW
import ru.itmo.aiex.care.domain.CareFixtures.actor
import ru.itmo.aiex.common.error.ForbiddenException
import ru.itmo.aiex.common.security.RoleCode
import java.math.BigDecimal
import java.util.UUID

class CareEntitiesTest {
    @Test
    fun `роль актора в консультации - участие важнее роли администратора`() {
        val specialistUser = UUID.randomUUID()
        val client = UUID.randomUUID()
        val session = CareFixtures.session(CareFixtures.specialist(specialistUser), clientId = client)

        assertThat(session.roleOf(actor(specialistUser, RoleCode.SPECIALIST))).isEqualTo(ConsultationRole.SPECIALIST)
        assertThat(session.roleOf(actor(client))).isEqualTo(ConsultationRole.CLIENT)
        assertThat(session.roleOf(actor(client, RoleCode.USER, RoleCode.ADMIN))).isEqualTo(ConsultationRole.CLIENT)
        assertThat(session.roleOf(actor(roles = arrayOf(RoleCode.ADMIN)))).isEqualTo(ConsultationRole.ADMIN)
        assertThat(session.roleOf(actor())).isNull()
    }

    @Test
    fun `консультация копирует время слота и применяет изменения по правилам`() {
        val session = CareFixtures.session()
        assertThat(session.startsAt).isEqualTo(session.slot.startsAt)
        assertThat(session.durationMin).isEqualTo(session.slot.durationMin)
        assertThat(session.status).isEqualTo(SessionStatus.REQUESTED)

        val later = NOW.plusSeconds(60)
        assertThat(
            session.applyChange(ConsultationRole.SPECIALIST, ConsultationChange(status = SessionStatus.CONFIRMED, summary = "план"), later),
        ).isTrue()
        assertThat(session.status).isEqualTo(SessionStatus.CONFIRMED)
        assertThat(session.summary).isEqualTo("план")
        assertThat(session.updatedAt).isEqualTo(later)

        assertThat(session.applyChange(ConsultationRole.SPECIALIST, ConsultationChange(recommendations = "дневник"), later)).isFalse()
        assertThat(session.recommendations).isEqualTo("дневник")

        assertThat(
            session.applyChange(ConsultationRole.CLIENT, ConsultationChange(status = SessionStatus.CANCELLED, cancelReason = "заболела"), later),
        )
            .isTrue()
        assertThat(session.cancelReason).isEqualTo("заболела")
        assertThat(session.rating).isNull()
    }

    @Test
    fun `отклонённое изменение не трогает консультацию`() {
        val session = CareFixtures.session()
        assertThatThrownBy { session.applyChange(ConsultationRole.CLIENT, ConsultationChange(status = SessionStatus.CONFIRMED), NOW) }
            .isInstanceOf(ForbiddenException::class.java)
        assertThat(session.status).isEqualTo(SessionStatus.REQUESTED)
    }

    @Test
    fun `видимость и управление профилем специалиста`() {
        val owner = UUID.randomUUID()
        val specialist = CareFixtures.specialist(owner)
        val stranger = actor()
        val admin = actor(roles = arrayOf(RoleCode.ADMIN))

        assertThat(specialist.isVisibleTo(null)).isTrue()
        assertThat(specialist.canBeManagedBy(stranger)).isFalse()
        assertThat(specialist.canBeManagedBy(admin)).isTrue()

        specialist.changeStatus(SpecialistStatus.INACTIVE, NOW)
        assertThat(specialist.isVisibleTo(null)).isFalse()
        assertThat(specialist.isVisibleTo(stranger)).isFalse()
        assertThat(specialist.isVisibleTo(actor(owner, RoleCode.SPECIALIST))).isTrue()
        assertThat(specialist.isVisibleTo(admin)).isTrue()
    }

    @Test
    fun `профиль обновляется только переданными полями, без специализаций нельзя`() {
        val specialist = CareFixtures.specialist()
        val later = NOW.plusSeconds(1)
        specialist.describe(headline = null, bio = "Новая био", pricePerHour = BigDecimal("100.00"), now = later)
        assertThat(specialist.headline).isEqualTo("Психолог")
        assertThat(specialist.bio).isEqualTo("Новая био")
        assertThat(specialist.pricePerHour).isEqualByComparingTo("100")
        assertThat(specialist.updatedAt).isEqualTo(later)

        specialist.changeStatus(SpecialistStatus.ACTIVE, NOW.plusSeconds(99))
        assertThat(specialist.updatedAt).describedAs("тот же статус - без изменений").isEqualTo(later)

        assertThatThrownBy { specialist.replaceSpecializations(emptyList(), NOW) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `равенство сущностей - по идентификатору, справочника - по коду`() {
        val slot = CareFixtures.slot()
        assertThat(slot).isEqualTo(slot).isNotEqualTo(CareFixtures.slot())
        assertThat(slot.endsAt).isEqualTo(slot.startsAt.plusSeconds(3600))
        assertThat(slot.overlaps(slot.startsAt.plusSeconds(1800), 60)).isTrue()
        assertThat(CareFixtures.specialization("grief")).isEqualTo(CareFixtures.specialization("grief"))
        assertThat(CareFixtures.specialization("grief").hashCode()).isEqualTo("grief".hashCode())
        val specialist = CareFixtures.specialist()
        assertThat(specialist).isEqualTo(specialist).isNotEqualTo(CareFixtures.specialist())
        val session = CareFixtures.session()
        assertThat(session).isEqualTo(session).isNotEqualTo(CareFixtures.session())
        assertThat(SessionStatus.ACTIVE).containsExactlyInAnyOrder(SessionStatus.REQUESTED, SessionStatus.CONFIRMED)
        assertThat(SessionStatus.DONE.isActive).isFalse()
    }
}
