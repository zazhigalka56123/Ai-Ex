package ru.itmo.aiex.iam.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.entity.Role
internal interface RoleJpaRepository : JpaRepository<Role, Long> {
    fun findAllByCodeIn(codes: Collection<RoleCode>): List<Role>
}
