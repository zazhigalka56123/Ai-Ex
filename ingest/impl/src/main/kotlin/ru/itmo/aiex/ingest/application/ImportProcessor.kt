package ru.itmo.aiex.ingest.application

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import ru.itmo.aiex.common.events.ChatImportFailed
import ru.itmo.aiex.common.events.ChatImportParsed
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.time.nowMicros
import ru.itmo.aiex.ingest.domain.ChatImport
import ru.itmo.aiex.ingest.domain.ImportErrorCode
import ru.itmo.aiex.ingest.domain.ImportSource
import ru.itmo.aiex.ingest.domain.MessageAuthor
import ru.itmo.aiex.ingest.domain.MessageRow
import ru.itmo.aiex.ingest.domain.ParseResult
import ru.itmo.aiex.ingest.domain.corpus.AuthorResolver
import ru.itmo.aiex.ingest.domain.corpus.CorpusAnalyzer
import ru.itmo.aiex.ingest.domain.corpus.CorpusInput
import ru.itmo.aiex.ingest.domain.corpus.CorpusMessage
import ru.itmo.aiex.ingest.domain.parsing.ChatExportParser
import ru.itmo.aiex.ingest.domain.parsing.ChatParseException
import ru.itmo.aiex.persona.api.CorpusSnapshot
import ru.itmo.aiex.persona.api.PersonaLifecycle
import java.time.Clock
import java.util.concurrent.TimeUnit

@Component
class ImportProcessor(
    parsers: List<ChatExportParser>,
    private val transactions: ImportTransactions,
    private val personaLifecycle: PersonaLifecycle,
    private val properties: ImportProperties,
    private val events: DomainEventPublisher,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val parsers: Map<ImportSource, ChatExportParser> = parsers.associateBy { it.source }
    private val analyzer = CorpusAnalyzer()

    fun process(job: ImportJob): ChatImport {
        val startedAt = System.nanoTime()
        val prepared =
            try {
                transactions.markParsing(job.importId)
                prepare(job)
            } catch (ex: ChatParseException) {
                return fail(job, ex.code, ex.message ?: ex.code.name, startedAt)
            } catch (ex: Exception) {
                log.error("Импорт {}: непредвиденная ошибка разбора", job.importId, ex)
                return fail(job, ImportErrorCode.INTERNAL, "Внутренняя ошибка разбора выгрузки", startedAt)
            }
        val parsed =
            try {
                transactions.commitParsed(job.importId, prepared.rows, prepared.result)
            } catch (ex: Exception) {
                log.error("Импорт {}: не удалось сохранить сообщения", job.importId, ex)
                return fail(job, ImportErrorCode.INTERNAL, "Не удалось сохранить сообщения выгрузки", startedAt)
            }
        events.publish(ChatImportParsed(job.importId, job.personaId, job.ownerId, parsed.messageCount, clock.nowMicros()))
        val result = rebuildProfile(job, prepared.snapshot) ?: parsed
        logResult(result, startedAt)
        return result
    }

    private fun prepare(job: ImportJob): PreparedImport {
        val parser = parsers[job.source] ?: throw ChatParseException(ImportErrorCode.MALFORMED_FILE, "Формат ${job.source} не поддерживается")
        val chat = parser.parse(job.content)
        if (chat.messages.isEmpty()) throw ChatParseException(ImportErrorCode.EMPTY_CORPUS, "В выгрузке нет ни одного текстового сообщения")
        if (chat.messages.size > properties.maxMessages) {
            throw ChatParseException(
                ImportErrorCode.TOO_MANY_MESSAGES,
                "В выгрузке ${chat.messages.size} сообщений, максимум - ${properties.maxMessages}",
            )
        }
        val theirName = AuthorResolver.resolve(chat, job.theirName, job.personaName)
        val messages = AuthorResolver.normalize(chat, theirName)
        val theirCount = messages.count { it.author == MessageAuthor.THEM }
        if (theirCount == 0) throw ChatParseException(ImportErrorCode.EMPTY_CORPUS, "У «$theirName» нет ни одного текстового сообщения")
        val snapshot = analyzer.analyze(CorpusInput(job.importId, job.source, theirName, messages, chat.attachments))
        return PreparedImport(rows(messages), ParseResult(messages.size, theirCount, chat.skipped, theirName), snapshot)
    }

    private fun rows(messages: List<CorpusMessage>) = messages.mapIndexed { index, message ->
        MessageRow(message.author, message.body, message.sentAt, index)
    }

    private fun rebuildProfile(job: ImportJob, snapshot: CorpusSnapshot): ChatImport? = try {
        personaLifecycle.rebuildFrom(job.personaId, snapshot)
        null
    } catch (ex: Exception) {
        log.warn("Импорт {}: сообщения сохранены, но профиль персоны {} не собран: {}", job.importId, job.personaId, ex.message)
        transactions.markRebuildFailed(job.importId, "Профиль не собран: ${ex.message}. Повторите POST /personas/{id}/profile:rebuild")
    }

    private fun fail(job: ImportJob, code: ImportErrorCode, message: String, startedAt: Long): ChatImport {
        val failed = transactions.markFailed(job.importId, code, message)
        try {
            personaLifecycle.trainingFailed(job.personaId, job.importId)
        } catch (ex: Exception) {
            log.error("Импорт {}: не удалось вернуть персону {} из TRAINING", job.importId, job.personaId, ex)
        }
        events.publish(ChatImportFailed(job.importId, job.personaId, job.ownerId, code.name, clock.nowMicros()))
        logResult(failed, startedAt)
        return failed
    }

    private fun logResult(chatImport: ChatImport, startedAt: Long) {
        log.info(
            "Импорт {}: {}{}, сообщений {}, их {}, пропущено {}, за {} мс",
            chatImport.id,
            chatImport.status,
            chatImport.errorCode?.let { " ($it)" } ?: "",
            chatImport.messageCount,
            chatImport.theirMessageCount,
            chatImport.skippedCount,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
        )
    }

    private class PreparedImport(val rows: List<MessageRow>, val result: ParseResult, val snapshot: CorpusSnapshot)
}
