package com.chesspattern.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Сквозной паттерн, найденный по набору партий игрока.
 * `evidence` — список доказательств (партия, ход, оценка) в jsonb.
 */
@Entity
@Table(name = "pattern_hits")
class PatternHitEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var code: String = "",

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false)
    var severity: Double = 0.0,

    @Column(nullable = false)
    var confidence: Double = 0.0,

    @Column(nullable = false)
    var summary: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var evidence: String = "[]",

    @Column(name = "computed_at", nullable = false)
    var computedAt: Instant = Instant.now(),
)
