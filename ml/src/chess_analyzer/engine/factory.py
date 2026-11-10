"""Выбор движка под текущую конфигурацию."""

from __future__ import annotations

from dataclasses import dataclass

from chess_analyzer.config import Settings
from chess_analyzer.engine.base import Evaluator
from chess_analyzer.engine.heuristic import HeuristicEvaluator
from chess_analyzer.engine.stockfish import StockfishEvaluator


@dataclass(frozen=True)
class EngineInfo:
    name: str
    available: bool
    depth: int
    note: str


def create_evaluator(settings: Settings, depth: int | None = None) -> Evaluator:
    """Stockfish, если он настроен и найден; иначе — эвристика."""
    if settings.stockfish_available:
        assert settings.stockfish_path is not None
        return StockfishEvaluator(
            path=settings.stockfish_path,
            depth=depth or settings.stockfish_depth,
            threads=settings.stockfish_threads,
            hash_mb=settings.stockfish_hash_mb,
        )
    return HeuristicEvaluator()


def describe_engine(settings: Settings) -> EngineInfo:
    if settings.stockfish_available:
        return EngineInfo(
            name="stockfish",
            available=True,
            depth=settings.stockfish_depth,
            note="Полноценный анализ через UCI.",
        )
    return EngineInfo(
        name="heuristic",
        available=True,
        depth=0,
        note="Stockfish не найден: оценка по материалу (degraded-режим). "
        "Задайте STOCKFISH_PATH для полноценного анализа.",
    )
