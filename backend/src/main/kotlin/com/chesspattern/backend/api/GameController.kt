package com.chesspattern.backend.api

import com.chesspattern.backend.service.BreakingPointView
import com.chesspattern.backend.service.BreakingSummary
import com.chesspattern.backend.service.EvidenceView
import com.chesspattern.backend.service.GameError
import com.chesspattern.backend.service.GameSummary
import com.chesspattern.backend.service.InsightsQueryService
import com.chesspattern.backend.service.PatternView
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class GameSummaryResponse(
    val id: UUID,
    val white: String,
    val black: String,
    val result: String,
    val playedAt: Instant?,
    val accuracy: Double?,
    val blunders: Int?,
    val mistakes: Int?,
    val inaccuracies: Int?,
    val analysed: Boolean,
) {
    companion object {
        fun of(summary: GameSummary) = GameSummaryResponse(
            id = summary.id,
            white = summary.white,
            black = summary.black,
            result = summary.result,
            playedAt = summary.playedAt,
            accuracy = summary.accuracy,
            blunders = summary.blunders,
            mistakes = summary.mistakes,
            inaccuracies = summary.inaccuracies,
            analysed = summary.analysed,
        )
    }
}

data class GameDetailResponse(
    val id: UUID,
    val white: String,
    val black: String,
    val result: String,
    val accuracy: Double?,
    val breakingPoint: BreakingPointView?,
    val errors: List<GameError>,
    val errorsTotal: Int,
)

data class EvidenceResponse(
    val gameId: String,
    val ply: Int?,
    val san: String?,
    val detail: String?,
) {
    companion object {
        fun of(view: EvidenceView) = EvidenceResponse(view.gameId, view.ply, view.san, view.detail)
    }
}

data class PatternResponse(
    val code: String,
    val title: String,
    val summary: String,
    val severity: Double,
    val confidence: Double,
    val evidence: List<EvidenceResponse>,
) {
    companion object {
        fun of(view: PatternView) = PatternResponse(
            code = view.code,
            title = view.title,
            summary = view.summary,
            severity = view.severity,
            confidence = view.confidence,
            evidence = view.evidence.map(EvidenceResponse::of),
        )
    }
}

data class InsightsResponse(
    val player: String,
    val gamesAnalysed: Int,
    val generatedAt: Instant,
    val patterns: List<PatternResponse>,
    val breakingPoint: BreakingSummary?,
)

@RestController
@RequestMapping("/api/v1")
class GameController(private val query: InsightsQueryService) {

    @GetMapping("/games")
    fun games(
        @RequestParam player: String,
        @RequestParam(defaultValue = "20") limit: Int,
    ): List<GameSummaryResponse> = query.games(player, limit).map(GameSummaryResponse::of)

    @GetMapping("/games/{id}")
    fun detail(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<GameDetailResponse> {
        val detail = query.gameDetail(id, offset, size) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            GameDetailResponse(
                id = detail.id,
                white = detail.white,
                black = detail.black,
                result = detail.result,
                accuracy = detail.accuracy,
                breakingPoint = detail.breakingPoint,
                errors = detail.errors,
                errorsTotal = detail.errorsTotal,
            ),
        )
    }
}

@RestController
@RequestMapping("/api/v1")
class InsightsController(private val query: InsightsQueryService) {

    @GetMapping("/insights")
    fun insights(@RequestParam player: String): ResponseEntity<InsightsResponse> {
        val snapshot = query.insights(player) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            InsightsResponse(
                player = snapshot.player,
                gamesAnalysed = snapshot.gamesAnalysed,
                generatedAt = snapshot.generatedAt,
                patterns = snapshot.patterns.map(PatternResponse::of),
                breakingPoint = snapshot.breakingPoint,
            ),
        )
    }
}
