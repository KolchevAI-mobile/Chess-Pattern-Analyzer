package com.chesspattern.backend.repository

import com.chesspattern.backend.domain.PatternHitEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PatternHitRepository : JpaRepository<PatternHitEntity, UUID> {
    fun findAllByUserIdOrderByComputedAtDesc(userId: UUID): List<PatternHitEntity>
}
