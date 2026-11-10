package com.chesspattern.backend.api

import com.chesspattern.backend.api.dto.ServiceInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SystemController(
    @Value("\${app.version:0.1.0}") private val version: String,
) {

    @GetMapping("/")
    fun index(): ServiceInfo = ServiceInfo(
        service = "Chess Pattern Analyzer",
        part = "backend",
        version = version,
        docs = listOf("/api/v1/health", "../Roadmap.md", "../MVP.md"),
    )
}
