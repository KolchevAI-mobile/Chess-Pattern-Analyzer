package com.chesspattern.backend.api.dto

/**
 * Ответ `/api/v1/health` — используется Android-клиентом и smoke-тестами.
 */
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
)

/**
 * Ответ корневого маршрута: краткая карточка сервиса для разработчика,
 * который открыл адрес в браузере.
 */
data class ServiceInfo(
    val service: String,
    val part: String,
    val version: String,
    val docs: List<String>,
)
