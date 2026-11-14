package com.chesspattern.backend.api

import com.chesspattern.backend.service.ImportService
import com.chesspattern.backend.service.ImportSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Тело запроса импорта. Поля — `var` с умолчаниями: так Jackson собирает объект
 * без Kotlin-модуля (в M1 он не подключён).
 */
class ImportRequest {
    var platform: String? = null
    var username: String? = null
    var limit: Int? = null
    var perfType: String? = null
}

data class ImportResponse(
    val player: String,
    val gamesImported: Int,
    val gamesAnalysed: Int,
    val cached: Int,
    val patternsFound: Int,
    val durationMs: Long,
)

@RestController
@RequestMapping("/api/v1")
class ImportController(private val importService: ImportService) {

    @PostMapping("/import")
    fun import(@RequestBody request: ImportRequest): ResponseEntity<ImportResponse> {
        val platform = (request.platform ?: "lichess").lowercase()
        if (platform != "lichess") {
            return ResponseEntity.badRequest().body(
                ImportResponse(
                    player = request.username.orEmpty(),
                    gamesImported = 0,
                    gamesAnalysed = 0,
                    cached = 0,
                    patternsFound = 0,
                    durationMs = 0,
                ),
            )
        }
        val summary: ImportSummary = importService.import(
            username = request.username.orEmpty(),
            limit = request.limit,
            perfType = request.perfType,
        )
        return ResponseEntity.ok(
            ImportResponse(
                player = summary.player,
                gamesImported = summary.gamesImported,
                gamesAnalysed = summary.gamesAnalysed,
                cached = summary.cached,
                patternsFound = summary.patternsFound,
                durationMs = summary.durationMs,
            ),
        )
    }
}
