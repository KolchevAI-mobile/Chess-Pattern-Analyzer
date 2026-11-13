package com.chesspattern.backend.repository

import com.chesspattern.backend.domain.GameEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GameRepository : JpaRepository<GameEntity, UUID> {
    fun findBySourceAndExternalId(source: String, externalId: String): GameEntity?

    fun findAllByUserIdOrderByPlayedAtDesc(userId: UUID, pageable: Pageable): List<GameEntity>
}
