package ru.itmo.aiex.dialog.dto

import com.fasterxml.jackson.annotation.JsonInclude
import ru.itmo.aiex.common.error.ErrorCode
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ChatEvent(
    val type: String,
    val message: MessageResponse? = null,
    val requestId: String? = null,
    val active: Boolean? = null,
    val code: String? = null,
    val detail: String? = null,
) {
    companion object {
        fun message(message: MessageResponse, requestId: String? = null) = ChatEvent("message", message = message, requestId = requestId)

        fun typing(active: Boolean) = ChatEvent("typing", active = active)

        fun error(code: ErrorCode, detail: String, requestId: String?) = ChatEvent("error", requestId = requestId, code = code.name, detail = detail)
    }
}
