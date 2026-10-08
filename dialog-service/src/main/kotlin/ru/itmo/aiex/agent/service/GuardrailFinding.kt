package ru.itmo.aiex.agent.service

import ru.itmo.aiex.common.moderation.FlagReason
data class GuardrailFinding(val reason: FlagReason, val rule: String)
