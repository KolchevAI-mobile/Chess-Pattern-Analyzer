package com.chesspattern.backend.service

import com.chesspattern.backend.analysis.AnalysisPayload
import com.chesspattern.backend.domain.AnalysisEntity
import com.chesspattern.backend.domain.GameEntity
import com.chesspattern.backend.domain.PatternHitEntity
import com.chesspattern.backend.repository.AnalysisRepository
import com.chesspattern.backend.repository.GameRepository
import com.chesspattern.backend.repository.PatternHitRepository
import com.chesspattern.backend.repository.UserRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

data class GameSummary(
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
)

data class GameError(
    val ply: Int,
    val san: String,
    val cpLoss: Int,
    val phase: String,
    val severity: String,
    val clock: Double?,
)

data class GameDetail(
    val id: UUID,
    val white: String,
    val black: String,
    val result: String,
    val accuracy: Double?,
    val breakingPoint: BreakingPointView?,
    val errors: List<GameError>,
    val errorsTotal: Int,
)

data class BreakingPointView(
    val ply: Int,
    val san: String,
    val phase: String,
    val scoreBefore: Int,
    val scoreAfter: Int,
)

data class PatternView(
    val code: String,
    val title: String,
    val summary: String,
    val severity: Double,
    val confidence: Double,
    val evidence: List<EvidenceView>,
)

data class EvidenceView(
    val gameId: String,
    val ply: Int?,
    val san: String?,
    val detail: String?,
)

data class BreakingSummary(
    val games: Int,
    val gamesWithBreakingPoint: Int,
    val medianPly: Double?,
    val dominantPhase: String?,
)

data class InsightSnapshot(
    val player: String,
    val gamesAnalysed: Int,
    val generatedAt: Instant,
    val patterns: List<PatternView>,
    val breakingPoint: BreakingSummary?,
)

/**
 * Чтение витрины: списки партий, детали с постраничными ошибками, инсайты.
 * Тяжёлый `payload` разбирается только когда он действительно нужен.
 */
@Service
class InsightsQueryService(
    private val users: UserRepository,
    private val games: GameRepository,
    private val analyses: AnalysisRepository,
    private val patterns: PatternHitRepository,
) {

    fun games(player: String, limit: Int): List<GameSummary> {
        val user = users.findByLichessNick(player.trim()) ?: return emptyList()
        return games.findAllByUserIdOrderByPlayedAtDesc(user.id, PageRequest.of(0, limit.coerceIn(1, 100)))
            .map { game -> summary(game, analyses.findAllByGameId(game.id).firstOrNull()) }
    }

    fun gameDetail(id: UUID, offset: Int, size: Int): GameDetail? {
        val game = games.findById(id).orElse(null) ?: return null
        val analysis = analyses.findAllByGameId(id).firstOrNull()
        val payload = analysis?.let { AnalysisPayload.parse(it.payload) }
        val allErrors = payload?.errors?.map {
            GameError(
                ply = it.ply,
                san = it.san,
                cpLoss = it.cpLoss,
                phase = it.phase,
                severity = it.severity,
                clock = it.clock,
            )
        } ?: emptyList()

        return GameDetail(
            id = game.id,
            white = game.white,
            black = game.black,
            result = game.result,
            accuracy = analysis?.accuracy,
            breakingPoint = payload?.breakingPoint?.let {
                BreakingPointView(
                    ply = it.ply,
                    san = it.san,
                    phase = it.phase,
                    scoreBefore = it.scoreBefore,
                    scoreAfter = it.scoreAfter,
                )
            },
            errors = allErrors.drop(offset.coerceAtLeast(0)).take(size.coerceIn(1, 100)),
            errorsTotal = allErrors.size,
        )
    }

    fun insights(player: String): InsightSnapshot? {
        val user = users.findByLichessNick(player.trim()) ?: return null
        val userGames = games.findAllByUserIdOrderByPlayedAtDesc(user.id, PageRequest.of(0, 100))
        val userAnalyses = userGames.mapNotNull { analyses.findAllByGameId(it.id).firstOrNull() }

        val patternViews = patterns.findAllByUserIdOrderByComputedAtDesc(user.id).map(::toPatternView)

        val breaking = userAnalyses.mapNotNull { AnalysisPayload.parse(it.payload).breakingPoint }
        val summary = if (breaking.isEmpty()) {
            null
        } else {
            val phases = breaking.map { it.phase }
            BreakingSummary(
                games = userAnalyses.size,
                gamesWithBreakingPoint = breaking.size,
                medianPly = median(breaking.map { it.ply.toDouble() }),
                dominantPhase = phases.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
            )
        }

        return InsightSnapshot(
            player = user.lichessNick ?: player,
            gamesAnalysed = userAnalyses.size,
            generatedAt = Instant.now(),
            patterns = patternViews,
            breakingPoint = summary,
        )
    }

    private fun summary(game: GameEntity, analysis: AnalysisEntity?): GameSummary {
        val payload = analysis?.let { AnalysisPayload.parse(it.payload) }
        return GameSummary(
            id = game.id,
            white = game.white,
            black = game.black,
            result = game.result,
            playedAt = game.playedAt,
            accuracy = analysis?.accuracy,
            blunders = payload?.blunders,
            mistakes = payload?.mistakes,
            inaccuracies = payload?.inaccuracies,
            analysed = analysis != null,
        )
    }

    private fun toPatternView(entity: PatternHitEntity): PatternView =
        PatternView(
            code = entity.code,
            title = entity.title,
            summary = entity.summary,
            severity = entity.severity,
            confidence = entity.confidence,
            evidence = AnalysisPayload.parseEvidence(entity.evidence).map {
                EvidenceView(
                    gameId = it.gameId,
                    ply = it.ply,
                    san = it.san,
                    detail = it.detail,
                )
            },
        )

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }
}
