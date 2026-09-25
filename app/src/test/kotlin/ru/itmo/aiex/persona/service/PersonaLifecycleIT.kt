package ru.itmo.aiex.persona.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.common.events.PersonaProfileActivated
import ru.itmo.aiex.common.metrics.MetricsContributor
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.persona.dto.PersonaState
import ru.itmo.aiex.persona.testing.PersonaIntegrationTest
import ru.itmo.aiex.persona.testing.TestCorpus
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RecordApplicationEvents
class PersonaLifecycleIT : PersonaIntegrationTest() {
    @Autowired
    private lateinit var lifecycle: PersonaLifecycle

    @Autowired
    private lateinit var access: PersonaAccess

    @Autowired
    private lateinit var profiles: PersonaProfileQuery

    @Autowired
    private lateinit var metrics: List<MetricsContributor>

    @Autowired
    private lateinit var events: ApplicationEvents

    private fun readyPersona(owner: UUID): UUID {
        val persona = createPersona(owner)
        lifecycle.startTraining(persona, owner, UUID.randomUUID())
        lifecycle.rebuildFrom(persona, TestCorpus.snapshot())
        return persona
    }

    @Test
    fun `DRAFT, TRAINING, READY - профиль собран, фразы в промпте, событие опубликовано`() {
        val owner = createUser()
        val persona = createPersona(owner)
        lifecycle.startTraining(persona, owner, UUID.randomUUID())
        assertThat(access.findSummary(persona)?.status).isEqualTo(PersonaState.TRAINING)
        assertThat(profiles.findActiveProfile(persona)).isNull()

        val result = lifecycle.rebuildFrom(persona, TestCorpus.snapshot())

        assertThat(result.versionNo).isEqualTo(1)
        assertThat(result.status).isEqualTo(PersonaState.READY)
        val summary = access.assertOwned(persona, owner)
        assertThat(summary.canChat).isTrue()
        assertThat(summary.activeProfileId).isEqualTo(result.profileId)

        val view = requireNotNull(profiles.findActiveProfile(persona))
        assertThat(view.profileId).isEqualTo(result.profileId)
        assertThat(view.personaName).isEqualTo("Маша")
        assertThat(view.systemPrompt).contains("- «неа»", "- «ГДЕ ТЫ БЫЛ»", "### Характер", "Узнаётся по фразам")
        assertThat(view.style.replySpeed).isEqualTo("slow")
        assertThat(view.style.samplePhrases).containsExactlyElementsOf(TestCorpus.COLD_PHRASES)
        assertThat(view.traits.map { it.key }).contains("jealousy", "night_owl")
        assertThat(view.tags).contains("cold", "jealous")
        assertThat(events.stream(PersonaProfileActivated::class.java).toList().map { it.personaId }).contains(persona)

        mockMvc.get("/api/v1/personas/$persona/profile") { header(USER_HEADER, owner) }.andExpect {
            status { isOk() }
            jsonPath("$.versionNo") { value(1) }
            jsonPath("$.profileId") { value(result.profileId.toString()) }
            jsonPath("$.systemPrompt") { value(containsString("- «с кем?»")) }
            jsonPath("$.style.replySpeed") { value("slow") }
            jsonPath("$.style.samplePhrases.length()") { value(TestCorpus.COLD_PHRASES.size) }
            jsonPath("$.corpusStats.avgReplyDelaySeconds") { value(4000) }
            jsonPath("$.traits.length()") { value(10) }
        }
        mockMvc.get("/api/v1/personas/$persona") { header(USER_HEADER, owner) }.andExpect {
            jsonPath("$.status") { value("READY") }
            jsonPath("$.activeProfile.versionNo") { value(1) }
            jsonPath("$.traits.length()") { value(10) }
        }
    }

    @Test
    fun `две пересборки - версии 1 и 2, активна ровно одна, черты заменены, а не задвоены`() {
        val owner = createUser()
        val persona = readyPersona(owner)
        lifecycle.startTraining(persona, owner, UUID.randomUUID())
        val second = lifecycle.rebuildFrom(
            persona,
            TestCorpus.snapshot(stats = TestCorpus.warmStats(), phrases = listOf("люблю тебя", "ну привет ❤")),
        )

        assertThat(second.versionNo).isEqualTo(2)
        assertThat(countRows("SELECT count(*) FROM persona.persona_profile_versions WHERE persona_id = ?", persona)).isEqualTo(2)
        assertThat(countRows("SELECT count(*) FROM persona.persona_profile_versions WHERE persona_id = ? AND active", persona)).isEqualTo(1)
        assertThat(countRows("SELECT count(*) FROM persona.persona_traits WHERE persona_id = ?", persona)).isEqualTo(10)
        assertThat(profiles.findActiveProfile(persona)?.versionNo).isEqualTo(2)
        assertThat(profiles.findActiveProfile(persona)?.systemPrompt).contains("- «люблю тебя»").doesNotContain("- «неа»")

        mockMvc.get("/api/v1/personas/$persona/profile/versions") { header(USER_HEADER, owner) }.andExpect {
            status { isOk() }
            header { string("X-Total-Count", "2") }
            jsonPath("$[0].versionNo") { value(2) }
            jsonPath("$[0].active") { value(true) }
            jsonPath("$[1].versionNo") { value(1) }
            jsonPath("$[1].active") { value(false) }
        }
    }

    @Test
    fun `profile_rebuild идемпотентен - повтор по тому же корпусу не создаёт версию`() {
        val owner = createUser()
        val persona = readyPersona(owner)
        repeat(2) {
            mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, owner) }.andExpect {
                status { isAccepted() }
                header { string("Location", containsString("/api/v1/personas/$persona/profile")) }
                jsonPath("$.versionNo") { value(1) }
                jsonPath("$.status") { value("READY") }
            }
        }
        assertThat(countRows("SELECT count(*) FROM persona.persona_profile_versions WHERE persona_id = ?", persona)).isEqualTo(1)
    }

    @Test
    fun `profile_rebuild из сохранённого корпуса возвращает застрявшую персону в READY - владелец или администратор`() {
        val owner = createUser()
        val persona = readyPersona(owner)

        lifecycle.startTraining(persona, owner, UUID.randomUUID())
        jdbcTemplate.update(
            "INSERT INTO persona.corpus_snapshots (id, persona_id, import_id, payload, created_at) " +
                "SELECT gen_random_uuid(), persona_id, gen_random_uuid(), payload, now() FROM persona.corpus_snapshots WHERE persona_id = ?",
            persona,
        )
        mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, createAdmin()) }.andExpect {
            status { isAccepted() }
            jsonPath("$.versionNo") { value(2) }
            jsonPath("$.status") { value("READY") }
        }
        mockMvc.post("/api/v1/personas/$persona/profile:rebuild") { header(USER_HEADER, owner) }.andExpect {
            jsonPath("$.versionNo") { value(2) }
        }
    }

    @Test
    fun `упавший разбор возвращает персону в DRAFT без профиля и в READY с профилем, вне TRAINING - no-op`() {
        val owner = createUser()
        val draft = createPersona(owner)
        lifecycle.startTraining(draft, owner, UUID.randomUUID())
        lifecycle.trainingFailed(draft, UUID.randomUUID())
        assertThat(access.findSummary(draft)?.status).isEqualTo(PersonaState.DRAFT)
        lifecycle.trainingFailed(draft, UUID.randomUUID())
        assertThat(access.findSummary(draft)?.status).isEqualTo(PersonaState.DRAFT)

        val ready = readyPersona(owner)
        lifecycle.startTraining(ready, owner, UUID.randomUUID())
        lifecycle.trainingFailed(ready, UUID.randomUUID())
        assertThat(access.findSummary(ready)?.status).isEqualTo(PersonaState.READY)
        assertThat(profiles.findActiveProfile(ready)).isNotNull()

        lifecycle.trainingFailed(UUID.randomUUID(), UUID.randomUUID())
    }

    @Test
    fun `старт обучения - только владельцу и только из DRAFT или READY`() {
        val owner = createUser()
        val persona = createPersona(owner)
        assertThatThrownBy { lifecycle.startTraining(persona, createUser(), UUID.randomUUID()) }
            .isInstanceOf(NotFoundException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.PERSONA_NOT_FOUND)
        lifecycle.startTraining(persona, owner, UUID.randomUUID())
        assertThatThrownBy { lifecycle.startTraining(persona, owner, UUID.randomUUID()) }
            .isInstanceOf(IllegalStateTransitionException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.PERSONA_INVALID_STATE)
    }

    @Test
    fun `два параллельных старта обучения одной персоны - проходит ровно один, второй ловит @Version или видит TRAINING`() {
        val owner = createUser()
        val persona = createPersona(owner)
        val start = CountDownLatch(1)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val pool = Executors.newFixedThreadPool(2)
        repeat(2) {
            pool.submit {
                start.await()
                runCatching { lifecycle.startTraining(persona, owner, UUID.randomUUID()) }.onFailure(errors::add)
            }
        }
        start.countDown()
        pool.shutdown()
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue()

        assertThat(errors).hasSize(1)
        assertThat(errors.single()).isInstanceOfAny(OptimisticLockingFailureException::class.java, IllegalStateTransitionException::class.java)
        assertThat(access.findSummary(persona)?.status).isEqualTo(PersonaState.TRAINING)
    }

    @Test
    fun `архивация (TX-4) - профиль деактивирован, теги сняты, событие, персона больше не собирается`() {
        val owner = createUser()
        val persona = readyPersona(owner)
        assertThat(countRows("SELECT count(*) FROM persona.persona_tags WHERE persona_id = ?", persona)).isPositive()

        lifecycle.archive(persona, Actor(owner, setOf(RoleCode.USER)))
        lifecycle.archive(persona, Actor(owner, setOf(RoleCode.USER)))

        assertThat(access.findSummary(persona)?.status).isEqualTo(PersonaState.ARCHIVED)
        assertThat(access.findSummary(persona)?.activeProfileId).isNull()
        assertThat(profiles.findActiveProfile(persona)).isNull()
        assertThat(countRows("SELECT count(*) FROM persona.persona_profile_versions WHERE persona_id = ? AND active", persona)).isZero()
        assertThat(countRows("SELECT count(*) FROM persona.persona_tags WHERE persona_id = ?", persona)).isZero()
        val archived = events.stream(PersonaArchived::class.java).toList().filter { it.personaId == persona }
        assertThat(archived).hasSize(1)
        assertThat(archived.single().byAdmin).isFalse()
        assertThat(archived.single().archivedBy).isEqualTo(owner)

        mockMvc.get("/api/v1/personas/$persona/profile") { header(USER_HEADER, owner) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PERSONA_NOT_READY") }
        }
        assertThatThrownBy { lifecycle.rebuildFrom(persona, TestCorpus.snapshot()) }
            .isInstanceOf(IllegalStateTransitionException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.PERSONA_INVALID_STATE)
        assertThatThrownBy { lifecycle.startTraining(persona, owner, UUID.randomUUID()) }.isInstanceOf(IllegalStateTransitionException::class.java)
    }

    @Test
    fun `администратор архивирует чужую - событие помечено byAdmin, посторонний получает 404`() {
        val owner = createUser()
        val persona = createPersona(owner)
        assertThatThrownBy { lifecycle.archive(persona, Actor(createUser(), setOf(RoleCode.USER))) }.isInstanceOf(NotFoundException::class.java)
        val admin = createAdmin()
        mockMvc.delete("/api/v1/personas/$persona") { header(USER_HEADER, admin) }.andExpect { status { isNoContent() } }
        val event = events.stream(PersonaArchived::class.java).toList().single { it.personaId == persona }
        assertThat(event.byAdmin).isTrue()
        assertThat(event.archivedBy).isEqualTo(admin)
        assertThat(event.ownerId).isEqualTo(owner)
    }

    @Test
    fun `проверка владения и сводка для других модулей`() {
        val owner = createUser()
        val persona = createPersona(owner)
        val summary = access.assertOwned(persona, owner)
        assertThat(summary.name).isEqualTo("Маша")
        assertThat(summary.status).isEqualTo(PersonaState.DRAFT)
        assertThat(summary.canChat).isFalse()
        assertThatThrownBy { access.assertOwned(persona, createUser()) }.isInstanceOf(NotFoundException::class.java)
        assertThatThrownBy { access.assertOwned(UUID.randomUUID(), owner) }.isInstanceOf(NotFoundException::class.java)
        assertThat(access.findSummary(UUID.randomUUID())).isNull()
        assertThat(profiles.findActiveProfile(UUID.randomUUID())).isNull()
        assertThatThrownBy { lifecycle.rebuildFrom(UUID.randomUUID(), TestCorpus.snapshot()) }.isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `метрики администратора - число персон по статусам`() {
        val owner = createUser()
        readyPersona(owner)
        createPersona(owner)
        val values = metrics.flatMap { it.metrics().entries }.associate { it.key to it.value }
        assertThat(values).containsEntry("personas.ready", 1L).containsEntry("personas.draft", 1L)
        assertThat(values).containsKeys("personas.training", "personas.archived")
    }
}
