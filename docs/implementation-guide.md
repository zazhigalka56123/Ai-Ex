# Руководство по реализации лабораторных №2–3

Архитектура описана в [architecture.md](architecture.md), запуск и переменные окружения — в [README](../README.md).

## Модули

- `app` запускает core: персоны, импорт, диалоги, LLM, консультации и модерацию.
- `iam-service` использует Spring MVC, Reactor и JPA. Блокирующие операции выполняются через `Mono.fromCallable` на `boundedElastic`.
- `notification-service` использует WebFlux и R2DBC. Синхронный Feign также выполняется на `boundedElastic`.
- `shared-kernel` содержит общие типы и контракты, `shared-web` — инфраструктуру MVC, `shared-security` — проверку и выпуск JWT. В WebFlux `shared-web` подключать нельзя.
- `gateway`, `config-server`, `discovery-server` обеспечивают маршрутизацию, конфигурацию и регистрацию.

Внутри бизнес-модулей используются пакеты `controller`, `dto`, `service`, `repository`, `entity`. DTO отделены от сущностей. Kotlin `val`/`var` уже обеспечивают аксессоры; Lombok не нужен.

## HTTP

MVC-контроллеры возвращают DTO или ResponseEntity, когда нужны статус и заголовки. IAM возвращает Flux<UserResponse> для списка с заголовками пагинации и Link; одиночные результаты — Mono с DTO или ResponseEntity. Список Notifications возвращает Flux<NotificationResponse>, заголовки устанавливает через ServerHttpResponse. Отдельные результаты и агрегаты возвращаются через Mono. Для ошибок используются ProblemDetail, code и traceId.

В MVC применяется TraceIdFilter, в WebFlux — TraceIdWebFilter. Reactor Context не равен MDC: trace ID передаётся в синхронный Feign явно. Во внешнем API пагинация ограничена 50 записями.

## Базы данных

Схемой управляет Liquibase. Для Notifications миграции выполняются через JDBC, рабочие запросы — через R2DBC. Для изменения схемы добавляется новый changeSet; применённые миграции не редактируются. Нельзя удалять тома ради перехода с прежней версии приложения.

Outbox core записывается в одной транзакции с бизнес-изменением. Доставка повторяется с тем же UUID; Notifications исключает дубликаты по первичному ключу.

Notifications хранит только историю подтверждённых доставок: id, recipientId, type, payload и createdAt. Статусов доставки, счётчика попыток и sentAt в модели нет. Метрика notifications.sent — число записей истории. Старые поля удаляет новая миграция; предыдущие changeSet остаются неизменными. При наличии старых недоставленных записей миграция останавливается до их разбора.

## Проверки

Команды выполняются только после согласованного запуска; Testcontainers требует Docker:

```sh
./gradlew build :koverVerify :koverXmlReport :koverHtmlReport
python3 scripts/smoke-lab2.py --resilience
```

Для авторизации smoke нужно экспортировать `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` и `JWT_SECRET`; подробнее — [защита lab3](lab3-defense.md).

Smoke требует уже поднятый Compose-стенд, создаёт демо-данные и в режиме resilience по очереди останавливает и восстанавливает IAM и Notifications. Общий отчёт покрытия расположен в `build/reports/kover/`.
