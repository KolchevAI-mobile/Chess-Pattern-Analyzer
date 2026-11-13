package com.chesspattern.backend.repository

import com.chesspattern.backend.domain.AnalysisEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnalysisRepository : JpaRepository<AnalysisEntity, UUID> {
    fun findByGameIdAndAnalysisVersion(gameId: UUID, analysisVersion: String): AnalysisEntity?

    fun findAllByGameId(gameId: UUID): List<AnalysisEntity>
}
