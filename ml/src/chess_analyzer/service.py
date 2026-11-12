"""Оркестрация: разбор PGN → оценка партий → поиск паттернов."""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass

import chess.engine

from chess_analyzer.analysis.game import analyze_game
from chess_analyzer.config import Settings
from chess_analyzer.domain.models import GameAnalysis, PatternHit
from chess_analyzer.engine.factory import create_evaluator, describe_engine
from chess_analyzer.errors import AnalysisError, EmptyPgnError, PlayerNotFoundError
from chess_analyzer.ingest.pgn import parse_pgn
from chess_analyzer.patterns.detectors import detect_patterns


@dataclass(frozen=True)
class AnalysisResult:
    games: tuple[GameAnalysis, ...]
    patterns: tuple[PatternHit, ...]
    skipped: tuple[str, ...]
    engine: str
    depth: int
    duration_ms: int


class AnalysisService:
    """Фасад над пайплайном.

    Движок UCI не потокобезопасен, поэтому анализ сериализуется блокировкой:
    на MVP это один процесс, очереди и воркеры появятся в M2.
    """

    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self._lock = threading.Lock()

    def analyze(
        self,
        pgn: str,
        player: str,
        depth: int | None = None,
        max_games: int | None = None,
    ) -> AnalysisResult:
        started = time.perf_counter()

        limit = max_games or self.settings.max_games_per_request
        limit = max(1, min(limit, self.settings.max_games_per_request))
        games = parse_pgn(pgn, limit=limit)
        if not games:
            raise EmptyPgnError("PGN пуст или не содержит разбираемых партий")

        evaluator = create_evaluator(self.settings, self.effective_depth(depth))
        analyses: list[GameAnalysis] = []
        skipped: list[str] = []
        try:
            with self._lock:
                for game in games:
                    side = game.side_of(player)
                    if side is None:
                        skipped.append(game.game_id)
                        continue
                    try:
                        analyses.append(analyze_game(game, side, evaluator, self.settings))
                    except ValueError:
                        skipped.append(game.game_id)
        except chess.engine.EngineError as exc:  # движок упал/не запустился
            raise AnalysisError(f"Движок недоступен: {exc}") from exc
        finally:
            evaluator.close()

        if not analyses:
            raise PlayerNotFoundError(
                f"Игрок «{player}» не найден ни в одной из разобранных партий "
                f"({len(games)} шт.)"
            )

        engine_info = describe_engine(self.settings)
        return AnalysisResult(
            games=tuple(analyses),
            patterns=tuple(detect_patterns(tuple(analyses), self.settings)),
            skipped=tuple(skipped),
            engine=engine_info.name,
            # Эвристика глубиной не считается — честно отдаём 0.
            depth=self.effective_depth(depth) if engine_info.name == "stockfish" else 0,
            duration_ms=int((time.perf_counter() - started) * 1000),
        )

    def effective_depth(self, requested: int | None) -> int:
        """Глубина из запроса, зажатая в [1, max_depth]."""
        depth = requested or self.settings.stockfish_depth
        return max(1, min(depth, self.settings.max_depth))
