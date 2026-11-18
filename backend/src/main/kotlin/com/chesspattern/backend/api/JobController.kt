package com.chesspattern.backend.api

import com.chesspattern.backend.domain.JobStatus
import com.chesspattern.backend.service.JobService
import com.chesspattern.backend.service.JobStats
import com.chesspattern.backend.service.JobView
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class JobResponse(
    val id: UUID,
    val status: JobStatus,
    val player: String,
    val totalGames: Int,
    val processedGames: Int,
    val error: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val finishedAt: Instant?,
) {
    companion object {
        fun of(view: JobView) = JobResponse(
            id = view.id,
            status = view.status,
            player = view.player,
            totalGames = view.totalGames,
            processedGames = view.processedGames,
            error = view.error,
            createdAt = view.createdAt,
            startedAt = view.startedAt,
            finishedAt = view.finishedAt,
        )
    }
}

@RestController
@RequestMapping("/api/v1")
class JobController(private val jobService: JobService) {

    @GetMapping("/jobs/{id}")
    fun job(@PathVariable id: UUID): ResponseEntity<JobResponse> {
        val view = jobService.view(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(JobResponse.of(view))
    }

    @GetMapping("/jobs")
    fun recent(@RequestParam player: String): List<JobResponse> =
        jobService.recent(player).map(JobResponse::of)

    @GetMapping("/jobs/stats")
    fun stats(): JobStats = jobService.stats()
}
