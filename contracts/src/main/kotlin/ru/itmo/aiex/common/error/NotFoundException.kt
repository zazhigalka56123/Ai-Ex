package ru.itmo.aiex.common.error

class NotFoundException(code: ErrorCode, message: String) : AiExException(code, message) {
    companion object {
        fun of(code: ErrorCode, id: Any): NotFoundException = NotFoundException(code, "${code.title}: $id")
    }
}
