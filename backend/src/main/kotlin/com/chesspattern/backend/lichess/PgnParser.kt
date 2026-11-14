package com.chesspattern.backend.lichess

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Одна партия, извлечённая из PGN-экспорта Lichess.
 *
 * `externalId` — URL партии из заголовка `Site`: по нему импорт идемпотентен,
 * а ML-ядро отдаёт тот же идентификатор в `game_id`.
 */
data class ImportedGame(
    val externalId: String,
    val pgn: String,
    val white: String,
    val black: String,
    val result: String,
    val timeControl: String?,
    val playedAt: Instant?,
)

object PgnParser {

    private val headerRegex = Regex("""^\[(\w+)\s+"(.*)"]$""")

    fun parse(pgn: String): List<ImportedGame> = splitGames(pgn).mapNotNull(::toGame)

    /** Делит мультипартийный PGN на отдельные партии. */
    fun splitGames(pgn: String): List<String> {
        val normalized = pgn.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        return normalized
            .split(Regex("(?m)^(?=\\[Event )"))
            .map { it.trim() }
            .filter { it.startsWith("[") }
    }

    private fun toGame(gamePgn: String): ImportedGame? {
        val headers = headers(gamePgn)
        val white = headers["White"] ?: return null
        val black = headers["Black"] ?: return null
        val result = headers["Result"] ?: "*"

        return ImportedGame(
            externalId = headers["Site"]?.takeIf { it.isNotBlank() } ?: "local:${sha256(gamePgn)}",
            pgn = gamePgn,
            white = white,
            black = black,
            result = result,
            timeControl = headers["TimeControl"],
            playedAt = parsePlayedAt(headers),
        )
    }

    fun headers(gamePgn: String): Map<String, String> = buildMap {
        for (line in gamePgn.lineSequence()) {
            if (line.isBlank()) break
            val match = headerRegex.matchEntire(line.trim()) ?: continue
            put(match.groupValues[1], match.groupValues[2])
        }
    }

    private fun parsePlayedAt(headers: Map<String, String>): Instant? {
        val date = headers["UTCDate"] ?: headers["Date"] ?: return null
        return try {
            val parsedDate = LocalDate.parse(date.replace('.', '-'))
            val time = (headers["UTCTime"] ?: "00:00:00").take(8)
            val parsedTime = LocalTime.parse(time)
            parsedDate.atTime(parsedTime).toInstant(ZoneOffset.UTC)
        } catch (_: Exception) {
            null
        }
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
