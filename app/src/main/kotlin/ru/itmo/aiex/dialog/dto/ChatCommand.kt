package ru.itmo.aiex.dialog.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatCommand(val type: String? = null, val text: String? = null, val requestId: String? = null) {
    companion object {
        const val SEND = "send"
        const val MAX_TEXT_LENGTH = 2000
        const val MAX_REQUEST_ID_LENGTH = 64
    }
}
