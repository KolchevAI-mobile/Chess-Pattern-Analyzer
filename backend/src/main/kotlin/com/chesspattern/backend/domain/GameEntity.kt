package com.chesspattern.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Партия, импортированная из внешнего источника (M1 — Lichess).
 * `sha256` от PGN — ключ кэша разбора: одна и та же партия не считается дважды.
 */
@Entity
@Table(name = "games")
class GameEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var source: String = "",

    @Column(name = "external_id", nullable = false)
    var externalId: String = "",

    @Column(nullable = false)
    var pgn: String = "",

    @Column(nullable = false)
    var sha256: String = "",

    @Column(name = "white", nullable = false)
    var white: String = "",

    @Column(name = "black", nullable = false)
    var black: String = "",

    @Column(nullable = false)
    var result: String = "",

    @Column(name = "time_control")
    var timeControl: String? = null,

    @Column(name = "played_at")
    var playedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
