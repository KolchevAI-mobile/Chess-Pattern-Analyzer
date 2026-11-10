"""Разбор PGN в доменные объекты.

Источник партий на MVP — Lichess/Chess.com экспорт; в PGN сохраняются часы
(`%clk` в комментариях), без них невозможен паттерн «цейтнот».
"""

from __future__ import annotations

import hashlib
import io

import chess.pgn

from chess_analyzer.domain.models import GameResult, ParsedGame, ParsedMove


def _clean(value: str | None) -> str:
    value = (value or "").strip()
    return "" if value in {"", "?", "-"} else value


def _elo(value: str | None) -> int | None:
    cleaned = _clean(value)
    return int(cleaned) if cleaned.isdigit() else None


def _game_id(headers: dict[str, str], index: int) -> str:
    site = _clean(headers.get("Site"))
    if site:
        return site
    raw = "|".join(
        [
            _clean(headers.get("White")),
            _clean(headers.get("Black")),
            _clean(headers.get("Date")),
            _clean(headers.get("UTCDate")),
            str(index),
        ]
    )
    return "local:" + hashlib.sha1(raw.encode("utf-8")).hexdigest()[:12]


def _to_parsed(game: chess.pgn.Game, index: int) -> ParsedGame:
    headers = {key: value for key, value in game.headers.items()}
    moves: list[ParsedMove] = []
    for node in game.mainline():
        if node.move is None:
            continue
        moves.append(
            ParsedMove(
                ply=node.ply(),
                san=node.san(),
                clock=node.clock(),
            )
        )

    try:
        result = GameResult(headers.get("Result", "*"))
    except ValueError:
        result = GameResult.UNKNOWN

    return ParsedGame(
        game_id=_game_id(headers, index),
        white=_clean(headers.get("White")) or "White",
        black=_clean(headers.get("Black")) or "Black",
        white_elo=_elo(headers.get("WhiteElo")),
        black_elo=_elo(headers.get("BlackElo")),
        result=result,
        initial_fen=_clean(headers.get("FEN")) or None,
        moves=tuple(moves),
    )


def parse_pgn(text: str, limit: int = 20) -> list[ParsedGame]:
    """Читает до `limit` партий из PGN-текста (мультипартийный PGN поддержан)."""
    if not text or not text.strip():
        return []

    games: list[ParsedGame] = []
    stream = io.StringIO(text)
    while len(games) < limit:
        game = chess.pgn.read_game(stream)
        if game is None:
            break
        parsed = _to_parsed(game, len(games))
        if parsed.moves:
            games.append(parsed)
    return games
