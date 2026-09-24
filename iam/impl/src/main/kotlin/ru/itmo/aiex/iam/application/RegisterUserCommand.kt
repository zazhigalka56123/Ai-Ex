package ru.itmo.aiex.iam.application

import ru.itmo.aiex.common.security.RoleCode

data class RegisterUserCommand(val email: String, val displayName: String, val roles: Set<RoleCode>)
