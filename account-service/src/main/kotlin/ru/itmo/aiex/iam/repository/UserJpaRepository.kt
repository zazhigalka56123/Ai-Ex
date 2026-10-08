package ru.itmo.aiex.iam.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.iam.entity.User
import ru.itmo.aiex.iam.entity.UserStatus
import java.util.UUID

internal interface UserJpaRepository : JpaRepository<User, UUID> {
    fun existsByEmail(email: String): Boolean

    fun findAllByStatus(status: UserStatus, pageable: Pageable): Page<User>

    fun countByStatus(status: UserStatus): Long
}
