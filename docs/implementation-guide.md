# Руководство по реализации модулей

Документ дополняет [architecture.md](architecture.md): там — что и зачем, здесь — как именно это написано в коде.
Эталонный модуль, с которого копируется всё остальное, — **`iam:impl`** (+ его миграции в `app/src/main/resources/db/changelog/010-iam`).

## 1. Окружение

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)      # JDK 21; Gradle 8.14 на JDK 26 не запускается
# colima вместо Docker Desktop:
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock

./gradlew ktlintFormat               # автоформат (стиль intellij_idea = Kotlin coding conventions)
./gradlew :persona:impl:check        # ktlint + detekt + тесты + порог покрытия модуля 60%
./gradlew check                      # всё + границы модулей + общее покрытие 70%
```

## 2. Версии и подводные камни Spring Boot 4

| Что | Как в этом проекте |
| --- | --- |
| Spring Boot | 4.0.8, Kotlin 2.2.21, JDK 21, Gradle 8.14.3 |
| Jackson | **3.x**: пакеты `tools.jackson.*` (`tools.jackson.databind.json.JsonMapper`, `JsonNode.asString()`), аннотации остались `com.fasterxml.jackson.annotation.*` |
| Testcontainers | **2.x**: `org.testcontainers.postgresql.PostgreSQLContainer` (без дженерика) |
| MockMvc в тестах | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (уже в `AbstractIntegrationTest`) |
| MockK-бины | `com.ninja-squad.springmockk.MockkBean` |
| JPA-сущности | kotlin-allopen делает их `open`, поэтому `private set` запрещён компилятором — используем `protected set` |
| Hibernate | 7.x, `ddl-auto: validate` — схему создаёт только Liquibase |

## 3. Раскладка модуля

```
<module>/impl/src/main/kotlin/ru/itmo/aiex/<module>/
├── domain/            сущности, enum'ы, правила и автоматы состояний; domain/port — интерфейсы репозиториев
├── application/       сервисы сценариев (@Service, @Transactional), реализации контрактов `api`, обработчики событий, *Properties
├── infrastructure/    Spring Data-интерфейсы + адаптеры портов (@Repository), интеграции
└── web/               контроллеры; web/dto — Request/Response и маппинг Entity → Response
```

**Один файл — один тип, имя файла совпадает с именем типа.** Файл без типов (набор top-level функций) называется по смыслу: `Weights.kt`, `StablePageable.kt`, `PageParamParsing.kt`. Функция-маппер лежит в файле того типа, который она возвращает. Классы в `infrastructure` объявляются `internal`.

Правила, которые проверяются автоматически (ArchUnit в `app`, задача `checkModuleBoundaries` в Gradle):

- `domain` зависит только от `java/kotlin`, `jakarta.persistence`, `jakarta.validation`, `org.hibernate.annotations/type`, `common`, своих и чужих `*.api`. Никакого Spring.
- `application` не зависит от `web` и `infrastructure` — только от портов домена.
- `@Entity` — только в `domain`, `@RestController` — только в `web`, Spring Data-репозитории — только в `infrastructure`.
- Чужой модуль доступен только через его модуль `api`. Поля типа `@Entity` в классах `web` запрещены.

## 4. Сущности и миграции

- **id** — `UUID`, назначается приложением: `Ids.next()` (UUIDv7). Справочники (`roles`, `tags`, `specializations`) — `bigint identity`.
- **Корни агрегатов** получают `@Version var version: Long? = null` (`protected set`) — это и оптимистичная блокировка, и признак «новая сущность» для Spring Data (иначе `save` сделает лишний `SELECT`).
- **Время** — только через инжектируемый `java.time.Clock` и `clock.nowMicros()` (точность PostgreSQL; без усечения ломаются курсоры).
- **enum** — `@Enumerated(EnumType.STRING)` + `varchar(24)` + `CHECK (... IN (...))` в миграции. `ordinal` не используется нигде.
- **Типы колонок ↔ Kotlin**: `numeric` ↔ `BigDecimal`, `smallint` ↔ `Short`, `text`/`varchar` ↔ `String`, `timestamptz` ↔ `Instant`, `jsonb` ↔ `String` + `@JdbcTypeCode(SqlTypes.JSON)`.
- **Валидация в сущности** — `@field:NotBlank`, `@field:Size(...)` и т.д., повторяя DTO. Третий уровень — `NOT NULL`/`CHECK`/`UNIQUE` в миграции.
- `equals`/`hashCode` — по `id`. Таблицы — в схеме модуля: `@Table(name = "personas", schema = "persona")`.
- **FK — только внутри модуля.** Ссылки на чужие сущности — голый `uuid` без FK (`owner_id`, `persona_id` в `ingest` и т.п.).
- **Миграции** — только в своей папке `app/src/main/resources/db/changelog/NNN-<module>/`, файл на changeset, `id: NNN-XXX-что-делает`, `author: <модуль>`, у каждого — явный `rollback`. `CHECK` и частичные индексы — через `sql`.
- Сиды справочников/демо — отдельными файлами в `090-seed/` (демо — `context: demo`).

## 5. Репозитории

Порт в `domain/port` объявляет только нужные методы на доменных типах (`PageQuery`/`PageView` из `common`, не Spring `Pageable`).
В `infrastructure` — Spring Data-интерфейс и адаптер `@Repository`, реализующий порт. Перевод пагинации — `ru.itmo.aiex.persistence.toPageable()` / `toPageView()` из `web-common`.
Массовая вставка (тысячи строк) — через `EntityManager.persist` в цикле с `flush()/clear()` пачками: `saveAll` для сущностей с заданным id сделает `merge` с `SELECT` на каждую строку.

## 6. Сервисы и транзакции

- `@Service @Transactional(readOnly = true)` на классе, `@Transactional` на пишущих методах.
- Проверки прав — в сервисе по `Actor` (`actor.requireRole(RoleCode.ADMIN)`, проверка владения). **Чужой объект — `404`, не `403`** (исключение — специалист без доступа к беседе: `403` по §8).
- Ошибки — только исключения из `ru.itmo.aiex.common.error` с кодом из `ErrorCode`. Новые коды без согласования не добавлять.
- `spring.jpa.open-in-view=false`: всё, что нужно ответу, должно быть загружено внутри транзакции (fetch join / `@EntityGraph` / маппинг в сервисе). `LazyInitializationException` — баг, интеграционные тесты его ловят.
- **Сетевой вызов (LLM) — никогда внутри транзакции** (§9.3): короткая транзакция до, вызов вне, короткая транзакция после.
- Транзакция не пересекает границу модуля: вызов чужого `api` — это отдельная транзакция чужого модуля.

## 7. Контракты `api` и события

- **Один класс — один контракт** (`PersonaAccessAdapter : PersonaAccess`), `@Component` в `application`. Тогда в тестах другого модуля любой контракт подменяется `@MockkBean`, не задевая остальные.
- Метрики для администратора — бин `MetricsContributor` (ключи `<модуль>.<метрика>`).
- Публикация событий — только через `DomainEventPublisher` из `common`. Обработчики — в `application`:

```kotlin
@TransactionalEventListener(fallbackExecution = true)   // после коммита публикующей транзакции
@Transactional(propagation = Propagation.REQUIRES_NEW)  // своя транзакция своего модуля
fun on(event: PersonaArchived) { ... }                  // идемпотентно: событие может прийти повторно (лаб. 4)
```

## 8. Веб-слой

```kotlin
@GetMapping
@Operation(operationId = "listPersonas", summary = "Мои персоны", description = "...")   // operationId стабильный, camelCase
@ApiResponse(responseCode = "200", description = "Страница персон")                      // успешный код — явно
@ApiErrors(ErrorCode.FORBIDDEN)                                                          // все возможные коды ошибок
fun list(
    actor: Actor,                                                                         // X-User-Id → Actor (Actor? — необязательный)
    @PageParams(sortable = ["createdAt", "name"], defaultSort = "createdAt,desc") page: PageQuery,
): ResponseEntity<List<PersonaResponse>> = Responses.page(service.list(actor, page).map { it.toResponse() })
```

- Offset-пагинация: параметр `PageQuery` + `Responses.page(...)` (массив в теле, `X-Total-Count`/`X-Total-Pages`/`Link` в хедерах).
- Курсорная: параметр `@CursorParams(defaultLimit = 30) cursor: CursorQuery`, ответ `CursorPage<T>` (`items` + `nextCursor`), курсор — `CursorCodec`, выборка `LIMIT limit + 1` + `CursorPage.fromOverfetch`. `COUNT(*)` не выполняется.
- `201` — `Responses.created(body, "/api/v1/personas/{id}", id)`, `202` — `Responses.accepted(...)`, `204` — `ResponseEntity.noContent()`.
- `401` (если есть `Actor`), `400` (если есть пагинация) и `500` в OpenAPI добавляются автоматически — в `@ApiErrors` их писать не нужно.
- Request DTO — `data class` с `@field:`-аннотациями валидации, `@Valid @RequestBody`. Response DTO не содержат сущностей.

## 9. Тесты

- Модульные — JUnit 5 + MockK + AssertJ, в `src/test` модуля, класс `*Test`.
- Интеграционные — наследник `ru.itmo.aiex.testing.AbstractIntegrationTest`, класс `*IT`. Поднимается всё приложение на PostgreSQL 17 (Testcontainers), таблицы очищаются перед каждым тестом (справочники — нет).
  Хелперы: `mockMvc` (Kotlin DSL), `createUser(RoleCode...)`, `createAdmin()`, `json(obj)`, `MvcResult.json()`, `USER_HEADER`.
- Контракты других модулей, которые мешают сценарию, подменяются `@MockkBean`.
- Порог: 60% строк на модуль (`koverVerify` модуля), 70% на проект.

## 10. Git

Ветки `feature/<модуль>/<кратко>`, коммиты — conventional commits со scope = модуль:
`feat(persona): add profile versioning with single active invariant`.
