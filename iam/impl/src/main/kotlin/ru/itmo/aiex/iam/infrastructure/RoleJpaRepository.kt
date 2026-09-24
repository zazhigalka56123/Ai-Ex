package ru.itmo.aiex.iam.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.domain.Role

internal interface RoleJpaRepository : JpaRepository<Role, Long> {
    fun findAllByCodeIn(codes: Collection<RoleCode>): List<Role>
}
