package com.chesspattern.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * Точка входа backend-сервиса Chess Pattern Analyzer.
 *
 * M1: импорт партий Lichess, хранение в Postgres, вызов ML-ядра,
 * витрина для Android (список партий, ошибки постранично, инсайты).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
class ChessPatternBackendApplication

fun main(args: Array<String>) {
    runApplication<ChessPatternBackendApplication>(*args)
}
