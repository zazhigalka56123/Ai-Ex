package ru.itmo.aiex.iam.application

import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.iam.domain.UserStatus

data class UpdateUserCommand(val displayName: String? = null, val status: UserStatus? = null, val roles: Set<RoleCode>? = null)
