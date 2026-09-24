package ru.itmo.aiex.agent.api

import ru.itmo.aiex.common.moderation.FlagReason

data class GuardrailHit(val target: GuardrailTarget, val reason: FlagReason, val details: String)
