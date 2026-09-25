package ru.itmo.aiex.persona.entity

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.testing.AbstractIntegrationTest
import java.time.Instant
import java.util.UUID

class PersonaEntityValidationIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    private fun persona(name: String = "Маша", description: String? = null) =
        Persona(Ids.next(), UUID.randomUUID(), name, RelationshipKind.EX_PARTNER, description, Instant.parse("2026-09-22T03:00:00Z"))

    private fun flush(block: (EntityManager) -> Unit) {
        val entityManager = entityManagerFactory.createEntityManager()
        try {
            entityManager.transaction.begin()
            block(entityManager)
            entityManager.flush()
        } finally {
            if (entityManager.transaction.isActive) entityManager.transaction.rollback()
            entityManager.close()
        }
    }

    @Test
    fun `валидная персона доходит до вставки - проверка не вырождена`() {
        assertThatCode { flush { it.persist(persona()) } }.doesNotThrowAnyException()
    }

    @Test
    fun `пустое имя отклоняется на flush, минуя контроллер и его DTO`() {
        assertThatThrownBy { flush { it.persist(persona(name = "   ")) } }
            .isInstanceOf(ConstraintViolationException::class.java)
            .satisfies({ ex ->
                val violations = (ex as ConstraintViolationException).constraintViolations
                assertThat(violations.map { it.propertyPath.toString() }).contains("name")
            })
    }

    @Test
    fun `слишком длинное описание отклоняется на flush до SQL`() {
        assertThatThrownBy { flush { it.persist(persona(description = "а".repeat(Persona.DESCRIPTION_MAX + 1))) } }
            .isInstanceOf(ConstraintViolationException::class.java)
            .satisfies({ ex ->
                val violations = (ex as ConstraintViolationException).constraintViolations
                assertThat(violations.map { it.propertyPath.toString() }).contains("description")
            })
    }

    @Test
    fun `тег справочника обязан иметь код в нижнем регистре`() {
        assertThatThrownBy { flush { it.persist(Tag("НЕ КОД", "Название")) } }
            .isInstanceOf(ConstraintViolationException::class.java)
            .satisfies({ ex ->
                val violations = (ex as ConstraintViolationException).constraintViolations
                assertThat(violations.map { it.propertyPath.toString() }).contains("code")
            })
    }
}
