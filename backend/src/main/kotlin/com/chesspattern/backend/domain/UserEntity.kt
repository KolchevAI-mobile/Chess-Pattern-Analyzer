package com.chesspattern.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Игрок. На M1 — анонимный профиль: устройство + ник Lichess.
 */
@Entity
@Table(name = "users")
class UserEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    @Column(name = "device_token_hash")
    var deviceTokenHash: String? = null,

    @Column(name = "lichess_nick")
    var lichessNick: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
