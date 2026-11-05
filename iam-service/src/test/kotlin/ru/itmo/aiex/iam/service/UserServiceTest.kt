package ru.itmo.aiex.iam.service

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.ForbiddenException
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.entity.Role
import ru.itmo.aiex.iam.repository.RoleRepository
import ru.itmo.aiex.iam.repository.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class UserServiceTest {
    private val users = mockk<UserRepository>()
    private val roles = mockk<RoleRepository> { every { findByCodes(any()) } answers { firstArg<Collection<RoleCode>>().map(::Role) } }
    private val passwords = BCryptPasswordEncoder(4)
    private val admin = Actor(UUID.randomUUID(), setOf(RoleCode.ADMIN))
    private val service = UserService(users, roles, Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC), passwords)

    @Test
    fun `гонка двух регистраций с одним email - 409 EMAIL_TAKEN с исходной причиной, а не 500`() {
        val race = DataIntegrityViolationException("uq_users_email")
        every { users.existsByEmail("race@example.com") } returns false
        every { users.saveAndFlush(any()) } throws race

        assertThatThrownBy { service.registerUser(admin, RegisterUserCommand("Race@Example.com ", "Гонка", emptySet(), "Test-password-123!")) }
            .isInstanceOf(ConflictException::class.java)
            .hasCause(race)
            .extracting("code")
            .isEqualTo(ErrorCode.EMAIL_TAKEN)
    }

    @Test
    fun `email нормализуется, роль по умолчанию - USER`() {
        every { users.existsByEmail("masha@example.com") } returns false
        every { users.saveAndFlush(any()) } answers { firstArg() }

        val user = service.registerUser(admin, RegisterUserCommand("  Masha@Example.COM", " Маша ", emptySet(), "Test-password-123!"))

        assertThat(user.email).isEqualTo("masha@example.com")
        assertThat(user.displayName).isEqualTo("Маша")
        assertThat(user.roleCodes).containsExactly(RoleCode.USER)
        assertThat(user.passwordHash).isNotEqualTo("Test-password-123!")
        assertThat(passwords.matches("Test-password-123!", user.passwordHash)).isTrue()
    }
    @Test
    fun `обычный пользователь и специалист не создают пользователей`() {
        for (role in listOf(RoleCode.USER, RoleCode.SPECIALIST)) {
            assertThatThrownBy {
                service.registerUser(
                    Actor(UUID.randomUUID(), setOf(role)),
                    RegisterUserCommand("x@example.com", "X", setOf(RoleCode.ADMIN), "Test-password-123!"),
                )
            }.isInstanceOf(ForbiddenException::class.java)
        }
    }
}
