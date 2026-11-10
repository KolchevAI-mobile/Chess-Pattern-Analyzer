# Backend (Kotlin + Spring Boot)

API для Android-клиента и оркестратор: авторизация, импорт партий, очереди, агрегация
инсайтов. На M0 здесь только каркас, который гарантированно поднимается.

## Запуск

```powershell
cd backend
.\gradlew.bat bootRun
```

- Порт: `8080` (переопределяется `BACKEND_PORT`).
- `GET /api/v1/health` → `{"status":"ok","service":"backend","version":"0.1.0"}`
- `GET /` → карточка сервиса для разработчика.

Сборка и тесты:

```powershell
.\gradlew.bat build     # компиляция + тесты + bootJar
java -jar build\libs\chess-pattern-backend-0.1.0.jar
```

## Стек

- Kotlin 2.4.21, Spring Boot 4.1.2 (Spring 7), Tomcat 11.
- Java 21+ (`jvmTarget = 21`), Gradle 9.6.
- Пока без БД и без зависимостей от ML-сервиса — так проще проверять каркас.

## Структура

```
src/main/kotlin/com/chesspattern/backend/
├── ChessPatternBackendApplication.kt   # точка входа
└── api/
    ├── HealthController.kt             # /api/v1/health
    ├── SystemController.kt             # карточка сервиса
    └── dto/ServiceInfo.kt              # ответы API
src/main/resources/application.yml      # порт, версия, адрес ML-сервиса
src/test/kotlin/...                     # smoke-тест поднятия контекста
```

## Что появится дальше (см. Roadmap.md, M1–M2)

- `POST /api/v1/import` — импорт партий Lichess по нику, сохранение в PostgreSQL.
- `POST /api/v1/games/{id}/analysis` — постановка задачи в очередь для ML-ядра.
- `GET /api/v1/insights` — агрегированный «диагноз» для клиента.
- Аутентификация (JWT) и мультипользовательский режим.

Адрес ML-сервиса уже вынесен в конфигурацию: `app.ml-service-url` (`ML_SERVICE_URL`,
по умолчанию `http://localhost:8000`).
