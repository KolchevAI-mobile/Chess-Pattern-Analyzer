# Chess Pattern Analyzer

Мобильное приложение для шахматистов уровня **800–1400 Elo**, которое ищет не отдельные зевки,
а **сквозные паттерны ошибок** — повторяющиеся промахи одного корня в разных партиях и фазах игры.
Цель — замкнуть цикл **Диагноз → Корень проблемы → Тренировка → Проверка результата**.

Исходный замысел описан в [Passport.md](Passport.md).

> **Статус: M0 — каркас.** Все три части собираются и запускаются, ML-ядро уже считает
> реальный разбор партии (Stockfish), но данных ещё не хранит. Android-приложение — пока шаблон.
> Что дальше — в [Roadmap.md](Roadmap.md).

## Структура репозитория

```
ChessPatternAnalyzer/
├── android/     # Android-клиент: Kotlin + Jetpack Compose
├── backend/     # API для клиента: Kotlin + Spring Boot (порт 8080)
├── ml/          # ML/аналитическое ядро: Python + FastAPI + Stockfish (порт 8000)
├── Passport.md  # паспорт проекта (замысел, ЦА, архитектура)
├── Roadmap.md   # дорожная карта по вехам
├── MVP.md       # спецификация ближайшего MVP
└── Commits.md   # карта коммитов: сообщение, файлы, смысл (append-only)
```

**ML-ядро — центр проекта.** Backend и Android — оболочки: они доставляют партии,
показывают результат и хранят состояние. Вся логика «что не так с игроком» живёт в `ml/`.

## Что уже работает

| Часть | Состояние | Проверка |
|---|---|---|
| ML | Разбор PGN: cp-loss по ходам, точность, фазы, ошибки, детекторы паттернов, «надлом» | `GET /health`, `POST /api/v1/analyze`, Swagger `/docs` |
| Backend | Spring Boot 4.1.2, health-эндпоинт, карточка сервиса | `GET /api/v1/health` |
| Android | Шаблон Compose (один экран), сборка под SDK 37 | `./gradlew :app:assembleDebug` |

## Быстрый старт

Нужны: **JDK 21+** (проект проверен на JDK 24), **Python 3.11+**, Android Studio с SDK 37
(только для клиента). Опционально — **Stockfish** (без него ML работает в degraded-режиме).

### ML-ядро

```powershell
cd ml
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"
.\.venv\Scripts\python.exe -m uvicorn chess_analyzer.main:app --port 8000
```

Проверка: <http://localhost:8000/health> и Swagger на <http://localhost:8000/docs>.

Анализ партии из комплекта:

```powershell
.\.venv\Scripts\python.exe scripts\smoke_analyze.py
```

Чтобы включить настоящий движок, скачайте Stockfish в `ml/tools` и укажите путь в `ml/.env`:

```powershell
cd ml
.\scripts\fetch_stockfish.ps1          # скачивает и распаковывает бинарник
Copy-Item .env.example .env            # затем впишите STOCKFISH_PATH в .env
```

### Backend

```powershell
cd backend
.\gradlew.bat bootRun
```

Проверка: <http://localhost:8080/api/v1/health>. Тесты: `.\gradlew.bat test`.
Если `bootRun` не нужен, собранный jar запускается так:

```powershell
java -jar build\libs\chess-pattern-backend-0.1.0.jar
```

### Android

Откройте папку `android/` в Android Studio либо:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

## Работа в IDE

Корень репозитория — контейнер монорепы: Gradle-билда в нём нет, поэтому Android Studio
не может синхронизировать корень («does not contain a Gradle build»). Это нормально —
части открываются по отдельности:

| Что | Чем открывать |
|---|---|
| `android/` | Android Studio: File → Open → папка `android` |
| `backend/` | IntelliJ IDEA: File → Open → папка `backend` |
| `ml/` | PyCharm или VS Code: папка `ml` |

Git при этом продолжает работать: `.git` лежит в корне, IDE находит его выше по дереву.
Из терминала в корне можно собирать так: `.\android\gradlew.bat -p android :app:assembleDebug`.

## Контракт между частями

```
Android ──REST──▶ backend (8080) ──REST──▶ ml (8000)
                      │
                      └──PostgreSQL (появится в M1)
```

На M0 backend и ML друг о друге не знают: ML вызывается напрямую (Swagger/curl),
backend отдаёт только health. Связка и хранение — веха **M1** в [Roadmap.md](Roadmap.md).

Ответ ML на разбор партии выглядит так (сокращённо):

```json
{
  "meta": {"player": "Duke Karl", "games_analyzed": 2, "engine": "stockfish", "depth": 10, "duration_ms": 821},
  "games": [{"game_id": "Paris FRA", "accuracy": 84.8, "blunders": 2, "mistakes": 6,
             "breaking_point": {"ply": 18, "san": "b5", "phase": "opening"}}],
  "patterns": [{"code": "phase_gap", "title": "Слабое место: миттельшпиль",
                "severity": 1.0, "confidence": 0.4,
                "summary": "Средняя потеря в фазе «миттельшпиль» — 243 cp против 56 cp в остальных фазах."}]
}
```

## Тесты

```powershell
cd ml;     .\.venv\Scripts\python.exe -m pytest
cd backend; .\gradlew.bat test
```

## Документы и договорённости

- [Passport.md](Passport.md) — замысел, ЦА, целевая архитектура.
- [Roadmap.md](Roadmap.md) — вехи M0–M5 и критерии готовности.
- [MVP.md](MVP.md) — что именно входит в первый продукт и как его принимать.
- [Commits.md](Commits.md) — карта коммитов: сообщение, файлы и смысл каждого; **записи только дописываются**.
- [ml/README.md](ml/README.md), [backend/README.md](backend/README.md), [android/README.md](android/README.md) — детали по частям.
