package com.chesspattern.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class JobStatus { QUEUED, RUNNING, DONE, FAILED }

/**
 * Задание на импорт и разбор. Воркер забирает его через `FOR UPDATE SKIP LOCKED`,
 * поэтому одно задание не выполнится дважды даже при нескольких экземплярах.
 */
@Entity
@Table(name = "jobs")
class JobEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var type: String = "import",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: JobStatus = JobStatus.QUEUED,

    @Column(nullable = false)
    var player: String = "",

    @Column(name = "perf_type", nullable = false)
    var perfType: String = "rapid",

    @Column(name = "limit_games", nullable = false)
    var limitGames: Int = 20,

    @Column(name = "total_games", nullable = false)
    var totalGames: Int = 0,

    @Column(name = "processed_games", nullable = false)
    var processedGames: Int = 0,

    var error: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,
)
