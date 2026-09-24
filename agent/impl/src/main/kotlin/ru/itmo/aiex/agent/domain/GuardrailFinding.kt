package ru.itmo.aiex.agent.domain

import ru.itmo.aiex.common.moderation.FlagReason

data class GuardrailFinding(val reason: FlagReason, val rule: String)
