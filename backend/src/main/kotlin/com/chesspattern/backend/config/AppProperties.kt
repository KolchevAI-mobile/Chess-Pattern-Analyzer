package com.chesspattern.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Настройки приложения из секции `app` в application.yml.
 */
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val version: String = "0.1.0",
    val mlServiceUrl: String = "http://localhost:8000",
    /** Версия методики разбора: по ней решается, что пора пересчитать. */
    val analysisVersion: String = "1",
    val importLimit: Int = 20,
    val analysisDepth: Int = 10,
)
