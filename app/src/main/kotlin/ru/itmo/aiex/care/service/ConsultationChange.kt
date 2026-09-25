package ru.itmo.aiex.care.service

import ru.itmo.aiex.care.entity.SessionStatus

data class ConsultationChange(
    val status: SessionStatus? = null,
    val summary: String? = null,
    val recommendations: String? = null,
    val rating: Short? = null,
    val cancelReason: String? = null,
)
