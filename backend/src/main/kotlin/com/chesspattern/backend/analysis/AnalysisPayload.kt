package com.chesspattern.backend.analysis

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Разбор `payload` из таблицы `analyses` (ответ ML по одной партии).
 *
 * Ответ хранится целиком, поэтому новые поля (MMMP, ΔS в M4) не требуют миграций:
 * здесь читается только то, что нужно витрине сейчас.
 */
class AnalysisPayload(
    val blunders: Int,
    val mistakes: Int,
    val inaccuracies: Int,
    val errors: List<ParsedError>,
    val breakingPoint: ParsedBreakingPoint?,
) {

    data class ParsedError(
        val ply: Int,
        val san: String,
        val cpLoss: Int,
        val phase: String,
        val severity: String,
        val clock: Double?,
    )

    data class ParsedBreakingPoint(
        val ply: Int,
        val san: String,
        val phase: String,
        val scoreBefore: Int,
        val scoreAfter: Int,
    )

    data class ParsedEvidence(
        val gameId: String,
        val ply: Int?,
        val san: String?,
        val detail: String?,
    )

    companion object {
        private val mapper: JsonMapper = JsonMapper.builder().build()

        fun parse(raw: String): AnalysisPayload {
            val root = runCatching { mapper.readTree(raw) }.getOrNull()
            if (root == null || root.isNull) {
                return AnalysisPayload(0, 0, 0, emptyList(), null)
            }

            val errors = mutableListOf<ParsedError>()
            root.path("errors").forEach { node ->
                errors += ParsedError(
                    ply = node.path("ply").asInt(0),
                    san = node.path("san").asString(""),
                    cpLoss = node.path("cp_loss").asInt(0),
                    phase = node.path("phase").asString(""),
                    severity = node.path("severity").asString(""),
                    clock = node.path("clock").takeIf { it.isNumber }?.asDouble(),
                )
            }

            val breaking = root.path("breaking_point").takeIf { it.isObject }?.let { node ->
                ParsedBreakingPoint(
                    ply = node.path("ply").asInt(0),
                    san = node.path("san").asString(""),
                    phase = node.path("phase").asString(""),
                    scoreBefore = node.path("score_before").asInt(0),
                    scoreAfter = node.path("score_after").asInt(0),
                )
            }

            return AnalysisPayload(
                blunders = root.path("blunders").asInt(0),
                mistakes = root.path("mistakes").asInt(0),
                inaccuracies = root.path("inaccuracies").asInt(0),
                errors = errors,
                breakingPoint = breaking,
            )
        }

        fun parseEvidence(raw: String): List<ParsedEvidence> {
            val root: JsonNode = runCatching { mapper.readTree(raw) }.getOrNull() ?: return emptyList()
            if (!root.isArray) return emptyList()

            val evidence = mutableListOf<ParsedEvidence>()
            root.forEach { node ->
                evidence += ParsedEvidence(
                    gameId = node.path("game_id").asString(""),
                    ply = node.path("ply").takeIf { it.isNumber }?.asInt(),
                    san = node.path("san").takeIf { it.isString }?.asString(),
                    detail = node.path("detail").takeIf { it.isString }?.asString(),
                )
            }
            return evidence
        }
    }
}
