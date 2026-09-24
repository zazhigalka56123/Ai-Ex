package ru.itmo.aiex.care.application

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import ru.itmo.aiex.care.domain.CareFixtures
import ru.itmo.aiex.care.domain.CareFixtures.NOW
import ru.itmo.aiex.care.domain.ConsultationChange
import ru.itmo.aiex.care.domain.ConsultationSession
import ru.itmo.aiex.care.domain.SessionStatus
import ru.itmo.aiex.care.domain.Specialist
import ru.itmo.aiex.care.domain.port.ConsultationRepository
import ru.itmo.aiex.care.domain.port.SlotRepository
import ru.itmo.aiex.care.domain.port.SpecialistRepository
import ru.itmo.aiex.care.domain.port.SpecializationRepository
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.SlotAlreadyBookedException
import ru.itmo.aiex.common.events.ConsultationRequested
import ru.itmo.aiex.common.events.ConsultationStatusChanged
import ru.itmo.aiex.common.events.DomainEvent
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.security.RoleCode
import java.math.BigDecimal
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

class CareServicesTest {
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val sessions = mockk<ConsultationRepository>()
    private val slots = mockk<SlotRepository>()
    private val specialists = mockk<SpecialistRepository>(relaxUnitFun = true)
    private val published = mutableListOf<DomainEvent>()
    private val events = DomainEventPublisher { published += it }
    private val consultations = ConsultationService(sessions, slots, specialists, events, clock)

    @Test
    fun `TX-3 - нарушение частичного уникального индекса превращается в SLOT_TAKEN, счётчик и событие не трогаются`() {
        val slot = CareFixtures.slot()
        every { slots.findByIdForUpdate(slot.id) } returns slot
        every { sessions.existsActiveOnSlot(slot.id) } returns false
        every { sessions.saveAndFlush(any()) } throws DataIntegrityViolationException("ux_consultation_sessions_active_slot")

        assertThatThrownBy { consultations.book(CareFixtures.actor(), BookConsultationCommand(slot.id)) }
            .isInstanceOf(SlotAlreadyBookedException::class.java)
            .satisfies({ assertThat(it.suppressed.single()).isInstanceOf(DataIntegrityViolationException::class.java) })
        verify(exactly = 0) { specialists.incrementBookedCount(any()) }
        assertThat(published).isEmpty()
    }

    @Test
    fun `TX-3 - успешная запись увеличивает счётчик и публикует ConsultationRequested`() {
        val specialist = CareFixtures.specialist()
        val slot = CareFixtures.slot(specialist)
        val client = CareFixtures.actor()
        val saved = slot<ConsultationSession>()
        every { slots.findByIdForUpdate(slot.id) } returns slot
        every { sessions.existsActiveOnSlot(slot.id) } returns false
        every { sessions.saveAndFlush(capture(saved)) } answers { saved.captured }

        val session = consultations.book(client, BookConsultationCommand(slot.id, sharedConversationId = UUID.randomUUID()))

        assertThat(session.status).isEqualTo(SessionStatus.REQUESTED)
        assertThat(session.userId).isEqualTo(client.userId)
        assertThat(session.createdAt).isEqualTo(NOW)
        verify { specialists.incrementBookedCount(specialist.id) }
        val event = published.single() as ConsultationRequested
        assertThat(event.specialistUserId).isEqualTo(specialist.userId)
        assertThat(event.startsAt).isEqualTo(slot.startsAt)
    }

    @Test
    fun `отмена уменьшает счётчик, а смена полей без статуса событий не публикует`() {
        val specialist = CareFixtures.specialist()
        val client = UUID.randomUUID()
        val session = CareFixtures.session(specialist, clientId = client)
        every { sessions.findById(session.id) } returns session
        every { sessions.saveAndFlush(session) } returns session

        consultations.update(
            CareFixtures.actor(specialist.userId, RoleCode.SPECIALIST),
            session.id,
            ConsultationChange(status = SessionStatus.CONFIRMED),
        )
        consultations.update(CareFixtures.actor(specialist.userId, RoleCode.SPECIALIST), session.id, ConsultationChange(summary = "резюме"))
        consultations.update(CareFixtures.actor(client), session.id, ConsultationChange(status = SessionStatus.CANCELLED))

        assertThat(published.map { (it as ConsultationStatusChanged).status }).containsExactly("CONFIRMED", "CANCELLED")
        verify(exactly = 1) { specialists.decrementBookedCount(specialist.id) }
    }

    @Test
    fun `гонка двух профилей одного пользователя - SPECIALIST_PROFILE_EXISTS`() {
        val specializations = mockk<SpecializationRepository>()
        val service = SpecialistService(specialists, specializations, clock)
        val actor = CareFixtures.actor(roles = arrayOf(RoleCode.SPECIALIST))
        every { specialists.existsByUserId(actor.userId) } returns false
        every { specializations.findByCodes(setOf("breakup")) } returns listOf(CareFixtures.specialization())
        every { specialists.saveAndFlush(any<Specialist>()) } throws DataIntegrityViolationException("uq_specialists_user")

        assertThatThrownBy { service.create(actor, CreateSpecialistCommand("Психолог", "Био", BigDecimal.TEN, setOf(" Breakup "))) }
            .isInstanceOf(ConflictException::class.java)
            .extracting { (it as AiExException).code }
            .isEqualTo(ErrorCode.SPECIALIST_PROFILE_EXISTS)
    }

    @Test
    fun `гонка публикации слота с тем же началом - SLOT_OVERLAP`() {
        val specialist = CareFixtures.specialist()
        val service = SlotService(specialists, slots, clock)
        every { specialists.findByIdForUpdate(specialist.id) } returns specialist
        every { slots.findStartingBetween(specialist.id, any(), any()) } returns emptyList()
        every { slots.add(any()) } throws DataIntegrityViolationException("uq_specialist_slots_start")

        assertThatThrownBy {
            service.create(CareFixtures.actor(specialist.userId, RoleCode.SPECIALIST), specialist.id, CreateSlotCommand(NOW.plusSeconds(3600), 60))
        }.isInstanceOf(ConflictException::class.java)
            .extracting { (it as AiExException).code }
            .isEqualTo(ErrorCode.SLOT_OVERLAP)
    }

    @Test
    fun `фасад записи не ходит в dialog без расшаривания`() {
        val service = mockk<ConsultationService>()
        val booking = ConsultationBooking(service, mockk())
        val actor = CareFixtures.actor()
        val command = BookConsultationCommand(UUID.randomUUID())
        val session = CareFixtures.session()
        every { service.book(actor, command) } returns session

        assertThat(booking.book(actor, command)).isSameAs(session)
    }

    @Test
    fun `справочник - гонка создания и удаление используемой специализации через FK`() {
        val specializations = mockk<SpecializationRepository>()
        val catalog = SpecializationCatalogAdapter(specializations)
        every { specializations.existsByCode("dup") } returns false
        every { specializations.saveAndFlush(any()) } throws DataIntegrityViolationException("uq_specializations_code")
        assertThatThrownBy { catalog.create("dup", "Дубль") }
            .extracting { (it as AiExException).code }
            .isEqualTo(ErrorCode.DICTIONARY_CODE_TAKEN)

        val used = CareFixtures.specialization("used")
        every { specializations.findById(7) } returns used
        every { specializations.isInUse(7) } returns false
        every { specializations.deleteAndFlush(used) } throws DataIntegrityViolationException("fk_specialist_specializations_specialization")
        assertThatThrownBy { catalog.delete(7) }
            .extracting { (it as AiExException).code }
            .isEqualTo(ErrorCode.CONSTRAINT_VIOLATED)
        every { specializations.deleteAndFlush(used) } just runs
        catalog.delete(7)
    }
}
