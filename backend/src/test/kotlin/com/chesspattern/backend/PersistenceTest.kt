package com.chesspattern.backend

import com.chesspattern.backend.domain.AnalysisEntity
import com.chesspattern.backend.domain.GameEntity
import com.chesspattern.backend.domain.UserEntity
import com.chesspattern.backend.repository.AnalysisRepository
import com.chesspattern.backend.repository.GameRepository
import com.chesspattern.backend.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

/**
 * Проверяет связку «сущности ↔ миграция ↔ Postgres», включая jsonb-payload.
 *
 * Требует поднятого Postgres и переменной DB_TESTS=true.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_TESTS", matches = "true")
class PersistenceTest {

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var games: GameRepository

    @Autowired
    private lateinit var analyses: AnalysisRepository

    @Test
    fun `stores game and analysis with json payload`() {
        val user = users.save(UserEntity(lichessNick = "tester"))
        val game = games.save(
            GameEntity(
                userId = user.id,
                source = "lichess",
                externalId = "abcd1234",
                pgn = "1. e4 e5 1-0",
                sha256 = "hash-of-pgn",
                white = "White",
                black = "Black",
                result = "1-0",
            ),
        )
        val payload = """{"patterns":[{"code":"phase_gap","severity":1.0}]}"""
        val saved = analyses.save(
            AnalysisEntity(
                gameId = game.id,
                engine = "stockfish",
                engineDepth = 10,
                analysisVersion = "1",
                accuracy = 81.0,
                avgCpLoss = 142.3,
                payload = payload,
            ),
        )

        val loaded = analyses.findById(saved.id).orElseThrow()

        assertEquals("stockfish", loaded.engine)
        assertEquals(81.0, loaded.accuracy)
        assertTrue(loaded.payload.contains("phase_gap"), "jsonb должен вернуться без потерь")
        assertEquals(1, analyses.findAllByGameId(game.id).size)
    }

    @Test
    fun `game lookup is idempotent by source and external id`() {
        val user = users.save(UserEntity(lichessNick = "tester-2"))
        games.save(
            GameEntity(
                userId = user.id,
                source = "lichess",
                externalId = "same-id",
                pgn = "1. d4 d5 1/2-1/2",
                sha256 = "hash-2",
                white = "A",
                black = "B",
                result = "1/2-1/2",
            ),
        )

        val found = games.findBySourceAndExternalId("lichess", "same-id")

        assertEquals("hash-2", found?.sha256)
    }
}
