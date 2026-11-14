package com.chesspattern.backend.lichess

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

/**
 * Клиент публичного API Lichess.
 *
 * Важные детали (см. Roadmap.md, M1):
 * - `perfType` — rapid/blitz; bullet исключён сознательно;
 * - `clocks=true` — без него Lichess не отдаст `%clk` и не заработает детектор цейтнота.
 */
@Component
class LichessClient(
    private val props: com.chesspattern.backend.config.AppProperties,
) {

    private val client: RestClient = com.chesspattern.backend.web.HttpClients.create(
        baseUrl = "https://lichess.org",
        connectTimeoutMs = 10_000,
        readTimeoutMs = 60_000,
    )

    fun fetchGames(nickname: String, limit: Int, perfType: String): String {
        var attempt = 0
        var backoffMs = 700L
        while (true) {
            try {
                return client.get()
                    .uri { builder ->
                        builder.path("/api/games/user/{nick}")
                            .queryParam("max", limit)
                            .queryParam("perfType", perfType)
                            .queryParam("clocks", "true")
                            .queryParam("opening", "false")
                            .build(nickname)
                    }
                    .header(HttpHeaders.ACCEPT, "application/x-chess-pgn")
                    .header(HttpHeaders.USER_AGENT, "chess-pattern-analyzer/${props.version}")
                    .retrieve()
                    .body(String::class.java)
                    ?: ""
            } catch (ex: HttpClientErrorException) {
                if (ex.statusCode == HttpStatus.TOO_MANY_REQUESTS && attempt < 3) {
                    Thread.sleep(backoffMs)
                    backoffMs *= 2
                    attempt++
                    continue
                }
                throw ex
            } catch (ex: HttpServerErrorException) {
                if (attempt < 2) {
                    Thread.sleep(backoffMs)
                    backoffMs *= 2
                    attempt++
                    continue
                }
                throw ex
            }
        }
    }
}
