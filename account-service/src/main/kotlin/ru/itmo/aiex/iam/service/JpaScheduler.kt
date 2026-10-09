package ru.itmo.aiex.iam.service

import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler
import reactor.core.scheduler.Schedulers

/**
 * JPA блокирует поток, поэтому вызовы репозиториев уходят с event loop Netty на отдельный пул.
 * Потоков ровно столько, сколько соединений в Hikari: лишние потоки всё равно ждали бы соединение.
 */
@Component
@Profile("microservice")
class JpaScheduler(@Value("\${spring.datasource.hikari.maximum-pool-size:10}") poolSize: Int) : DisposableBean {
    val scheduler: Scheduler = Schedulers.newBoundedElastic(poolSize, Schedulers.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, THREAD_PREFIX)

    /** Выполняет блокирующий вызов после подписки; транзакция `@Transactional` целиком живёт внутри этого потока. */
    fun <T : Any> call(block: () -> T?): Mono<T> = Mono.fromCallable(block).subscribeOn(scheduler)

    override fun destroy() = scheduler.dispose()

    companion object {
        const val THREAD_PREFIX = "jpa"
    }
}
