package com.chesspattern.backend.service

import com.chesspattern.backend.config.AppProperties
import com.chesspattern.backend.domain.AnalysisEntity
import com.chesspattern.backend.domain.GameEntity
import com.chesspattern.backend.domain.PatternHitEntity
import com.chesspattern.backend.domain.UserEntity
import com.chesspattern.backend.lichess.ImportedGame
import com.chesspattern.backend.lichess.LichessClient
import com.chesspattern.backend.lichess.PgnParser
import com.chesspattern.backend.ml.MlAnalyzerClient
import com.chesspattern.backend.repository.AnalysisRepository
import com.chesspattern.backend.repository.GameRepository
import com.chesspattern.backend.repository.PatternHitRepository
import com.chesspattern.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

class ImportException(message: String) : RuntimeException(message)

data class ImportSummary(
    val player: String,
    val gamesImported: Int,
    val gamesAnalysed: Int,
    val cached: Int,
    val patternsFound: Int,
    val durationMs: Long,
)

/**
 * Импорт партий Lichess → сохранение → разбор в ML-ядре.
 *
 * Идемпотентность: пара `(source, external_id)` — ключ партии, а `sha256` + версия
 * методики — ключ разбора. Повторный импорт тех же партий не запускает Stockfish.
 */
@Service
class ImportService(
    private val users: UserRepository,
    private val games: GameRepository,
    private val analyses: AnalysisRepository,
    private val patterns: PatternHitRepository,
    private val lichess: LichessClient,
    private val ml: MlAnalyzerClient,
    private val props: AppProperties,
) {

    @Transactional
    fun import(username: String, limit: Int?, perfType: String?): ImportSummary {
        val started = System.currentTimeMillis()
        val nick = username.trim()
        if (nick.isEmpty()) throw ImportException("Ник не может быть пустым")

        val max = (limit ?: props.importLimit).coerceIn(1, props.importLimit)
        val perf = normalizePerf(perfType)

        val pgn = lichess.fetchGames(nick, max, perf)
        val parsed = PgnParser.parse(pgn).take(max)
        if (parsed.isEmpty()) {
            throw ImportException("У игрока «$nick» не нашлось партий в режиме $perf")
        }

        val user = users.findByLichessNick(nick) ?: users.save(UserEntity(lichessNick = nick))
        val stored = parsed.map { upsertGame(user.id, it) }
        val version = props.analysisVersion

        val fresh = stored.filter { analyses.findByGameIdAndAnalysisVersion(it.id, version) == null }

        var analysed = 0
        var cached = 0
        var patternsFound = patterns.findAllByUserIdOrderByComputedAtDesc(user.id).size

        if (fresh.isEmpty()) {
            // Всё уже разобрано этой версией методики — Stockfish не трогаем.
            cached = stored.size
        } else {
            // Отправляем все партии выборки, а не только новые: паттерны считаются
            // по набору целиком, иначе свежая партия исказит картину.
            val allPgn = stored.joinToString("\n\n") { it.pgn }
            val result = ml.analyze(allPgn, nick, stored.size)
            val byExternalId = stored.associateBy { it.externalId }

            for (gameResult in result.games) {
                val game = byExternalId[gameResult.gameId] ?: continue
                val existing = analyses.findByGameIdAndAnalysisVersion(game.id, version)
                if (existing != null) {
                    existing.engine = result.engine
                    existing.engineDepth = result.depth
                    existing.accuracy = gameResult.accuracy
                    existing.avgCpLoss = gameResult.avgCpLoss
                    existing.payload = gameResult.payloadJson
                    analyses.save(existing)
                } else {
                    analyses.save(
                        AnalysisEntity(
                            gameId = game.id,
                            engine = result.engine,
                            engineDepth = result.depth,
                            analysisVersion = version,
                            accuracy = gameResult.accuracy,
                            avgCpLoss = gameResult.avgCpLoss,
                            payload = gameResult.payloadJson,
                        ),
                    )
                }
                analysed++
            }

            patterns.deleteAllByUserId(user.id)
            result.patterns.forEach { pattern ->
                patterns.save(
                    PatternHitEntity(
                        userId = user.id,
                        code = pattern.code,
                        title = pattern.title,
                        severity = pattern.severity,
                        confidence = pattern.confidence,
                        summary = pattern.summary,
                        evidence = pattern.evidenceJson,
                    ),
                )
            }
            patternsFound = result.patterns.size
        }

        return ImportSummary(
            player = nick,
            gamesImported = stored.size,
            gamesAnalysed = analysed,
            cached = cached,
            patternsFound = patternsFound,
            durationMs = System.currentTimeMillis() - started,
        )
    }

    private fun upsertGame(userId: UUID, game: ImportedGame): GameEntity {
        games.findBySourceAndExternalId(SOURCE, game.externalId)?.let { return it }
        return games.save(
            GameEntity(
                userId = userId,
                source = SOURCE,
                externalId = game.externalId,
                pgn = game.pgn,
                sha256 = PgnParser.sha256(game.pgn),
                white = game.white,
                black = game.black,
                result = game.result,
                timeControl = game.timeControl,
                playedAt = game.playedAt,
            ),
        )
    }

    private fun normalizePerf(perfType: String?): String =
        when (val value = (perfType ?: "rapid").lowercase()) {
            "rapid", "blitz" -> value
            "bullet" -> throw ImportException("Bullet исключён: там нет осмысленных часов для анализа")
            else -> throw ImportException("Режим «$value» не поддерживается: только rapid или blitz")
        }

    companion object {
        const val SOURCE = "lichess"
    }
}
