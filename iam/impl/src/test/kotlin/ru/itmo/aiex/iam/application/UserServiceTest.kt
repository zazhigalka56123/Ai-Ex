package ru.itmo.aiex.iam.application

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.domain.Role
import ru.itmo.aiex.iam.domain.port.RoleRepository
import ru.itmo.aiex.iam.domain.port.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UserServiceTest {
    private val users = mockk<UserRepository>()
    private val roles = mockk<RoleRepository> { every { findByCodes(any()) } answers { firstArg<Collection<RoleCode>>().map(::Role) } }
    private val service = UserService(users, roles, Clock.fixed(Instant.parse("2026-09-24T10:00:00Z"), ZoneOffset.UTC))

    @Test
    fun `гонка двух регистраций с одним email - 409 EMAIL_TAKEN с исходной причиной, а не 500`() {
        val race = DataIntegrityViolationException("uq_users_email")
        every { users.existsByEmail("race@example.com") } returns false
        every { users.saveAndFlush(any()) } throws race

        assertThatThrownBy { service.register(RegisterUserCommand("Race@Example.com ", "Гонка", emptySet())) }
            .isInstanceOf(ConflictException::class.java)
            .hasCause(race)
            .extracting("code")
            .isEqualTo(ErrorCode.EMAIL_TAKEN)
    }

    @Test
    fun `email нормализуется, роль по умолчанию - USER`() {
        every { users.existsByEmail("masha@example.com") } returns false
        every { users.saveAndFlush(any()) } answers { firstArg() }

        val user = service.register(RegisterUserCommand("  Masha@Example.COM", " Маша ", emptySet()))

        assertThat(user.email).isEqualTo("masha@example.com")
        assertThat(user.displayName).isEqualTo("Маша")
        assertThat(user.roleCodes).containsExactly(RoleCode.USER)
    }
}
