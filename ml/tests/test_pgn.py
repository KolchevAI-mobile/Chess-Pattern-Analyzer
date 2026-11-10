from __future__ import annotations

from chess_analyzer.domain.models import GameResult, Side
from chess_analyzer.ingest.pgn import parse_pgn

PGN = """
[Event "Test game"]
[Site "https://lichess.org/abcd1234"]
[White "Alice"]
[Black "Bob"]
[WhiteElo "1500"]
[BlackElo "?"]
[Result "1-0"]

1. e4 { [%clk 0:05:00] } e5 { [%clk 0:05:00] } 2. Nf3 { [%clk 0:04:50] } Nc6 { [%clk 0:04:40] } 1-0
"""

MULTI = PGN + """
[Event "Second"]
[White "Bob"]
[Black "Alice"]
[Result "1/2-1/2"]

1. d4 { [%clk 0:03:00] } d5 { [%clk 0:03:00] } 1/2-1/2
"""


def test_parses_headers_and_moves() -> None:
    games = parse_pgn(PGN)
    assert len(games) == 1

    game = games[0]
    assert game.white == "Alice"
    assert game.black == "Bob"
    assert game.white_elo == 1500
    assert game.black_elo is None
    assert game.result is GameResult.WHITE_WIN
    assert game.game_id == "https://lichess.org/abcd1234"
    assert len(game.moves) == 4


def test_parses_clocks() -> None:
    game = parse_pgn(PGN)[0]
    assert game.moves[0].clock == 300.0
    assert game.moves[2].clock == 290.0


def test_multi_game_pgn_and_limit() -> None:
    assert len(parse_pgn(MULTI)) == 2
    assert len(parse_pgn(MULTI, limit=1)) == 1


def test_side_of_player() -> None:
    game = parse_pgn(MULTI)[0]
    assert game.side_of("Alice") is Side.WHITE
    assert game.side_of("alice") is Side.WHITE
    assert game.side_of("Bob") is Side.BLACK
    assert game.side_of("white") is Side.WHITE
    assert game.side_of("carol") is None


def test_empty_pgn() -> None:
    assert parse_pgn("   ") == []
