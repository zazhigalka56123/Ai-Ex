package ru.itmo.aiex.dialog.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.CursorPage
import ru.itmo.aiex.common.paging.CursorQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.dialog.service.ConversationService
import ru.itmo.aiex.dialog.service.MessageExchangeService
import ru.itmo.aiex.dialog.dto.MessageExchangeResponse
import ru.itmo.aiex.dialog.dto.MessageResponse
import ru.itmo.aiex.dialog.dto.SendMessageRequest
import ru.itmo.aiex.dialog.dto.toResponse
import ru.itmo.aiex.common.web.ApiPaths
import ru.itmo.aiex.common.web.CursorParams
import ru.itmo.aiex.common.web.Responses
import ru.itmo.aiex.common.web.openapi.ApiErrors
import java.util.UUID

@RestController
@RequestMapping("${ApiPaths.V1}/conversations/{conversationId}/messages")
@Tag(name = "Сообщения", description = "Переписка с персоной и история беседы с бесконечной прокруткой")
class MessageController(private val exchanges: MessageExchangeService, private val conversations: ConversationService) {
    @PostMapping
    @Operation(
        operationId = "sendMessage",
        summary = "Написать персоне и получить ответ",
        description = "TX-2: сообщение пользователя фиксируется короткой транзакцией, ответ генерируется вне транзакции, " +
            "затем фиксируется второй транзакцией. Если LLM недоступен или не уложился в таймаут - `503`, " +
            "а сообщение пользователя остаётся сохранённым. `Location` указывает на ответ персоны.",
    )
    @ApiResponse(
        responseCode = "201",
        description = "Сообщение сохранено, ответ получен",
        headers = [Header(name = "Location", description = "URI ответа персоны")],
    )
    @ApiErrors(
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.CONVERSATION_NOT_FOUND,
        ErrorCode.PERSONA_NOT_FOUND,
        ErrorCode.CONVERSATION_INVALID_STATE,
        ErrorCode.PERSONA_NOT_READY,
        ErrorCode.LLM_UNAVAILABLE,
    )
    fun send(
        actor: Actor,
        @PathVariable conversationId: UUID,
        @Valid @RequestBody request: SendMessageRequest,
    ): ResponseEntity<MessageExchangeResponse> {
        val exchange = exchanges.send(actor, conversationId, request.text)
        return Responses.created(
            exchange.toResponse(),
            "${ApiPaths.V1}/conversations/{conversationId}/messages/{id}",
            conversationId,
            exchange.reply.id,
        )
    }

    @GetMapping
    @Operation(
        operationId = "listConversationMessages",
        summary = "История беседы (бесконечная прокрутка)",
        description = "Курсорная (keyset) пагинация без общего количества: новые сверху, `nextCursor = null` - дальше данных нет. " +
            "Что видно, зависит от текущего пользователя: владельцу - всё; специалисту, которому беседу расшарили в активной " +
            "консультации, - всё; администратору - только сообщения с флагами модерации; специалисту без расшаривания - `403`; " +
            "остальным - `404`.",
    )
    @ApiResponse(responseCode = "200", description = "Порция сообщений")
    @ApiErrors(ErrorCode.VALIDATION_FAILED, ErrorCode.FORBIDDEN, ErrorCode.CONVERSATION_NOT_FOUND)
    fun list(actor: Actor, @PathVariable conversationId: UUID, @CursorParams(defaultLimit = 30) cursor: CursorQuery): CursorPage<MessageResponse> =
        conversations.listMessages(actor, conversationId, cursor).map { it.toResponse() }

    @GetMapping("/{id}")
    @Operation(
        operationId = "getConversationMessage",
        summary = "Сообщение беседы",
        description = "Те же правила видимости, что у истории: администратор получит только флагнутое сообщение.",
    )
    @ApiResponse(responseCode = "200", description = "Сообщение")
    @ApiErrors(ErrorCode.FORBIDDEN, ErrorCode.CONVERSATION_NOT_FOUND, ErrorCode.MESSAGE_NOT_FOUND)
    fun get(actor: Actor, @PathVariable conversationId: UUID, @PathVariable id: UUID): MessageResponse =
        conversations.getMessage(actor, conversationId, id).toResponse()
}
