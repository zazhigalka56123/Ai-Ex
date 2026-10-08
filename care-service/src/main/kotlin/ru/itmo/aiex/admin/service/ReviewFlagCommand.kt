package ru.itmo.aiex.admin.service

import ru.itmo.aiex.admin.entity.FlagStatus
data class ReviewFlagCommand(val status: FlagStatus, val resolution: String? = null, val archivePersona: Boolean = false)
