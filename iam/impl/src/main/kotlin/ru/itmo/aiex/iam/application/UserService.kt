package ru.itmo.aiex.iam.application

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.ForbiddenException
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.iam.domain.Role
import ru.itmo.aiex.iam.domain.User
import ru.itmo.aiex.iam.domain.UserStatus
import ru.itmo.aiex.iam.domain.port.RoleRepository
import ru.itmo.aiex.iam.domain.port.UserRepository
import java.time.Clock
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UserService(private val users: UserRepository, private val roles: RoleRepository, private val clock: Clock) {
    @Transactional
    fun register(command: RegisterUserCommand): User {
        val email = normalizeEmail(command.email)
        if (users.existsByEmail(email)) throw emailTaken(email)
        val now = clock.nowMicros()
        val user = User(id = Ids.next(), email = email, displayName = command.displayName.trim(), createdAt = now, updatedAt = now)
        user.replaceRoles(resolveRoles(command.roles.ifEmpty { setOf(RoleCode.USER) }), now)
        return try {
            users.saveAndFlush(user)
        } catch (ex: DataIntegrityViolationException) {
            throw emailTaken(email, ex)
        }
    }

    fun list(actor: Actor, status: UserStatus?, page: PageQuery): PageView<User> {
        actor.requireRole(RoleCode.ADMIN)
        return users.findPage(status, page)
    }

    fun get(actor: Actor, userId: UUID): User {
        if (actor.userId != userId && !actor.isAdmin) throw NotFoundException.of(ErrorCode.USER_NOT_FOUND, userId)
        return users.findById(userId) ?: throw NotFoundException.of(ErrorCode.USER_NOT_FOUND, userId)
    }

    @Transactional
    fun update(actor: Actor, userId: UUID, command: UpdateUserCommand): User {
        val user = get(actor, userId)
        if ((command.status != null || command.roles != null) && !actor.isAdmin) {
            throw ForbiddenException("Статус и роли меняет только администратор")
        }
        if (actor.userId == userId && command.status == UserStatus.BLOCKED) {
            throw ConflictException(ErrorCode.USER_INVALID_STATE, "Администратор не может заблокировать сам себя")
        }
        val now = clock.nowMicros()
        command.displayName?.let { user.rename(it.trim(), now) }
        command.status?.let { user.changeStatus(it, now) }
        command.roles?.let { user.replaceRoles(resolveRoles(it), now) }
        return users.saveAndFlush(user)
    }

    private fun resolveRoles(codes: Set<RoleCode>): List<Role> {
        if (codes.isEmpty()) throw ValidationException("roles", "size", "Нужна хотя бы одна роль")
        return roles.findByCodes(codes).also { found ->
            check(found.size == codes.size) { "Справочник ролей неполон: ожидались $codes" }
        }
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun emailTaken(email: String, cause: Throwable? = null) =
        ConflictException(ErrorCode.EMAIL_TAKEN, "Email $email уже зарегистрирован", cause)
}
