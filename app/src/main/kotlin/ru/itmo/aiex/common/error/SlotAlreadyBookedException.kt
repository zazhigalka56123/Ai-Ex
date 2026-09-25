package ru.itmo.aiex.common.error

class SlotAlreadyBookedException(message: String = "Слот уже забронирован") : ConflictException(ErrorCode.SLOT_TAKEN, message)
