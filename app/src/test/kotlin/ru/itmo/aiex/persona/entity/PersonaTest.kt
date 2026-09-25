package ru.itmo.aiex.persona.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import java.time.Instant
import java.util.UUID

class PersonaTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val t1 = t0.plusSeconds(60)

    private fun persona() = Persona(UUID.randomUUID(), UUID.randomUUID(), "Маша", RelationshipKind.EX_PARTNER, null, t0)

    private fun training() = persona().apply { startTraining(t0) }

    private fun ready() = training().apply { activateProfile(UUID.randomUUID(), t0) }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource(
        "DRAFT, TRAINING, true",
        "DRAFT, ARCHIVED, true",
        "DRAFT, READY, false",
        "DRAFT, DRAFT, false",
        "TRAINING, READY, true",
        "TRAINING, DRAFT, true",
        "TRAINING, ARCHIVED, true",
        "TRAINING, TRAINING, false",
        "READY, TRAINING, true",
        "READY, ARCHIVED, true",
        "READY, DRAFT, false",
        "READY, READY, false",
        "ARCHIVED, DRAFT, false",
        "ARCHIVED, TRAINING, false",
        "ARCHIVED, READY, false",
        "ARCHIVED, ARCHIVED, false",
    )
    fun `автомат статусов разрешает только допустимые переходы`(from: PersonaStatus, to: PersonaStatus, allowed: Boolean) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed)
    }

    @Test
    fun `новая персона - черновик без профиля`() {
        val persona = persona()
        assertThat(persona.status).isEqualTo(PersonaStatus.DRAFT)
        assertThat(persona.activeProfileId).isNull()
        assertThat(persona.updatedAt).isEqualTo(t0)
        assertThat(persona.isOwnedBy(persona.ownerId)).isTrue()
        assertThat(persona.isOwnedBy(UUID.randomUUID())).isFalse()
    }

    @Test
    fun `полный цикл DRAFT, TRAINING, READY, TRAINING, READY`() {
        val persona = persona()
        persona.startTraining(t1)
        assertThat(persona.status).isEqualTo(PersonaStatus.TRAINING)
        assertThat(persona.updatedAt).isEqualTo(t1)

        val v1 = UUID.randomUUID()
        persona.activateProfile(v1, t1)
        assertThat(persona.status).isEqualTo(PersonaStatus.READY)
        assertThat(persona.activeProfileId).isEqualTo(v1)

        persona.startTraining(t1)
        val v2 = UUID.randomUUID()
        persona.activateProfile(v2, t1)
        assertThat(persona.activeProfileId).isEqualTo(v2)
    }

    @Test
    fun `активировать профиль можно только из TRAINING`() {
        assertThatThrownBy { persona().activateProfile(UUID.randomUUID(), t1) }
            .isInstanceOf(IllegalStateTransitionException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.PERSONA_INVALID_STATE)
            .hasFieldOrPropertyWithValue("from", "DRAFT")
            .hasFieldOrPropertyWithValue("to", "READY")
        assertThatThrownBy { ready().activateProfile(UUID.randomUUID(), t1) }.isInstanceOf(IllegalStateTransitionException::class.java)
    }

    @Test
    fun `второй старт обучения запрещён - это и есть защита от параллельного импорта`() {
        assertThatThrownBy { training().startTraining(t1) }
            .isInstanceOf(IllegalStateTransitionException::class.java)
            .hasFieldOrPropertyWithValue("from", "TRAINING")
    }

    @Test
    fun `упавший разбор возвращает в DRAFT без профиля и в READY с профилем`() {
        val fresh = training()
        assertThat(fresh.trainingFailed(t1)).isTrue()
        assertThat(fresh.status).isEqualTo(PersonaStatus.DRAFT)

        val retrained = ready().apply { startTraining(t1) }
        assertThat(retrained.trainingFailed(t1)).isTrue()
        assertThat(retrained.status).isEqualTo(PersonaStatus.READY)
        assertThat(retrained.activeProfileId).isNotNull()
    }

    @Test
    fun `упавший разбор вне TRAINING - no-op`() {
        val draft = persona()
        assertThat(draft.trainingFailed(t1)).isFalse()
        assertThat(draft.status).isEqualTo(PersonaStatus.DRAFT)
        val archived = persona().apply { archive(t0) }
        assertThat(archived.trainingFailed(t1)).isFalse()
        assertThat(archived.status).isEqualTo(PersonaStatus.ARCHIVED)
    }

    @Test
    fun `архивация снимает профиль, повторная - no-op, из архива выхода нет`() {
        val persona = ready()
        assertThat(persona.archive(t1)).isTrue()
        assertThat(persona.status).isEqualTo(PersonaStatus.ARCHIVED)
        assertThat(persona.activeProfileId).isNull()
        assertThat(persona.archivedAt).isEqualTo(t1)
        assertThat(persona.archive(t1.plusSeconds(5))).isFalse()
        assertThat(persona.archivedAt).isEqualTo(t1)

        assertThatThrownBy { persona.startTraining(t1) }
            .isInstanceOf(IllegalStateTransitionException::class.java)
            .hasFieldOrPropertyWithValue("from", "ARCHIVED")
    }

    @Test
    fun `архивировать можно и во время обучения`() {
        val persona = training()
        assertThat(persona.archive(t1)).isTrue()
        assertThat(persona.status).isEqualTo(PersonaStatus.ARCHIVED)
    }

    @Test
    fun `редактирование меняет только переданные поля, архивную персону не редактируют`() {
        val persona = persona()
        persona.edit("Мария", null, "описание", clearDescription = false, now = t1)
        assertThat(persona.name).isEqualTo("Мария")
        assertThat(persona.relationshipKind).isEqualTo(RelationshipKind.EX_PARTNER)
        assertThat(persona.description).isEqualTo("описание")
        assertThat(persona.updatedAt).isEqualTo(t1)

        persona.edit(null, RelationshipKind.FRIEND, null, clearDescription = true, now = t1)
        assertThat(persona.relationshipKind).isEqualTo(RelationshipKind.FRIEND)
        assertThat(persona.description).isNull()

        persona.archive(t1)
        assertThatThrownBy { persona.edit("x", null, null, clearDescription = false, now = t1) }
            .isInstanceOf(ConflictException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.PERSONA_INVALID_STATE)
    }

    @Test
    fun `равенство сущностей - по id`() {
        val id = UUID.randomUUID()
        val a = Persona(id, UUID.randomUUID(), "A", RelationshipKind.OTHER, null, t0)
        val b = Persona(id, UUID.randomUUID(), "B", RelationshipKind.FRIEND, null, t1)
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b)
        assertThat(a).isNotEqualTo(persona())
    }

    @Test
    fun `ручной тег не перевешивается автоанализом, автотег становится ручным при явной установке`() {
        val persona = persona()
        val tag = Tag("jealous", "ревнивая").also { setId(it, 7L) }
        val personaTag = PersonaTag(persona, tag, "0.500".toBigDecimal(), TraitSource.AUTO, t0)
        assertThat(personaTag.id).isEqualTo(PersonaTagId(persona.id, 7L))

        personaTag.reweighAuto("0.800".toBigDecimal())
        assertThat(personaTag.weight).isEqualByComparingTo("0.8")

        personaTag.assignManually("0.300".toBigDecimal())
        assertThat(personaTag.source).isEqualTo(TraitSource.MANUAL)
        personaTag.reweighAuto("0.900".toBigDecimal())
        assertThat(personaTag.weight).isEqualByComparingTo("0.3")
        assertThat(PersonaTagId(persona.id, 7L)).isEqualTo(personaTag.id).hasSameHashCodeAs(personaTag.id)
    }

    private fun setId(tag: Tag, id: Long) {
        Tag::class.java.getDeclaredField("id").apply { isAccessible = true }.set(tag, id)
    }
}
