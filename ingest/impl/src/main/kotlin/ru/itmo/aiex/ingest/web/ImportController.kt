package ru.itmo.aiex.ingest.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Size
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.CursorPage
import ru.itmo.aiex.common.paging.CursorQuery
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.ingest.application.ImportQueryService
import ru.itmo.aiex.ingest.application.ImportService
import ru.itmo.aiex.ingest.application.UploadedFile
import ru.itmo.aiex.ingest.domain.ImportSource
import ru.itmo.aiex.ingest.web.dto.ImportResponse
import ru.itmo.aiex.ingest.web.dto.ImportedMessageResponse
import ru.itmo.aiex.ingest.web.dto.toResponse
import ru.itmo.aiex.web.ApiPaths
import ru.itmo.aiex.web.CursorParams
import ru.itmo.aiex.web.PageParams
import ru.itmo.aiex.web.Responses
import ru.itmo.aiex.web.openapi.ApiErrors
import java.util.UUID

@RestController
@Validated
@RequestMapping(ApiPaths.V1)
@Tag(name = "Импорт переписки", description = "Загрузка выгрузок Telegram/WhatsApp, статус разбора и нормализованные сообщения")
class ImportController(private val imports: ImportService, private val queries: ImportQueryService) {
    @PostMapping("/personas/{id}/imports", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        operationId = "createImport",
        summary = "Загрузить выгрузку переписки",
        description =
        "`multipart/form-data`, лимит - `AIEX_IMPORT_MAX_FILE_MB` (20 МБ). Формат определяется автоматически " +
            "(Telegram JSON одного чата, WhatsApp TXT, текст «Имя: сообщение») или задаётся `source`. " +
            "Персона переходит в `TRAINING`; небольшие файлы разбираются сразу, большие - асинхронно: статус - в `GET /imports/{id}`. " +
            "Если разбор упал, импорт получает статус `FAILED` с `errorCode`, а персона возвращается в прежнее состояние.",
    )
    @ApiResponse(responseCode = "202", description = "Выгрузка принята", headers = [Header(name = "Location", description = "URI импорта")])
    @ApiErrors(
        ErrorCode.VALIDATION_FAILED,
        ErrorCode.FORBIDDEN,
        ErrorCode.PERSONA_NOT_FOUND,
        ErrorCode.PERSONA_INVALID_STATE,
        ErrorCode.CONCURRENT_MODIFICATION,
        ErrorCode.FILE_TOO_LARGE,
        ErrorCode.UNSUPPORTED_FORMAT,
    )
    fun upload(
        actor: Actor,
        @PathVariable id: UUID,
        @RequestPart("file") file: MultipartFile,
        @Parameter(description = "Формат выгрузки; по умолчанию определяется автоматически")
        @RequestParam(required = false) source: ImportSource?,
        @Parameter(description = "Имя автора, чьи сообщения - персона; по умолчанию - собеседник личного чата или автор с именем персоны")
        @RequestParam(required = false)
        @Size(max = 128)
        theirName: String?,
    ): ResponseEntity<ImportResponse> {
        val uploaded = UploadedFile(file.originalFilename, file.size) { file.inputStream }
        val chatImport = imports.upload(actor, id, uploaded, source, theirName)
        return Responses.accepted(chatImport.toResponse(), "${ApiPaths.V1}/imports/{id}", chatImport.id)
    }

    @GetMapping("/personas/{id}/imports")
    @Operation(
        operationId = "listPersonaImports",
        summary = "Импорты персоны",
        description = "Импорты своей персоны, новые первыми. Offset-пагинация с `X-Total-Count`.",
    )
    @ApiResponse(responseCode = "200", description = "Страница импортов")
    @ApiErrors(ErrorCode.PERSONA_NOT_FOUND)
    fun list(
        actor: Actor,
        @PathVariable id: UUID,
        @PageParams(sortable = ["createdAt"], defaultSort = "createdAt,desc") page: PageQuery,
    ): ResponseEntity<List<ImportResponse>> = Responses.page(queries.listByPersona(actor, id, page).map { it.toResponse() })

    @GetMapping("/imports/{id}")
    @Operation(operationId = "getImport", summary = "Статус импорта", description = "Статус и статистика разбора; только свой импорт.")
    @ApiResponse(responseCode = "200", description = "Импорт")
    @ApiErrors(ErrorCode.IMPORT_NOT_FOUND)
    fun get(actor: Actor, @PathVariable id: UUID): ImportResponse = queries.get(actor, id).toResponse()

    @GetMapping("/imports/{id}/messages")
    @Operation(
        operationId = "listImportMessages",
        summary = "Сообщения импорта",
        description =
        "Нормализованные сообщения выгрузки в исходном порядке. **Курсорная (keyset) пагинация** без общего количества: " +
            "следующая порция - по `nextCursor`, `null` - дальше данных нет. После архивации персоны сообщения удаляются.",
    )
    @ApiResponse(responseCode = "200", description = "Порция сообщений")
    @ApiErrors(ErrorCode.IMPORT_NOT_FOUND)
    fun messages(actor: Actor, @PathVariable id: UUID, @CursorParams(defaultLimit = 30) cursor: CursorQuery): CursorPage<ImportedMessageResponse> =
        queries.messages(actor, id, cursor).map { it.toResponse() }
}
