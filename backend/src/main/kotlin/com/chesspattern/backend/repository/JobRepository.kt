package com.chesspattern.backend.repository

import com.chesspattern.backend.domain.JobEntity
import com.chesspattern.backend.domain.JobStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface JobRepository : JpaRepository<JobEntity, UUID> {

    /**
     * Атомарно забирает старейшее задание из очереди.
     * `SKIP LOCKED` не даёт двум воркерам взять одно и то же задание.
     */
    @Query(
        value = "select * from jobs where status = 'QUEUED' order by created_at for update skip locked limit 1",
        nativeQuery = true,
    )
    fun lockNextQueued(): JobEntity?

    fun countByStatus(status: JobStatus): Long

    fun findAllByPlayerOrderByCreatedAtDesc(player: String): List<JobEntity>
}
