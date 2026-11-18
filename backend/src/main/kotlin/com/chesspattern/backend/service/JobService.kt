package com.chesspattern.backend.service

import com.chesspattern.backend.config.AppProperties
import com.chesspattern.backend.domain.JobEntity
import com.chesspattern.backend.domain.JobStatus
import com.chesspattern.backend.repository.JobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class JobView(
    val id: UUID,
    val type: String,
    val status: JobStatus,
    val player: String,
    val totalGames: Int,
    val processedGames: Int,
    val error: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
)

data class JobStats(
    val queued: Long,
    val running: Long,
    val done: Long,
    val failed: Long,
)

/**
 * Очередь заданий на Postgres: `FOR UPDATE SKIP LOCKED` вместо Redis.
 * Для одного сервиса этого достаточно, а вторую систему в M2 не тащим.
 */
@Service
class JobService(
    private val jobs: JobRepository,
    private val props: AppProperties,
) {

    @Transactional
    fun enqueue(player: String, limit: Int?, perfType: String?): JobEntity {
        val nick = player.trim()
        if (nick.isEmpty()) throw ImportException("Ник не может быть пустым")
        val job = JobEntity(
            player = nick,
            limitGames = (limit ?: props.importLimit).coerceIn(1, props.importLimit),
            perfType = (perfType ?: "rapid").lowercase(),
        )
        return jobs.save(job)
    }

    /** Забирает задание и переводит в RUNNING; null — очередь пуста. */
    @Transactional
    fun claim(): JobEntity? {
        val job = jobs.lockNextQueued() ?: return null
        job.status = JobStatus.RUNNING
        job.startedAt = Instant.now()
        return jobs.save(job)
    }

    /** Прогресс пишется отдельной транзакцией — иначе клиент не увидит его до конца импорта. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun progress(jobId: UUID, processed: Int, total: Int) {
        jobs.findById(jobId).ifPresent { job ->
            job.processedGames = processed
            job.totalGames = total
            jobs.save(job)
        }
    }

    @Transactional
    fun finish(jobId: UUID, status: JobStatus, error: String?) {
        jobs.findById(jobId).ifPresent { job ->
            job.status = status
            job.error = error
            job.finishedAt = Instant.now()
            if (status == JobStatus.DONE) {
                job.processedGames = job.totalGames
            }
            jobs.save(job)
        }
    }

    @Transactional(readOnly = true)
    fun view(jobId: UUID): JobView? = jobs.findById(jobId).map(::toView).orElse(null)

    @Transactional(readOnly = true)
    fun stats(): JobStats = JobStats(
        queued = jobs.countByStatus(JobStatus.QUEUED),
        running = jobs.countByStatus(JobStatus.RUNNING),
        done = jobs.countByStatus(JobStatus.DONE),
        failed = jobs.countByStatus(JobStatus.FAILED),
    )

    @Transactional(readOnly = true)
    fun recent(player: String): List<JobView> =
        jobs.findAllByPlayerOrderByCreatedAtDesc(player.trim()).map(::toView)

    private fun toView(job: JobEntity) = JobView(
        id = job.id,
        type = job.type,
        status = job.status,
        player = job.player,
        totalGames = job.totalGames,
        processedGames = job.processedGames,
        error = job.error,
        createdAt = job.createdAt,
        startedAt = job.startedAt,
        finishedAt = job.finishedAt,
    )
}
