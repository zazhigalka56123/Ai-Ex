package ru.itmo.aiex.dialog.domain

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

class DialogEntityValidationIT : AbstractIntegrationTest() {
    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    private val now: Instant = Instant.parse("2026-09-22T03:00:00Z")

    private fun conversation(title: String = "Маша") = Conversation(Ids.next(), UUID.randomUUID(), UUID.randomUUID(), title, now)

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

    private fun violatedPaths(ex: Throwable): List<String> =
        (ex as ConstraintViolationException).constraintViolations.map { it.propertyPath.toString() }

    @Test
    fun `валидная беседа с сообщением доходит до вставки - проверка не вырождена`() {
        assertThatCode {
            flush { entityManager ->
                val conversation = conversation()
                entityManager.persist(conversation)
                entityManager.flush()
                entityManager.persist(Message.fromUser(conversation, "привет, спишь?", now))
            }
        }.doesNotThrowAnyException()
    }

    @Test
    fun `пустое название беседы отклоняется на flush`() {
        assertThatThrownBy { flush { it.persist(conversation(title = " ")) } }
            .isInstanceOf(ConstraintViolationException::class.java)
            .satisfies({ assertThat(violatedPaths(it)).contains("title") })
    }

    @Test
    fun `пустой текст сообщения отклоняется на flush`() {
        assertThatThrownBy {
            flush { entityManager ->
                val conversation = conversation()
                entityManager.persist(conversation)
                entityManager.flush()
                entityManager.persist(Message.fromUser(conversation, "  ", now))
            }
        }.isInstanceOf(ConstraintViolationException::class.java)
            .satisfies({ assertThat(violatedPaths(it)).contains("body") })
    }

    @Test
    fun `текст длиннее лимита отклоняется на flush до SQL`() {
        assertThatThrownBy {
            flush { entityManager ->
                val conversation = conversation()
                entityManager.persist(conversation)
                entityManager.flush()
                entityManager.persist(Message.fromUser(conversation, "а".repeat(Message.MAX_BODY_LENGTH + 1), now))
            }
        }.isInstanceOf(ConstraintViolationException::class.java)
            .satisfies({ assertThat(violatedPaths(it)).contains("body") })
    }
}
