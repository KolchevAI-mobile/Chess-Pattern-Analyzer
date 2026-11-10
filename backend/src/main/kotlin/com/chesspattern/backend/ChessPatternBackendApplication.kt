package com.chesspattern.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Точка входа backend-сервиса Chess Pattern Analyzer.
 *
 * На этапе MVP сервис отвечает только за "витрину" API и оркестрацию:
 * в дальнейшем сюда добавятся загрузка партий из Lichess, Postgres и очередь
 * заданий для ML-ядра (см. Roadmap.md, веха M2).
 */
@SpringBootApplication
class ChessPatternBackendApplication

fun main(args: Array<String>) {
    runApplication<ChessPatternBackendApplication>(*args)
}
