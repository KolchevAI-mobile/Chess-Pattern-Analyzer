# Android-клиент

Kotlin + Jetpack Compose. На M1 — один рабочий экран: ник Lichess → импорт и разбор →
«Диагноз» с паттернами и список партий.

## Запуск

Открыть папку `android/` в Android Studio (она самодостаточна: свой Gradle-проект,
`local.properties` с путём к SDK) либо собрать из консоли:

```powershell
cd android
.\gradlew.bat :app:assembleDebug     # APK в app/build/outputs/apk/debug/
.\gradlew.bat :app:installDebug      # на подключённое устройство/эмулятор
```

## Как это работает

1. Вводится ник Lichess → приложение вызывает `POST /api/v1/import` (10 партий, rapid).
2. Затем `GET /api/v1/insights` и `GET /api/v1/games`.
3. Экран показывает топ-паттерн (title, summary, уверенность), вторичные паттерны и партии.

Адрес backend — константа `ApiClient.BASE_URL = "http://10.0.2.2:8080"` (loopback хоста
из эмулятора). Для реального устройства замените на IP машины; приложение разрешает
cleartext-трафик — только для локальной разработки.

HTTP-клиент сознательно на `HttpURLConnection` + `org.json`: для M1 без Retrofit/Hilt,
чтобы не тащить DI и сериализацию до первого экрана. Слой данных (`data/`) от этого
не зависит и меняется на Retrofit без переписывания UI.

## Параметры

- `namespace`/`applicationId`: `com.chesspattern`.
- `compileSdk`/`targetSdk`: 37, `minSdk`: 28.
- Compose BOM 2026.02.01, AGP 9.4.1, Kotlin 2.2.10.

## Что появится в M2

- Экран прогресса импорта (очередь).
- Навигация и DI (Hilt), разбиение одного экрана на экраны списка и деталей.
