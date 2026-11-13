package com.chesspattern.backend.repository

import com.chesspattern.backend.domain.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun findByLichessNick(lichessNick: String): UserEntity?
}
