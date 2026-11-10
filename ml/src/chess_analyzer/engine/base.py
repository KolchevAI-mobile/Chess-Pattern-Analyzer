"""Контракт движка оценки позиции.

Движок — сменная деталь: на MVP это Stockfish через UCI, на следующих
этапах рядом появится Maia (человеческие оценки), а тесты используют
детерминированную заглушку.
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass

import chess

MATE_CP = 10_000
CP_LIMIT = 3_000


@dataclass(frozen=True)
class EvalScore:
    """Оценка в сантипешках с точки зрения белых; `mate` — в ходах до мата."""

    cp: int
    mate: int | None = None
    depth: int = 0


def clamp_cp(cp: int) -> int:
    return max(-CP_LIMIT, min(CP_LIMIT, cp))


class Evaluator(ABC):
    name: str = "unknown"

    @abstractmethod
    def evaluate(self, board: chess.Board) -> EvalScore:
        """Оценка текущей позиции (сторона хода — `board.turn`)."""

    def close(self) -> None:
        """Освобождение ресурсов (для UCI-процесса)."""

    def __enter__(self) -> "Evaluator":
        return self

    def __exit__(self, *exc_info: object) -> None:
        self.close()
