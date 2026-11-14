package com.chesspattern.backend.ml

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Клиент ML-ядра. Один вызов на весь набор партий: паттерны считаются
 * по выборке целиком, а не по одной партии.
 */
@Component
class MlAnalyzerClient(private val props: com.chesspattern.backend.config.AppProperties) {

    private val client = com.chesspattern.backend.web.HttpClients.create(
        baseUrl = props.mlServiceUrl,
        connectTimeoutMs = 10_000,
        // Разбор 20 партий на слабом CPU — минуты, поэтому запас большой.
        readTimeoutMs = 300_000,
    )

    private val mapper: JsonMapper = JsonMapper.builder().build()

    fun analyze(pgn: String, player: String, maxGames: Int): MlAnalysis {
        val body = mapOf(
            "pgn" to pgn,
            "player" to player,
            "depth" to props.analysisDepth,
            "max_games" to maxGames,
        )
        val raw = client.post()
            .uri("/api/v1/analyze")
            .body(body)
            .retrieve()
            .body(String::class.java)
            ?: error("ML-ядро вернуло пустой ответ")

        return parse(raw)
    }

    private fun parse(raw: String): MlAnalysis {
        val root: JsonNode = mapper.readTree(raw)
        val meta = root.path("meta")

        val games = mutableListOf<MlGameResult>()
        root.path("games").forEach { game ->
            games += MlGameResult(
                gameId = game.path("game_id").asString(),
                accuracy = game.path("accuracy").asDouble(0.0),
                avgCpLoss = game.path("avg_cp_loss").asDouble(0.0),
                blunders = game.path("blunders").asInt(0),
                mistakes = game.path("mistakes").asInt(0),
                inaccuracies = game.path("inaccuracies").asInt(0),
                payloadJson = game.toString(),
            )
        }

        val patterns = mutableListOf<MlPattern>()
        root.path("patterns").forEach { pattern ->
            patterns += MlPattern(
                code = pattern.path("code").asString(),
                title = pattern.path("title").asString(),
                severity = pattern.path("severity").asDouble(0.0),
                confidence = pattern.path("confidence").asDouble(0.0),
                summary = pattern.path("summary").asString(),
                evidenceJson = pattern.path("evidence").toString(),
            )
        }

        return MlAnalysis(
            engine = meta.path("engine").asString("unknown"),
            engineVersion = meta.path("engine_version").asString(""),
            analysisVersion = meta.path("analysis_version").asString(props.analysisVersion),
            depth = meta.path("depth").asInt(0),
            games = games,
            patterns = patterns,
        )
    }
}

data class MlAnalysis(
    val engine: String,
    val engineVersion: String,
    val analysisVersion: String,
    val depth: Int,
    val games: List<MlGameResult>,
    val patterns: List<MlPattern>,
)

data class MlGameResult(
    val gameId: String,
    val accuracy: Double,
    val avgCpLoss: Double,
    val blunders: Int,
    val mistakes: Int,
    val inaccuracies: Int,
    /** Ответ ML по партии целиком — его и храним в `analyses.payload`. */
    val payloadJson: String,
)

data class MlPattern(
    val code: String,
    val title: String,
    val severity: Double,
    val confidence: Double,
    val summary: String,
    val evidenceJson: String,
)
