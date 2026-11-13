package com.chesspattern.backend

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.test.context.SpringBootTest

/**
 * Smoke-тест: контекст Spring поднимается вместе с БД и миграциями Flyway.
 *
 * По умолчанию выключен, потому что M1 требует PostgreSQL: запускайте
 * `docker compose -f infra/docker-compose.yml up -d`, затем
 * `DB_TESTS=true ./gradlew test`.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_TESTS", matches = "true")
class ChessPatternBackendApplicationTest {

    @Test
    fun `context loads`() {
        // Если контекст или миграции не собрались, тест упадёт до этой строки.
    }
}
