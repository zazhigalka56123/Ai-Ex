package ru.itmo.aiex.common.error

enum class ErrorCode(val httpStatus: Int, val title: String) {
    VALIDATION_FAILED(400, "Ошибка валидации запроса"),

    UNAUTHENTICATED(401, "Текущий пользователь не определён"),
    FORBIDDEN(403, "Недостаточно прав"),

    NOT_FOUND(404, "Ресурс не найден"),
    USER_NOT_FOUND(404, "Пользователь не найден"),
    PERSONA_NOT_FOUND(404, "Персона не найдена"),
    PROFILE_NOT_FOUND(404, "Профиль персоны не найден"),
    IMPORT_NOT_FOUND(404, "Импорт не найден"),
    CONVERSATION_NOT_FOUND(404, "Беседа не найдена"),
    MESSAGE_NOT_FOUND(404, "Сообщение не найдено"),
    SPECIALIST_NOT_FOUND(404, "Специалист не найден"),
    SLOT_NOT_FOUND(404, "Слот не найден"),
    CONSULTATION_NOT_FOUND(404, "Консультация не найдена"),
    FLAG_NOT_FOUND(404, "Флаг модерации не найден"),
    TAG_NOT_FOUND(404, "Тег не найден"),
    SPECIALIZATION_NOT_FOUND(404, "Специализация не найдена"),

    METHOD_NOT_ALLOWED(405, "Метод не поддерживается"),
    NOT_ACCEPTABLE(406, "Формат ответа не поддерживается"),

    EMAIL_TAKEN(409, "Email уже занят"),
    DICTIONARY_CODE_TAKEN(409, "Код справочника уже занят"),
    SPECIALIST_PROFILE_EXISTS(409, "Профиль специалиста уже существует"),
    USER_INVALID_STATE(409, "Недопустимое состояние пользователя"),
    PERSONA_INVALID_STATE(409, "Недопустимый переход статуса персоны"),
    PERSONA_NOT_READY(409, "Персона не готова к диалогу"),
    IMPORT_INVALID_STATE(409, "Недопустимое состояние импорта"),
    CONVERSATION_INVALID_STATE(409, "Недопустимое состояние беседы"),
    CONSULTATION_INVALID_STATE(409, "Недопустимый переход статуса консультации"),
    FLAG_INVALID_STATE(409, "Недопустимый переход статуса флага"),
    FLAG_ALREADY_REPORTED(409, "Жалоба на это сообщение уже подана"),
    SLOT_TAKEN(409, "Слот уже занят"),
    SLOT_OVERLAP(409, "Слот пересекается с существующим"),
    CONSTRAINT_VIOLATED(409, "Нарушено ограничение целостности данных"),
    CONCURRENT_MODIFICATION(409, "Ресурс был изменён параллельно"),

    FILE_TOO_LARGE(413, "Файл больше допустимого размера"),
    UNSUPPORTED_FORMAT(415, "Неподдерживаемый формат выгрузки"),
    UNSUPPORTED_MEDIA_TYPE(415, "Неподдерживаемый Content-Type"),

    INTERNAL_ERROR(500, "Внутренняя ошибка сервера"),
    LLM_UNAVAILABLE(503, "LLM-провайдер недоступен"),
    ;

    val slug: String get() = name.lowercase().replace('_', '-')
}
