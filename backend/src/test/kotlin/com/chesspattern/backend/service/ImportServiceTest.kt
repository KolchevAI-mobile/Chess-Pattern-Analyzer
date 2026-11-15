package com.chesspattern.backend.service

import com.chesspattern.backend.lichess.LichessClient
import com.chesspattern.backend.ml.MlAnalysis
import com.chesspattern.backend.ml.MlAnalyzerClient
import com.chesspattern.backend.ml.MlGameResult
import com.chesspattern.backend.ml.MlPattern
import com.chesspattern.backend.repository.AnalysisRepository
import com.chesspattern.backend.repository.GameRepository
import com.chesspattern.backend.repository.PatternHitRepository
import com.chesspattern.backend.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional

/**
 * Сквозной тест импорта: Lichess и ML подменены моками, БД — настоящая.
 * Требует Postgres и `DB_TESTS=true`.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_TESTS", matches = "true")
class ImportServiceTest {

    @Autowired
    private lateinit var importService: ImportService

    @Autowired
    private lateinit var games: GameRepository

    @Autowired
    private lateinit var analyses: AnalysisRepository

    @Autowired
    private lateinit var patterns: PatternHitRepository

    @Autowired
    private lateinit var users: UserRepository

    @MockitoBean
    private lateinit var lichess: LichessClient

    @MockitoBean
    private lateinit var ml: MlAnalyzerClient

    private val pgn = """
        [Event "Game one"]
        [Site "https://lichess.org/aaa111"]
        [White "tester"]
        [Black "opponent1"]
        [Result "1-0"]
        [UTCDate "2026.11.01"]
        [UTCTime "12:00:00"]

        1. e4 e5 2. Nf3 Nc6 1-0

        [Event "Game two"]
        [Site "https://lichess.org/bbb222"]
        [White "opponent2"]
        [Black "tester"]
        [Result "0-1"]

        1. d4 d5 2. c4 e6 0-1
    """.trimIndent()

    private fun mlResult(): MlAnalysis = MlAnalysis(
        engine = "stockfish",
        engineVersion = "1.11.2",
        analysisVersion = "1",
        depth = 10,
        games = listOf(
            MlGameResult(
                gameId = "https://lichess.org/aaa111",
                accuracy = 81.5,
                avgCpLoss = 142.3,
                blunders = 2,
                mistakes = 6,
                inaccuracies = 3,
                payloadJson = """{"accuracy":81.5,"blunders":2,"errors":[{"ply":18,"san":"b5","cp_loss":126,"phase":"opening","severity":"mistake","clock":280.0}]}""",
            ),
            MlGameResult(
                gameId = "https://lichess.org/bbb222",
                accuracy = 77.0,
                avgCpLoss = 180.0,
                blunders = 4,
                mistakes = 5,
                inaccuracies = 1,
                payloadJson = """{"accuracy":77.0,"blunders":4,"errors":[]}""",
            ),
        ),
        patterns = listOf(
            MlPattern(
                code = "phase_gap",
                title = "Слабое место: миттельшпиль",
                severity = 0.9,
                confidence = 0.6,
                summary = "Средняя потеря в миттельшпиле выше",
                evidenceJson = """[{"game_id":"https://lichess.org/aaa111","ply":18,"san":"b5","detail":"126 cp"}]""",
            ),
        ),
    )

    @Test
    fun `imports games, stores analyses and patterns`() {
        Mockito.`when`(lichess.fetchGames("tester", 10, "rapid")).thenReturn(pgn)
        Mockito.`when`(ml.analyze(anyString(), anyString(), anyInt())).thenReturn(mlResult())

        val summary = importService.import("tester", 10, "rapid")

        assertEquals(2, summary.gamesImported)
        assertEquals(2, summary.gamesAnalysed)
        assertEquals(0, summary.cached)
        assertEquals(1, summary.patternsFound)

        // База общая с локальными прогонами, поэтому смотрим только на тестового игрока.
        val user = users.findByLichessNick("tester")!!
        val stored = games.findAllByUserIdOrderByPlayedAtDesc(user.id, PageRequest.of(0, 100))
        assertEquals(2, stored.size)
        val analysed = stored.mapNotNull { analyses.findByGameIdAndAnalysisVersion(it.id, "1") }
        assertEquals(2, analysed.size)
        // Порядок зависит от played_at (у одной партии даты нет), поэтому сравниваем набор.
        assertEquals(setOf(81.5, 77.0), analysed.map { it.accuracy }.toSet())

        val hits = patterns.findAllByUserIdOrderByComputedAtDesc(user.id)
        assertEquals(1, hits.size)
        assertEquals("phase_gap", hits.first().code)
    }

    @Test
    fun `second import uses cache and does not call ml again`() {
        Mockito.`when`(lichess.fetchGames("tester", 10, "rapid")).thenReturn(pgn)
        Mockito.`when`(ml.analyze(anyString(), anyString(), anyInt())).thenReturn(mlResult())

        importService.import("tester", 10, "rapid")
        val second = importService.import("tester", 10, "rapid")

        assertEquals(0, second.gamesAnalysed)
        assertEquals(2, second.cached)
        Mockito.verify(ml, Mockito.times(1)).analyze(anyString(), anyString(), anyInt())
    }

    @Test
    fun `rejects bullet and empty results`() {
        assertThrows(ImportException::class.java) { importService.import("tester", 10, "bullet") }

        Mockito.`when`(lichess.fetchGames("empty", 5, "rapid")).thenReturn("")
        assertThrows(ImportException::class.java) { importService.import("empty", 5, "rapid") }
    }
}
