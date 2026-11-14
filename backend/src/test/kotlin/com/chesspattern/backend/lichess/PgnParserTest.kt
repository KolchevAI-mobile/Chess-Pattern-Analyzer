package com.chesspattern.backend.lichess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class PgnParserTest {

    private val twoGames = """
        [Event "Game one"]
        [Site "https://lichess.org/aaa111"]
        [White "tester"]
        [Black "opponent1"]
        [Result "1-0"]
        [UTCDate "2026.11.01"]
        [UTCTime "12:00:00"]
        [TimeControl "600+0"]

        1. e4 e5 2. Nf3 Nc6 1-0

        [Event "Game two"]
        [Site "https://lichess.org/bbb222"]
        [White "opponent2"]
        [Black "tester"]
        [Result "0-1"]

        1. d4 d5 2. c4 e6 0-1
    """.trimIndent()

    @Test
    fun `splits multi-game pgn`() {
        val games = PgnParser.splitGames(twoGames)

        assertEquals(2, games.size)
        assertTrue(games[0].startsWith("[Event \"Game one\"]"))
    }

    @Test
    fun `parses headers and ids`() {
        val games = PgnParser.parse(twoGames)

        assertEquals(2, games.size)
        assertEquals("https://lichess.org/aaa111", games[0].externalId)
        assertEquals("tester", games[0].white)
        assertEquals("opponent1", games[0].black)
        assertEquals("1-0", games[0].result)
        assertEquals("600+0", games[0].timeControl)
        assertEquals(Instant.parse("2026-11-01T12:00:00Z"), games[0].playedAt)
        assertEquals("https://lichess.org/bbb222", games[1].externalId)
    }

    @Test
    fun `falls back to local id without Site header`() {
        val pgn = """
            [Event "Local"]
            [White "a"]
            [Black "b"]
            [Result "*"]

            1. e4 *
        """.trimIndent()

        val game = PgnParser.parse(pgn).single()

        assertTrue(game.externalId.startsWith("local:"))
        assertNull(game.playedAt)
    }

    @Test
    fun `sha256 is stable and hex-encoded`() {
        val first = PgnParser.sha256("1. e4 e5")
        val second = PgnParser.sha256("1. e4 e5")

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertTrue(first.all { it in "0123456789abcdef" })
    }

    @Test
    fun `ignores empty input`() {
        assertTrue(PgnParser.parse("   ").isEmpty())
        assertNotNull(PgnParser.headers("[Event \"x\"]"))
    }
}
