package ru.itmo.aiex.admin.application

import ru.itmo.aiex.admin.domain.FlagStatus

data class ReviewFlagCommand(val status: FlagStatus, val resolution: String? = null, val archivePersona: Boolean = false)
