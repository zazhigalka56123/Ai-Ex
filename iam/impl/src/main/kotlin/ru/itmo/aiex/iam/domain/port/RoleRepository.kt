package ru.itmo.aiex.iam.domain.port

import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.domain.Role

interface RoleRepository {
    fun findByCodes(codes: Collection<RoleCode>): List<Role>
}
