package ru.itmo.aiex.ingest.service

import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskRejectedException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.FileTooLargeException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.ingest.entity.ChatImport
import ru.itmo.aiex.ingest.entity.ImportErrorCode
import ru.itmo.aiex.ingest.entity.ImportSource
import ru.itmo.aiex.ingest.service.parsing.FormatDetector
import ru.itmo.aiex.persona.service.PersonaAccess
import ru.itmo.aiex.persona.service.PersonaLifecycle
import java.util.UUID

@Service
class ImportService(
    private val personaAccess: PersonaAccess,
    private val personaLifecycle: PersonaLifecycle,
    private val transactions: ImportTransactions,
    private val processor: ImportProcessor,
    private val asyncRunner: AsyncImportRunner,
    private val properties: ImportProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun upload(actor: Actor, personaId: UUID, file: UploadedFile, source: ImportSource?, theirName: String?): ChatImport {
        actor.requireRole(RoleCode.USER)
        val persona = personaAccess.assertOwned(personaId, actor.userId)
        if (file.sizeBytes <= 0) throw ValidationException("file", "required", "Файл выгрузки пуст")
        if (file.sizeBytes > properties.maxFileBytes) {
            throw FileTooLargeException("Файл ${file.sizeBytes / BYTES_IN_MB} МБ больше лимита ${properties.maxFileMb} МБ")
        }
        val content = file.readBytes()
        if (content.isEmpty()) throw ValidationException("file", "required", "Файл выгрузки пуст")
        val filename = file.originalFilename?.trim()?.takeIf { it.isNotEmpty() }?.takeLast(ChatImport.FILENAME_MAX) ?: DEFAULT_FILENAME
        val resolvedSource =
            if (source != null) {
                source.also { FormatDetector.rejectBinary(FormatDetector.extensionOf(filename), content) }
            } else {
                FormatDetector.detect(filename, content)
            }

        val pending = transactions.createPending(personaId, actor.userId, resolvedSource, filename, content.size.toLong())
        try {
            personaLifecycle.startTraining(personaId, actor.userId, pending.id)
        } catch (ex: Exception) {
            val code = if (ex is AiExException || ex is OptimisticLockingFailureException) ImportErrorCode.PERSONA_BUSY else ImportErrorCode.INTERNAL
            transactions.markFailed(pending.id, code, "Персона сейчас не может принять выгрузку: ${ex.message}")
            log.info("Импорт {} отклонён: персона {} занята ({})", pending.id, personaId, ex.javaClass.simpleName)
            throw ex
        }

        val job = ImportJob(pending.id, personaId, actor.userId, persona.name, resolvedSource, theirName?.trim()?.takeIf { it.isNotEmpty() }, content)
        if (content.size > properties.asyncThresholdBytes) {
            dispatchAsync(job)
            return transactions.find(pending.id)
        }
        return processor.process(job)
    }

    private fun dispatchAsync(job: ImportJob) {
        try {
            asyncRunner.submit(job)
            log.info("Импорт {} ({} КБ) передан на асинхронный разбор", job.importId, job.content.size / BYTES_IN_KB)
        } catch (ex: TaskRejectedException) {
            log.error("Импорт {}: очередь асинхронного разбора переполнена", job.importId, ex)
            transactions.markFailed(job.importId, ImportErrorCode.INTERNAL, "Сервер перегружен, повторите загрузку позже")
            personaLifecycle.trainingFailed(job.personaId, job.importId)
        }
    }

    private companion object {
        const val DEFAULT_FILENAME = "upload"
        const val BYTES_IN_KB = 1024
        const val BYTES_IN_MB = 1024 * 1024
    }
}
