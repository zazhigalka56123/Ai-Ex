package ru.itmo.aiex.agent.dto

import java.time.Instant

data class HistoryMessage(val speaker: Speaker, val text: String, val sentAt: Instant)
