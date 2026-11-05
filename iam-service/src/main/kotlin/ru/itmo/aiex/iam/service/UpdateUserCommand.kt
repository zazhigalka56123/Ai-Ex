package ru.itmo.aiex.iam.service

import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.entity.UserStatus
data class UpdateUserCommand(val displayName: String? = null, val status: UserStatus? = null, val roles: Set<RoleCode>? = null)
