package com.chesspattern.backend.web

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * HTTP-клиенты наружу (Lichess и ML-ядро).
 *
 * Таймауты обязательны: импорт 20 партий на ML уходит в минуты, а Lichess
 * может ответить 429 — без явных лимитов поток запросов зависнет.
 */
object HttpClients {

    fun create(baseUrl: String, connectTimeoutMs: Int, readTimeoutMs: Int): RestClient {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connectTimeoutMs)
            setReadTimeout(readTimeoutMs)
        }
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build()
    }
}
