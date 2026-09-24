package ru.itmo.aiex.iam.infrastructure

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.iam.domain.User
import ru.itmo.aiex.iam.domain.UserStatus
import ru.itmo.aiex.iam.domain.port.UserRepository
import ru.itmo.aiex.persistence.toPageView
import ru.itmo.aiex.persistence.toPageable
import java.util.UUID

@Repository
internal class UserRepositoryAdapter(private val jpa: UserJpaRepository) : UserRepository {
    override fun saveAndFlush(user: User): User = jpa.saveAndFlush(user)

    override fun findById(id: UUID): User? = jpa.findByIdOrNull(id)

    override fun existsByEmail(email: String): Boolean = jpa.existsByEmail(email)

    override fun findPage(status: UserStatus?, page: PageQuery): PageView<User> {
        val pageable = page.toPageable()
        val result = if (status == null) jpa.findAll(pageable) else jpa.findAllByStatus(status, pageable)
        return result.toPageView()
    }

    override fun countByStatus(status: UserStatus): Long = jpa.countByStatus(status)
}
