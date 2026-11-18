package com.chesspattern.backend.service

import com.chesspattern.backend.domain.JobStatus
import com.chesspattern.backend.lichess.LichessClient
import com.chesspattern.backend.ml.MlAnalysis
import com.chesspattern.backend.ml.MlAnalyzerClient
import com.chesspattern.backend.ml.MlGameResult
import com.chesspattern.backend.repository.JobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

/**
 * Очередь на Postgres: постановка, захват воркером, прогресс и финальный статус.
 * Lichess и ML — моки, БД настоящая. Требует `DB_TESTS=true`.
 */
@SpringBootTest(properties = ["app.jobs-enabled=false"])
class JobServiceTest {

    @Autowired
    private lateinit var jobService: JobService

    @Autowired
    private lateinit var jobWorker: JobWorker

    @Autowired
    private lateinit var jobs: JobRepository

    @MockitoBean
    private lateinit var lichess: LichessClient

    @MockitoBean
    private lateinit var ml: MlAnalyzerClient

    private val pgn = """
        [Event "Game one"]
        [Site "https://lichess.org/job111"]
        [White "job-player"]
        [Black "opponent"]
        [Result "1-0"]

        1. e4 e5 2. Nf3 Nc6 1-0
    """.trimIndent()

    private fun mlResult(): MlAnalysis = MlAnalysis(
        engine = "stockfish",
        engineVersion = "1.11.2",
        analysisVersion = "1",
        depth = 10,
        games = listOf(
            MlGameResult(
                gameId = "https://lichess.org/job111",
                accuracy = 80.0,
                avgCpLoss = 120.0,
                blunders = 1,
                mistakes = 2,
                inaccuracies = 1,
                payloadJson = """{"accuracy":80.0,"blunders":1,"errors":[]}""",
            ),
        ),
        patterns = emptyList(),
    )

    private fun drainUntilFinished(jobId: UUID) {
        repeat(10) {
            val view = jobService.view(jobId) ?: return
            if (view.status == JobStatus.DONE || view.status == JobStatus.FAILED) return
            jobWorker.processNext()
        }
    }

    @Test
    fun `worker processes queued job and reports progress`() {
        val nick = "job-player-${UUID.randomUUID()}"
        Mockito.`when`(lichess.fetchGames(nick, 10, "rapid")).thenReturn(pgn)
        Mockito.`when`(ml.analyze(anyString(), anyString(), anyInt())).thenReturn(mlResult())

        val job = jobService.enqueue(nick, 10, "rapid")
        assertEquals(JobStatus.QUEUED, jobService.view(job.id)?.status)

        drainUntilFinished(job.id)

        val view = jobService.view(job.id)!!
        assertEquals(JobStatus.DONE, view.status)
        assertEquals(1, view.totalGames)
        assertEquals(1, view.processedGames)
    }

    @Test
    fun `failed import is marked failed with message`() {
        val nick = "job-fail-${UUID.randomUUID()}"
        Mockito.`when`(lichess.fetchGames(nick, 10, "rapid")).thenReturn("")

        val job = jobService.enqueue(nick, 10, "rapid")
        drainUntilFinished(job.id)

        val view = jobService.view(job.id)!!
        assertEquals(JobStatus.FAILED, view.status)
        assertNotNull(view.error)
    }

    @Test
    fun `stats counts jobs by status`() {
        val stats = jobService.stats()

        assertTrue(stats.done + stats.queued + stats.failed + stats.running >= 1)
        assertEquals(jobs.countByStatus(JobStatus.DONE), stats.done)
    }
}
