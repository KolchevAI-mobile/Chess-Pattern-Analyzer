package com.chesspattern.data

/**
 * Ответы backend API. Поля nullable — backend опускает отсутствующие значения.
 */
data class GameSummary(
    val id: String,
    val white: String,
    val black: String,
    val result: String,
    val accuracy: Double?,
    val blunders: Int?,
    val mistakes: Int?,
    val inaccuracies: Int?,
    val analysed: Boolean,
)

data class Evidence(
    val gameId: String,
    val ply: Int?,
    val san: String?,
    val detail: String?,
)

data class Pattern(
    val code: String,
    val title: String,
    val summary: String,
    val severity: Double,
    val confidence: Double,
    val evidence: List<Evidence>,
)

data class Insights(
    val player: String,
    val gamesAnalysed: Int,
    val patterns: List<Pattern>,
    val breakingPoint: BreakingSummary?,
)

data class BreakingSummary(
    val gamesWithBreakingPoint: Int,
    val medianPly: Double?,
    val dominantPhase: String?,
)

data class JobState(
    val id: String,
    val status: String,
    val processed: Int,
    val total: Int,
    val error: String?,
)

class ApiException(message: String) : RuntimeException(message)
