# Android-клиент

Kotlin + Jetpack Compose. На M0 — сгенерированный шаблон с одним экраном: он нужен, чтобы
сборочный конвейер и структура проекта были на месте до появления реальных экранов.

## Запуск

Открыть папку `android/` в Android Studio (она самодостаточна: свой Gradle-проект,
`local.properties` с путём к SDK) либо собрать из консоли:

```powershell
cd android
.\gradlew.bat :app:assembleDebug     # APK в app/build/outputs/apk/debug/
.\gradlew.bat :app:installDebug      # на подключённое устройство/эмулятор
```

## Параметры

- `namespace`/`applicationId`: `com.chesspattern`.
- `compileSdk`/`targetSdk`: 37, `minSdk`: 28.
- Compose BOM 2026.02.01, AGP 9.4.1, Kotlin 2.2.10.

## Что появится в M1

- Экран ввода ника Lichess и запуска импорта.
- Список партий с картой ошибок (из `/api/v1/games`).
- Экран «Диагноз»: топ-паттерн, доказательства, распределение ошибок по фазам.
- Сетевой слой (Retrofit/Ktor-клиент) и базовая навигация.
