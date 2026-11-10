package com.chesspattern.backend

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * Smoke-тест: контекст Spring поднимается без внешних зависимостей
 * (БД и очередь на этом этапе не подключены).
 */
@SpringBootTest
class ChessPatternBackendApplicationTest {

    @Test
    fun `context loads`() {
        // Если контекст не собрался, тест упадёт до этой строки.
    }
}
