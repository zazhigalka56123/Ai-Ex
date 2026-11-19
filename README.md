# «Привет, спишь?» — лабораторная работа 2

Система разделена на три бизнес-сервиса. Каждый запускается отдельным процессом, получает настройки из Spring Cloud Config Server и регистрируется в Eureka. Внешние HTTP- и WebSocket-запросы проходят через Spring Cloud Gateway.

| Модуль | Назначение | Хранилище | Порт |
| --- | --- | --- | --- |
| `app` / `core-service` | Персоны, импорт переписок, диалоги, LLM-агент, консультации, модерация | PostgreSQL + JPA | 8083 |
| `iam-service` | Пользователи, роли, проверка текущего пользователя | PostgreSQL + JPA + Reactor | 8081 |
| `notification-service` | Доставка и история уведомлений | PostgreSQL + R2DBC + WebFlux | 8082 |
| `config-server` | Централизованные настройки из `config-repository` | Native configuration repository | 8888 |
| `discovery-server` | Eureka Service Registry | — | 8761 |
| `gateway` | Маршрутизация и Circuit Breaker | — | 8080 |

`shared-kernel` содержит общие типы и контракты, `shared-web` — инфраструктуру MVC. Эти модули не являются запускаемыми сервисами и не содержат бизнес-репозиториев.

## Запуск

Нужны Docker с Compose; для локальной сборки — JDK 21. Gradle Wrapper включён в репозиторий.

```sh
docker compose up -d --build --wait --wait-timeout 600
```

Если Compose установлен отдельной командой, используйте `docker-compose`.

- Интерфейс: http://localhost:8080/
- API и Swagger UI: http://localhost:8080/swagger-ui.html
- В общем Swagger UI выберите `core`, `iam` или `notifications`. Спецификации и запросы «Try it out» доступны через Gateway; внутренние `/internal/**` не публикуются в документации.
- Eureka: http://localhost:8761
- Конфигурация: http://localhost:8888/core-service/default

Compose запускает три независимых PostgreSQL с отдельными пользователями, базами и томами. Данные лабораторной №1 не изменяются: используется новый Compose-проект `ai-ex-lab2`. Доступ к бизнес-сервисам с хоста идёт через Gateway; внутренние `/internal/**` маршруты наружу не публикуются.

```sh
docker compose down
```

Для запуска без контейнеризации JVM:

```sh
docker compose up -d core-db iam-db notification-db
./gradlew bootJar
java -jar config-server/build/libs/service.jar
java -jar discovery-server/build/libs/service.jar
java -jar iam-service/build/libs/service.jar
java -jar notification-service/build/libs/service.jar
java -jar app/build/libs/service.jar
java -jar gateway/build/libs/service.jar
```

Команды `java` запускаются в отдельных терминалах из корня репозитория, сначала Config Server и Eureka. Адрес Config Server задаётся `CONFIG_SERVER_URL`, Eureka — `EUREKA_URL`. Настройки подключения к БД задаются `CORE_DATABASE_*`, `IAM_DATABASE_*`, `NOTIFICATION_DATABASE_*`; значения по умолчанию находятся в `config-repository`.

Конфигурации сервисов хранятся в `config-repository/<spring.application.name>/application.yaml`. Общие настройки Eureka, Feign и Resilience4j находятся в `config-repository/application.yaml`; настройки конкретного сервиса имеют приоритет. Config Server ищет файлы в корне репозитория и в каталоге `{application}`, поэтому конфигурации разных сервисов не смешиваются. `CONFIG_REPOSITORY` задаёт корень репозитория (например, `file:/config-repository`). Локальные конфиги запуска также называются `application.yaml`, тестовый — `application-test.yaml`.

## Взаимодействие

Gateway направляет `/api/v1/users/**` в IAM, `/api/v1/notifications/**` — в сервис уведомлений, остальные API и `/api/v1/conversations/*/ws` — в core. Адреса сервисов определяются через Eureka и Spring Cloud LoadBalancer.

Core проверяет пользователей, получает имена специалистов и метрики через OpenFeign. Сервис уведомлений также проверяет пользователя через Feign; блокирующий вызов выполняется на `boundedElastic`, вне Netty event loop.

IAM возвращает `Flux<UserResponse>` для списка пользователей и `Mono` для одиночных результатов. Заголовки пагинации и `Link` формируются до выдачи элементов списка. Вызовы транзакционных JPA-сервисов выполняются лениво через `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`; DTO формируются на том же рабочем потоке. JPA остаётся блокирующим, его транзакции не переносятся между потоками.

Уведомления сохраняются и читаются через Spring Data R2DBC и Reactor. Контроллер возвращает `Flux<NotificationResponse>` с элементами JSON-массива, заголовки пагинации устанавливает через `ServerHttpResponse`. Реактивный `WebFilter` возвращает `X-Trace-Id`, сохраняет корректный входящий идентификатор или создаёт новый и помещает его в атрибуты запроса и Reactor Context. Core записывает уведомление в transactional outbox вместе с бизнес-изменением. Фоновый обработчик отправляет его через Feign и повторяет отправку после ошибки. Идентификатор сообщения постоянный, `ON CONFLICT DO NOTHING` исключает дубликаты при повторной доставке. Отправитель, как в лабораторной №1, фиксирует доставку в журнале; внешняя почта не подключена.

Resilience4j защищает Feign-вызовы и маршруты Gateway. После пяти вызовов при доле ошибок от 50% circuit breaker открывается на 10 секунд. Ошибки доступности возвращаются как HTTP 503; недоступность уведомлений оставляет записи в очереди. Настройки таймаутов и порогов находятся в Config Server.

## API

Сохраняются основные маршруты лабораторной №1: пользователи, персоны и профили, импорт, диалоги и сообщения, специалисты и консультации, справочники, модерация, метрики и уведомления. Текущий пользователь передаётся заголовком `X-User-Id`.

Демо-пользователи создаются контекстом Liquibase `demo`:

| Роль | UUID |
| --- | --- |
| Администратор | `00000000-0000-0000-0000-00000000a001` |
| Специалист | `00000000-0000-0000-0000-00000000b001` |
| Клиент | `00000000-0000-0000-0000-00000000c001` |

```sh
curl -H 'X-User-Id: 00000000-0000-0000-0000-00000000a001' \
  'http://localhost:8080/api/v1/users?size=5'
```

## Проверки

```sh
./gradlew test bootJar :koverVerify :koverXmlReport :koverHtmlReport
python3 scripts/smoke-lab2.py
python3 scripts/smoke-lab2.py --resilience
```

Интеграционные тесты используют Testcontainers и требуют запущенный Docker. Для Colima при необходимости задайте `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock` и `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.

Тесты проверяют бизнес-правила core, API IAM, выполнение JPA на `boundedElastic`, R2DBC-пагинацию и доступ, идемпотентную доставку и повторные попытки outbox. Сквозная проверка требует запущенной системы и обращается к Gateway, Eureka и Config Server. Режим `--resilience` сначала останавливает IAM и проверяет 503 непосредственно от работающего Notifications и состояние его Circuit Breaker OPEN, затем восстановление 200/CLOSED. После этого отдельно проверяет доставку outbox при остановке и восстановлении Notifications. Подробный сценарий защиты — в [docs/lab2-defense.md](docs/lab2-defense.md).

## Миграции, ошибки и покрытие

Notifications создаёт и обновляет схему через Liquibase по отдельному JDBC-соединению (`NOTIFICATION_JDBC_URL`). Рабочие запросы остаются R2DBC. Первая миграция принимает существующую таблицу из прежнего `schema.sql` без удаления данных; следующие изменения схемы добавляются новыми changeSet.

Ответ списка уведомлений описан DTO `NotificationResponse`. Ошибки Notifications возвращаются как ProblemDetail с `code` и `traceId`; стандартные HTTP-заголовки ошибок сохраняются. Gateway нормализует trace ID и передаёт его в запросах к бизнес-сервисам; fallback также содержит этот ID. Notifications передаёт trace ID в IAM явно из Reactor Context. Core передаёт его в Feign при формировании вызова, до переключения потока Circuit Breaker. Фоновая доставка outbox использует UUID события как trace ID повторных попыток.

Notifications хранит только подтверждённые доставки: `id`, `recipient_id`, `type`, `payload`, `created_at`. Публичный DTO содержит `id`, `type`, `payload`, `createdAt`; статуса, счётчика попыток, второго времени и фильтра по статусу нет. Метрика `notifications.sent` считает записи истории. Ожидание и повторы принадлежат outbox core. Миграция `notification-003-delivery-history` удаляет старые поля, сохраняя записи. Если в старой БД есть `PENDING` или `FAILED`, миграция останавливается: такие записи нужно отдельно разобрать, а не считать доставленными.

Для core маршрут Gateway ждёт HTTP-ответ до 90 секунд, TimeLimiter — 95 секунд. Это оставляет время для повторных обращений к LLM с настроенным таймаутом 20 секунд. Остальные маршруты сохраняют короткие ограничения.

Общий порог покрытия 70% проверяется корневой задачей `:koverVerify` по всем модулям. Отчёты находятся в `build/reports/kover/`. Фактическое покрытие определяется только после выполнения тестов. CI запускает smoke-сценарий с `--resilience`.
