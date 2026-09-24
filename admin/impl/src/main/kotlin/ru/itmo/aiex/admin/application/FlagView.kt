package ru.itmo.aiex.admin.application

import ru.itmo.aiex.admin.domain.ModerationFlag
import ru.itmo.aiex.dialog.api.MessageView

data class FlagView(val flag: ModerationFlag, val message: MessageView? = null, val personaArchived: Boolean? = null)
