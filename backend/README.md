# Backend (Kotlin + Spring Boot)

API для Android-клиента и оркестратор: импорт партий, хранение разборов, инсайты.
На M1 — Postgres, миграции Flyway и сущности под партии/разборы/паттерны.

## Запуск

Нужен PostgreSQL — он поднимается compose-файлом из `infra/`:

```powershell
docker compose -f infra\docker-compose.yml up -d
```

Затем:

```powershell
cd backend
.\gradlew.bat bootRun
```

- Порт: `8080` (переопределяется `BACKEND_PORT`).
- `GET /api/v1/health` → `{"status":"ok","service":"backend","version":"0.1.0"}`
- `GET /` → карточка сервиса для разработчика.

Подключение к БД — переменные окружения (значения по умолчанию совпадают compose-файлом):

| Переменная | По умолчанию |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/chess` |
| `DB_USER` | `chess` |
| `DB_PASSWORD` | `chess` |

Схему создаёт Flyway (`src/main/resources/db/migration`), Hibernate работает в режиме
`ddl-auto: validate` — расхождение сущностей и миграций падает на старте, а не в проде.

Сборка и тесты:

```powershell
.\gradlew.bat build              # компиляция + тесты (контекст без БД пропускается)
$env:DB_TESTS="true"; .\gradlew.bat test    # полный прогон с Postgres
java -jar build\libs\chess-pattern-backend-0.1.0.jar
```

Smoke-тест контекста помечен `@EnabledIfEnvironmentVariable(DB_TESTS, true)`: без базы он
пропускается, чтобы `gradlew build` не требовал запущенного Docker.

## Стек

- Kotlin 2.4.21, Spring Boot 4.1.2 (Spring 7), Tomcat 11.
- Java 21+ (`jvmTarget = 21`), Gradle 9.6.
- JPA (Hibernate 7), Flyway, PostgreSQL 16.

## Структура

```
src/main/kotlin/com/chesspattern/backend/
├── ChessPatternBackendApplication.kt   # точка входа
├── api/
│   ├── HealthController.kt             # /api/v1/health
│   ├── SystemController.kt             # карточка сервиса
│   └── dto/ServiceInfo.kt              # ответы API
├── domain/                             # сущности: users, games, analyses, pattern_hits
└── repository/                         # Spring Data JPA
src/main/resources/application.yml      # порт, datasource, flyway, адрес ML
src/main/resources/db/migration/        # V1__init.sql
src/test/kotlin/...                     # smoke-тест контекста (с БД)
```

`analyses.payload` — `jsonb` с ответом ML-ядра целиком: новые признаки (MMMP, ΔS в M4)
добавляются в JSON без миграций БД.

## Эндпоинты M1

```http
POST /api/v1/import
{ "platform": "lichess", "username": "nick", "limit": 10, "perfType": "rapid" }
→ { "player": "nick", "gamesImported": 10, "gamesAnalysed": 10,
    "cached": 0, "patternsFound": 2, "durationMs": 13027 }

GET /api/v1/games?player=nick&limit=20
→ [ { "id": "...", "white": "...", "black": "...", "result": "1-0",
      "accuracy": 95.1, "blunders": 0, "mistakes": 2, "analysed": true } ]

GET /api/v1/games/{id}?offset=0&size=20
→ { "errors": [...], "errorsTotal": 8, "breakingPoint": {...} }

GET /api/v1/insights?player=nick
→ { "player": "nick", "gamesAnalysed": 10,
    "patterns": [ { "code": "phase_gap", "title": "...", "severity": 0.56,
                    "confidence": 1.0, "evidence": [...] } ],
    "breakingPoint": { "gamesWithBreakingPoint": 1, "medianPly": 35.0 } }
```

Кэш: если все партии выборки уже разобраны текущей `analysis_version`, ML не вызывается —
повторный импорт отвечает за миллисекунды (`cached` = число партий).

## Что появится дальше (см. Roadmap.md, M2)

- Очередь задач и прогресс импорта (50 партий за раз).
- Lichess OAuth: вход и приватные партии.
- Аутентификация (JWT) и мультипользовательский режим.
