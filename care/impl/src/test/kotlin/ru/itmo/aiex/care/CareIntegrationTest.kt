package ru.itmo.aiex.care

import com.ninjasquad.springmockk.MockkBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.dialog.api.DialogQuery
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

abstract class CareIntegrationTest : AbstractIntegrationTest() {
    @MockkBean
    protected lateinit var dialogs: DialogQuery

    protected val base: Instant = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)

    protected fun at(hours: Long, minutes: Long = 0): Instant = base.plus(hours, ChronoUnit.HOURS).plus(minutes, ChronoUnit.MINUTES)

    protected fun createSpecialist(
        codes: List<String> = listOf("breakup"),
        price: String = "2500.00",
        vararg roles: RoleCode = arrayOf(RoleCode.SPECIALIST),
    ): SpecialistFixture {
        val userId = createUser(*roles, displayName = "Анна Психолог")
        val result =
            mockMvc
                .post("/api/v1/specialists") {
                    header(USER_HEADER, userId)
                    contentType = MediaType.APPLICATION_JSON
                    content = json(specialistBody(codes, price))
                }.andExpect { status { isCreated() } }
                .andReturn()
        return SpecialistFixture(userId, UUID.fromString(result.json()["id"].asString()))
    }

    protected fun specialistBody(codes: List<String> = listOf("breakup"), price: String = "2500.00") = mapOf(
        "headline" to "Психолог, помогаю пережить расставание",
        "bio" to "КПТ, стаж 7 лет",
        "pricePerHour" to price,
        "specializationCodes" to codes,
    )

    protected fun postSlot(actor: UUID, specialistId: UUID, startsAt: Instant, durationMin: Int = 60): ResultActionsDsl =
        mockMvc.post("/api/v1/specialists/$specialistId/slots") {
            header(USER_HEADER, actor)
            contentType = MediaType.APPLICATION_JSON
            content = json(mapOf("startsAt" to startsAt.toString(), "durationMin" to durationMin))
        }

    protected fun createSlot(specialist: SpecialistFixture, startsAt: Instant, durationMin: Int = 60): UUID {
        val result = postSlot(specialist.userId, specialist.id, startsAt, durationMin).andExpect { status { isCreated() } }.andReturn()
        return UUID.fromString(result.json()["id"].asString())
    }

    protected fun book(clientId: UUID, slotId: UUID, conversationId: UUID? = null): ResultActionsDsl = mockMvc.post("/api/v1/consultations") {
        header(USER_HEADER, clientId)
        contentType = MediaType.APPLICATION_JSON
        content = json(mapOf("slotId" to slotId, "sharedConversationId" to conversationId))
    }

    protected fun bookOk(clientId: UUID, slotId: UUID, conversationId: UUID? = null): UUID {
        val result = book(clientId, slotId, conversationId).andExpect { status { isCreated() } }.andReturn()
        return UUID.fromString(result.json()["id"].asString())
    }

    protected fun patchConsultation(actor: UUID, id: UUID, body: Map<String, Any?>): ResultActionsDsl = mockMvc.patch("/api/v1/consultations/$id") {
        header(USER_HEADER, actor)
        contentType = MediaType.APPLICATION_JSON
        content = json(body)
    }

    protected fun bookedCount(specialistId: UUID): Int =
        jdbcTemplate.queryForObject("SELECT booked_count FROM care.specialists WHERE id = ?", Int::class.java, specialistId)!!
}
