package ru.itmo.aiex.agent.dto

import ru.itmo.aiex.common.moderation.FlagReason
data class GuardrailHit(val target: GuardrailTarget, val reason: FlagReason, val details: String)
