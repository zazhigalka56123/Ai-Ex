package ru.itmo.aiex.iam.api

import ru.itmo.aiex.common.security.RoleCode
import java.util.UUID

data class UserView(val id: UUID, val displayName: String, val roles: Set<RoleCode>, val active: Boolean)
