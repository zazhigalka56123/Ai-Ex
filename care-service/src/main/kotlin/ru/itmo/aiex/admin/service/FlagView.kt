package ru.itmo.aiex.admin.service

import ru.itmo.aiex.admin.entity.ModerationFlag
import ru.itmo.aiex.dialog.dto.MessageView
data class FlagView(val flag: ModerationFlag, val message: MessageView? = null, val personaArchived: Boolean? = null)
