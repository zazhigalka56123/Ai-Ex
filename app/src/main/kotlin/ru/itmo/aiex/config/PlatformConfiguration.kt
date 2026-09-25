package ru.itmo.aiex.config

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import ru.itmo.aiex.common.events.DomainEventPublisher
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class PlatformConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun domainEventPublisher(publisher: ApplicationEventPublisher): DomainEventPublisher {
        val log = LoggerFactory.getLogger(DomainEventPublisher::class.java)
        return DomainEventPublisher { event ->
            log.debug("Событие {} {}", event::class.simpleName, event.eventId)
            publisher.publishEvent(event)
        }
    }

    @Bean
    fun mdcTaskDecorator(): TaskDecorator = TaskDecorator { task ->
        val context = MDC.getCopyOfContextMap()
        Runnable {
            val previous = MDC.getCopyOfContextMap()
            if (context != null) MDC.setContextMap(context) else MDC.clear()
            try {
                task.run()
            } finally {
                if (previous != null) MDC.setContextMap(previous) else MDC.clear()
            }
        }
    }
}
