package ru.itmo.aiex.iam.repository

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.persistence.toPageView
import ru.itmo.aiex.common.persistence.toPageable
import ru.itmo.aiex.iam.entity.User
import ru.itmo.aiex.iam.entity.UserStatus
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
