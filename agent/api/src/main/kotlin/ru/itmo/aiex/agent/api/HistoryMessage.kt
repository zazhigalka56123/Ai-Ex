package ru.itmo.aiex.agent.api

import java.time.Instant

data class HistoryMessage(val speaker: Speaker, val text: String, val sentAt: Instant)
