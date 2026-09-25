package ru.itmo.aiex.iam.repository

import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.entity.Role
interface RoleRepository {
    fun findByCodes(codes: Collection<RoleCode>): List<Role>
}
