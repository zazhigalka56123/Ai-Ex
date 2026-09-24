package ru.itmo.aiex.dialog.application

import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class DialogTransactions(transactionManager: PlatformTransactionManager) {
    private val writeTemplate = TransactionTemplate(transactionManager)
    private val readTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    fun <T> write(block: () -> T): T = execute(writeTemplate, block)

    fun <T> read(block: () -> T): T = execute(readTemplate, block)

    @Suppress("UNCHECKED_CAST")
    private fun <T> execute(template: TransactionTemplate, block: () -> T): T = template.execute { block() } as T
}
