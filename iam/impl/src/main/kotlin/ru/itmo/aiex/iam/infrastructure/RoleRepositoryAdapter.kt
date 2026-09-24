package ru.itmo.aiex.iam.infrastructure

import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.domain.Role
import ru.itmo.aiex.iam.domain.port.RoleRepository

@Repository
internal class RoleRepositoryAdapter(private val jpa: RoleJpaRepository) : RoleRepository {
    override fun findByCodes(codes: Collection<RoleCode>): List<Role> = jpa.findAllByCodeIn(codes)
}
