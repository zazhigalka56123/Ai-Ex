package ru.itmo.aiex.iam.repository

import org.springframework.stereotype.Repository
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.entity.Role

@Repository
internal class RoleRepositoryAdapter(private val jpa: RoleJpaRepository) : RoleRepository {
    override fun findByCodes(codes: Collection<RoleCode>): List<Role> = jpa.findAllByCodeIn(codes)
}
