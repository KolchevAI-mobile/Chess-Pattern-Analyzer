package com.chesspattern.backend.service

import com.chesspattern.backend.config.AppProperties
import com.chesspattern.backend.domain.JobStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Воркер очереди. На M2 — один поток: разбор сериализован, потому что Stockfish
 * в ML-ядре тоже один. Пул воркеров имеет смысл только вместе с пулом движков.
 */
@Component
class JobWorker(
    private val jobService: JobService,
    private val importService: ImportService,
    private val props: AppProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.jobs-poll-interval-ms:2000}")
    fun poll() {
        if (!props.jobsEnabled) return
        processNext()
    }

    /** Обрабатывает одну задачу. Возвращает false, если очередь пуста. */
    fun processNext(): Boolean {
        val job = jobService.claim() ?: return false

        log.info("Job {} started: player={} limit={}", job.id, job.player, job.limitGames)
        return try {
            importService.import(job.player, job.limitGames, job.perfType) { processed, total ->
                jobService.progress(job.id, processed, total)
            }
            jobService.finish(job.id, JobStatus.DONE, null)
            log.info("Job {} done", job.id)
            true
        } catch (ex: Exception) {
            log.warn("Job {} failed: {}", job.id, ex.message)
            jobService.finish(job.id, JobStatus.FAILED, ex.message ?: "Неизвестная ошибка")
            true
        }
    }
}
