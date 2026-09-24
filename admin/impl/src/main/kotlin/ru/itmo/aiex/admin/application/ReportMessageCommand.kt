package ru.itmo.aiex.admin.application

import ru.itmo.aiex.common.moderation.FlagReason
import java.util.UUID

data class ReportMessageCommand(val messageId: UUID, val reason: FlagReason, val comment: String? = null)
