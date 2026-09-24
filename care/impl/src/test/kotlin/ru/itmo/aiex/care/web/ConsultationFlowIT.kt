package ru.itmo.aiex.care.web

import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.care.CareIntegrationTest
import ru.itmo.aiex.care.api.ConsultationQuery
import ru.itmo.aiex.common.events.ConsultationRequested
import ru.itmo.aiex.common.events.ConsultationStatusChanged
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.common.security.RoleCode
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RecordApplicationEvents
class ConsultationFlowIT : CareIntegrationTest() {
    @Autowired
    private lateinit var events: ApplicationEvents

    @Autowired
    private lateinit var consultationQuery: ConsultationQuery

    @Autowired
    private lateinit var metricsContributors: List<MetricsContributor>

    @Test
    fun `слот в прошлом - 400, пересекающийся - 409, встык - 201, Location ведёт на слот`() {
        val specialist = createSpecialist()
        postSlot(specialist.userId, specialist.id, java.time.Instant.now().minusSeconds(60)).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("startsAt") }
        }
        val first =
            postSlot(specialist.userId, specialist.id, at(10), 90)
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.endsAt") { value(at(11, 30).toString()) }
                }.andReturn()
        postSlot(specialist.userId, specialist.id, at(11), 60).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SLOT_OVERLAP") }
        }
        postSlot(specialist.userId, specialist.id, at(9), 240).andExpect { jsonPath("$.code") { value("SLOT_OVERLAP") } }
        postSlot(specialist.userId, specialist.id, at(10), 30).andExpect { jsonPath("$.code") { value("SLOT_OVERLAP") } }
        postSlot(specialist.userId, specialist.id, at(11, 30), 30).andExpect { status { isCreated() } }
        postSlot(specialist.userId, specialist.id, at(9, 30), 30).andExpect { status { isCreated() } }

        val location = first.response.getHeader("Location")!!
        assertThat(location).contains("/api/v1/specialists/${specialist.id}/slots/")
        mockMvc.get(location).andExpect {
            status { isOk() }
            jsonPath("$.durationMin") { value(90) }
        }
    }

    @Test
    fun `слоты публикует только владелец-специалист, длительность 15-240`() {
        val owner = createSpecialist()
        val other = createSpecialist()
        val admin = createAdmin()
        postSlot(other.userId, owner.id, at(10)).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        postSlot(admin, owner.id, at(10)).andExpect { status { isForbidden() } }
        postSlot(owner.userId, UUID.randomUUID(), at(10)).andExpect { jsonPath("$.code") { value("SPECIALIST_NOT_FOUND") } }
        postSlot(owner.userId, owner.id, at(10), 10).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("durationMin") }
        }
        postSlot(owner.userId, owner.id, at(10), 241).andExpect { status { isBadRequest() } }
    }

    @Test
    fun `свободные слоты - занятый исчезает из выдачи, окно from-to и X-Total-Count`() {
        val specialist = createSpecialist()
        val slots = (10L..13L).map { createSlot(specialist, at(it)) }
        bookOk(createUser(), slots[1])

        mockMvc.get("/api/v1/specialists/${specialist.id}/slots").andExpect {
            status { isOk() }
            header { string("X-Total-Count", "3") }
            jsonPath("$[0].id") { value(slots[0].toString()) }
            jsonPath("$[1].id") { value(slots[2].toString()) }
        }
        mockMvc.get("/api/v1/specialists/${specialist.id}/slots?from=${at(12)}&to=${at(13)}").andExpect {
            header { string("X-Total-Count", "1") }
            jsonPath("$[0].id") { value(slots[2].toString()) }
        }
        mockMvc.get("/api/v1/specialists/${specialist.id}/slots?from=${at(13)}&to=${at(12)}").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("to") }
        }
        mockMvc.get("/api/v1/specialists/${specialist.id}/slots/${UUID.randomUUID()}").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SLOT_NOT_FOUND") }
        }
        val otherSlot = createSlot(createSpecialist(), at(10))
        mockMvc.get("/api/v1/specialists/${specialist.id}/slots/$otherSlot").andExpect { status { isNotFound() } }
    }

    @Test
    fun `полный цикл REQUESTED, CONFIRMED, DONE, резюме, оценка и события`() {
        val specialist = createSpecialist()
        val client = createUser()
        val slotId = createSlot(specialist, at(10), 45)

        val booked =
            book(client, slotId)
                .andExpect {
                    status { isCreated() }
                    header { string("Location", containsString("/api/v1/consultations/")) }
                    jsonPath("$.status") { value("REQUESTED") }
                    jsonPath("$.clientId") { value(client.toString()) }
                    jsonPath("$.specialistId") { value(specialist.id.toString()) }
                    jsonPath("$.slotId") { value(slotId.toString()) }
                    jsonPath("$.startsAt") { value(at(10).toString()) }
                    jsonPath("$.durationMin") { value(45) }
                }.andReturn()
        val id = UUID.fromString(booked.json()["id"].asString())
        assertThat(bookedCount(specialist.id)).isEqualTo(1)
        val requested = events.stream(ConsultationRequested::class.java).toList().single()
        assertThat(requested.sessionId).isEqualTo(id)
        assertThat(requested.clientId).isEqualTo(client)
        assertThat(requested.specialistUserId).isEqualTo(specialist.userId)

        patchConsultation(specialist.userId, id, mapOf("status" to "CONFIRMED")).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CONFIRMED") }
        }
        patchConsultation(specialist.userId, id, mapOf("status" to "DONE", "summary" to "Обсудили расставание", "recommendations" to "Дневник"))
            .andExpect {
                jsonPath("$.status") { value("DONE") }
                jsonPath("$.summary") { value("Обсудили расставание") }
                jsonPath("$.recommendations") { value("Дневник") }
            }
        patchConsultation(client, id, mapOf("rating" to 5)).andExpect {
            status { isOk() }
            jsonPath("$.rating") { value(5) }
        }
        mockMvc.get("/api/v1/consultations/$id") { header(USER_HEADER, client) }.andExpect { jsonPath("$.rating") { value(5) } }

        assertThat(events.stream(ConsultationStatusChanged::class.java).map { it.status }.toList()).containsExactly("CONFIRMED", "DONE")
        assertThat(bookedCount(specialist.id)).isEqualTo(1)
    }

    @Test
    fun `отмена освобождает слот - его можно забронировать снова, счётчик уменьшается`() {
        val specialist = createSpecialist()
        val slotId = createSlot(specialist, at(10))
        val first = bookOk(createUser(), slotId)
        val client = jdbcTemplate.queryForObject("SELECT user_id FROM care.consultation_sessions WHERE id = ?", UUID::class.java, first)!!

        book(createUser(), slotId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SLOT_TAKEN") }
        }
        patchConsultation(client, first, mapOf("status" to "CANCELLED", "cancelReason" to "Заболела")).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CANCELLED") }
            jsonPath("$.cancelReason") { value("Заболела") }
        }
        assertThat(bookedCount(specialist.id)).isZero()
        mockMvc.get("/api/v1/specialists/${specialist.id}/slots").andExpect { header { string("X-Total-Count", "1") } }

        bookOk(createUser(), slotId)
        assertThat(bookedCount(specialist.id)).isEqualTo(1)
        patchConsultation(client, first, mapOf("status" to "CANCELLED")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONSULTATION_INVALID_STATE") }
        }
    }

    @Test
    fun `права ролей на поля и переходы - 403 для чужого действия, 409 для неверного состояния`() {
        val specialist = createSpecialist()
        val client = createUser()
        val id = bookOk(client, createSlot(specialist, at(10)))

        patchConsultation(client, id, mapOf("status" to "CONFIRMED")).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        patchConsultation(client, id, mapOf("summary" to "Сам себе резюме")).andExpect { status { isForbidden() } }
        patchConsultation(specialist.userId, id, mapOf("rating" to 5)).andExpect { status { isForbidden() } }
        patchConsultation(client, id, mapOf("rating" to 4)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONSULTATION_INVALID_STATE") }
        }
        patchConsultation(specialist.userId, id, mapOf("status" to "DONE")).andExpect { jsonPath("$.code") { value("CONSULTATION_INVALID_STATE") } }
        patchConsultation(specialist.userId, id, mapOf("summary" to "Рано")).andExpect { status { isConflict() } }
        patchConsultation(specialist.userId, id, mapOf("status" to "REQUESTED")).andExpect { status { isForbidden() } }
        patchConsultation(client, id, mapOf("cancelReason" to "без отмены")).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("cancelReason") }
        }
        patchConsultation(client, id, mapOf("rating" to 6)).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("rating") }
        }
        mockMvc.get("/api/v1/consultations/$id") { header(USER_HEADER, client) }.andExpect { jsonPath("$.status") { value("REQUESTED") } }
    }

    @Test
    fun `посторонний не видит консультацию - 404, администратор закрывает её, но резюме не пишет`() {
        val specialist = createSpecialist()
        val client = createUser()
        val admin = createAdmin()
        val id = bookOk(client, createSlot(specialist, at(10)))
        val stranger = createUser()

        mockMvc.get("/api/v1/consultations/$id") { header(USER_HEADER, stranger) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CONSULTATION_NOT_FOUND") }
        }
        patchConsultation(stranger, id, mapOf("status" to "CANCELLED")).andExpect { status { isNotFound() } }
        patchConsultation(createSpecialist().userId, id, mapOf("status" to "CONFIRMED")).andExpect { status { isNotFound() } }
        patchConsultation(admin, UUID.randomUUID(), mapOf("status" to "DONE")).andExpect { status { isNotFound() } }

        mockMvc.get("/api/v1/consultations/$id") { header(USER_HEADER, admin) }.andExpect { status { isOk() } }
        patchConsultation(admin, id, mapOf("summary" to "Админ пишет")).andExpect { status { isForbidden() } }
        patchConsultation(admin, id, mapOf("status" to "CONFIRMED")).andExpect { status { isForbidden() } }
        patchConsultation(admin, id, mapOf("status" to "DONE")).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DONE") }
        }
        patchConsultation(admin, id, mapOf("status" to "CANCELLED")).andExpect { status { isConflict() } }
    }

    @Test
    fun `запись - только роль USER, не к самому себе, слот существует, в будущем и у активного специалиста`() {
        val specialist = createSpecialist(codes = listOf("breakup"), price = "100.00", RoleCode.SPECIALIST, RoleCode.USER)
        val slotId = createSlot(specialist, at(10))

        book(specialist.userId, slotId).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("FORBIDDEN") }
        }
        book(createUser(RoleCode.SPECIALIST), slotId).andExpect { status { isForbidden() } }
        book(createUser(), UUID.randomUUID()).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SLOT_NOT_FOUND") }
        }

        val pastSlot = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO care.specialist_slots (id, specialist_id, starts_at, duration_min, created_at)
            VALUES (?, ?, now() - interval '2 hours', 60, now())
            """.trimIndent(),
            pastSlot,
            specialist.id,
        )
        book(createUser(), pastSlot).andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("slotId") }
        }

        mockMvc
            .patch("/api/v1/specialists/${specialist.id}") {
                header(USER_HEADER, specialist.userId)
                contentType = MediaType.APPLICATION_JSON
                content = json(mapOf("status" to "INACTIVE"))
            }.andExpect { status { isOk() } }
        book(createUser(), slotId).andExpect { jsonPath("$.code") { value("SLOT_NOT_FOUND") } }
        mockMvc.get("/api/v1/specialists/${specialist.id}/slots").andExpect { status { isNotFound() } }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM care.consultation_sessions", Long::class.java)).isZero()
    }

    @Test
    fun `расшарить можно только свою беседу - чужая 404, своя видна специалисту, пока консультация активна`() {
        val specialist = createSpecialist()
        val otherSpecialist = createSpecialist()
        val client = createUser()
        val foreignConversation = UUID.randomUUID()
        val ownConversation = UUID.randomUUID()
        every { dialogs.isConversationOwnedBy(foreignConversation, client) } returns false
        every { dialogs.isConversationOwnedBy(ownConversation, client) } returns true

        book(client, createSlot(specialist, at(10)), foreignConversation).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CONVERSATION_NOT_FOUND") }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM care.consultation_sessions", Long::class.java)).isZero()

        val id =
            book(client, createSlot(specialist, at(12)), ownConversation)
                .andExpect { jsonPath("$.sharedConversationId") { value(ownConversation.toString()) } }
                .andReturn()
                .json()["id"]
                .asString()
                .let(UUID::fromString)
        verify(exactly = 1) { dialogs.isConversationOwnedBy(ownConversation, client) }

        assertThat(consultationQuery.isConversationSharedWith(ownConversation, specialist.userId)).isTrue()
        assertThat(consultationQuery.isConversationSharedWith(ownConversation, otherSpecialist.userId)).isFalse()
        assertThat(consultationQuery.isConversationSharedWith(foreignConversation, specialist.userId)).isFalse()
        assertThat(consultationQuery.hasActiveSession(client, specialist.id)).isTrue()
        assertThat(consultationQuery.hasActiveSession(client, otherSpecialist.id)).isFalse()

        patchConsultation(client, id, mapOf("status" to "CANCELLED")).andExpect { status { isOk() } }
        assertThat(consultationQuery.isConversationSharedWith(ownConversation, specialist.userId)).isFalse()
        assertThat(consultationQuery.hasActiveSession(client, specialist.id)).isFalse()
    }

    @Test
    fun `мои консультации - клиент и специалист видят свои, фильтр по статусу и X-Total-Count`() {
        val specialist = createSpecialist()
        val client = createUser()
        val first = bookOk(client, createSlot(specialist, at(10)))
        bookOk(client, createSlot(specialist, at(11)))
        bookOk(createUser(), createSlot(specialist, at(12)))
        patchConsultation(specialist.userId, first, mapOf("status" to "CONFIRMED")).andExpect { status { isOk() } }

        mockMvc.get("/api/v1/consultations?sort=startsAt,asc") { header(USER_HEADER, client) }.andExpect {
            status { isOk() }
            header { string("X-Total-Count", "2") }
            jsonPath("$[0].id") { value(first.toString()) }
        }
        mockMvc.get("/api/v1/consultations") { header(USER_HEADER, specialist.userId) }.andExpect { header { string("X-Total-Count", "3") } }
        mockMvc.get("/api/v1/consultations?status=CONFIRMED") { header(USER_HEADER, specialist.userId) }.andExpect {
            header { string("X-Total-Count", "1") }
            jsonPath("$[0].status") { value("CONFIRMED") }
        }
        mockMvc.get("/api/v1/consultations") { header(USER_HEADER, createUser()) }.andExpect { header { string("X-Total-Count", "0") } }
        mockMvc.get("/api/v1/consultations").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `два клиента одновременно бронируют один слот - ровно одна запись и один 409 SLOT_TAKEN`() {
        val specialist = createSpecialist()
        val slotId = createSlot(specialist, at(10))
        val clients = listOf(createUser(), createUser())
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(clients.size)
        try {
            val futures =
                clients.map { client ->
                    pool.submit(
                        Callable<MockHttpServletResponse> {
                            start.await()
                            book(client, slotId).andReturn().response
                        },
                    )
                }
            start.countDown()
            val responses = futures.map { it.get(30, TimeUnit.SECONDS) }

            assertThat(responses.map { it.status }).containsExactlyInAnyOrder(201, 409)
            val conflict = responses.single { it.status == 409 }
            assertThat(jsonMapper.readTree(conflict.contentAsString)["code"].asString()).isEqualTo("SLOT_TAKEN")
        } finally {
            pool.shutdownNow()
        }
        val active =
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM care.consultation_sessions WHERE slot_id = ? AND status IN ('REQUESTED', 'CONFIRMED')",
                Long::class.java,
                slotId,
            )
        assertThat(active).isEqualTo(1)
        assertThat(bookedCount(specialist.id)).isEqualTo(1)
    }

    @Test
    fun `метрики care считают активных специалистов и консультации по статусам`() {
        val specialist = createSpecialist()
        val client = createUser()
        val cancelled = bookOk(client, createSlot(specialist, at(10)))
        bookOk(client, createSlot(specialist, at(11)))
        patchConsultation(client, cancelled, mapOf("status" to "CANCELLED")).andExpect { status { isOk() } }

        val metrics = metricsContributors.map { it.metrics() }.reduce { acc, map -> acc + map }
        assertThat(metrics).containsEntry("specialists.active", 1L)
        assertThat(metrics).containsEntry("consultations.requested", 1L)
        assertThat(metrics).containsEntry("consultations.cancelled", 1L)
        assertThat(metrics).containsEntry("consultations.confirmed", 0L)
        assertThat(metrics).containsEntry("consultations.done", 0L)
    }

    @Test
    fun `тело записи без slotId - 400`() {
        mockMvc
            .post("/api/v1/consultations") {
                header(USER_HEADER, createUser())
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }
}
