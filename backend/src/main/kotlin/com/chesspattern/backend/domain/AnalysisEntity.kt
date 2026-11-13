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
 * Разбор одной партии.
 *
 * `payload` — ответ ML-ядра целиком, в jsonb. Поэтому новые признаки из M4
 * (MMMP, ΔS) добавляются без изменения схемы: это данные, а не колонки.
 */
@Entity
@Table(name = "analyses")
class AnalysisEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "game_id", nullable = false)
    var gameId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var engine: String = "",

    @Column(name = "engine_depth", nullable = false)
    var engineDepth: Int = 10,

    @Column(name = "analysis_version", nullable = false)
    var analysisVersion: String = "1",

    var accuracy: Double? = null,

    @Column(name = "avg_cp_loss")
    var avgCpLoss: Double? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var payload: String = "{}",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
