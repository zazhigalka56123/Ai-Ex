package ru.itmo.aiex.iam.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.repository.UserRepository
import java.util.UUID

@Component
class SupervisorBootstrap(
    private val users: UserRepository,
    private val service: UserService,
    @Value("\${aiex.security.bootstrap-email:}") private val email: String,
    @Value("\${aiex.security.bootstrap-password:}") private val password: String,
) : ApplicationRunner {
    @Transactional
    override fun run(args: ApplicationArguments) {
        if (email.isBlank() && password.isBlank()) return
        require(email.isNotBlank() && password.isNotBlank()) { "Для первого супервайзера задайте email и пароль" }
        val actor = Actor(UUID(0, 0), setOf(RoleCode.ADMIN))
        val existing = users.findByEmail(email.trim().lowercase())
        if (existing == null) {
            service.registerUser(actor, RegisterUserCommand(email, "Супервайзер", setOf(RoleCode.ADMIN, RoleCode.USER), password))
        } else if (existing.passwordHash == "!" && RoleCode.ADMIN in existing.roleCodes) {
            service.updateUser(actor, existing.id, UpdateUserCommand(password = password))
        }
    }
}
