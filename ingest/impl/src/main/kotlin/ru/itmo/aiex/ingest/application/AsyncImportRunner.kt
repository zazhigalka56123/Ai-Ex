package ru.itmo.aiex.ingest.application

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class AsyncImportRunner(private val processor: ImportProcessor) {
    @Async
    fun submit(job: ImportJob) {
        processor.process(job)
    }
}
