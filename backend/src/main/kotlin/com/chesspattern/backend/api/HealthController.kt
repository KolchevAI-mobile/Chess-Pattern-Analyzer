package com.chesspattern.backend.api

import com.chesspattern.backend.api.dto.HealthResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class HealthController(
    @Value("\${app.version:0.1.0}") private val version: String,
) {

    @GetMapping("/health")
    fun health(): HealthResponse = HealthResponse(
        status = "ok",
        service = "backend",
        version = version,
    )
}
