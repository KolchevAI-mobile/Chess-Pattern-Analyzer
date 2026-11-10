"""Доменные модели ядра: партия, оценка, ошибка, паттерн.

Здесь нет ни FastAPI, ни Stockfish — только предметная область, чтобы
аналитику и детекторы можно было тестировать без сервера и движка.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum


class Side(str, Enum):
    WHITE = "white"
    BLACK = "black"

    @property
    def title_ru(self) -> str:
        return "белые" if self is Side.WHITE else "чёрные"

    @property
    def opponent(self) -> "Side":
        return Side.BLACK if self is Side.WHITE else Side.WHITE


class Phase(str, Enum):
    OPENING = "opening"
    MIDDLEGAME = "middlegame"
    ENDGAME = "endgame"

    @property
    def title_ru(self) -> str:
        return {
            Phase.OPENING: "дебют",
            Phase.MIDDLEGAME: "миттельшпиль",
            Phase.ENDGAME: "эндшпиль",
        }[self]


class Severity(str, Enum):
    INACCURACY = "inaccuracy"
    MISTAKE = "mistake"
    BLUNDER = "blunder"


class GameResult(str, Enum):
    WHITE_WIN = "1-0"
    BLACK_WIN = "0-1"
    DRAW = "1/2-1/2"
    UNKNOWN = "*"

    def is_win_for(self, side: Side) -> bool:
        return (self is GameResult.WHITE_WIN and side is Side.WHITE) or (
            self is GameResult.BLACK_WIN and side is Side.BLACK
        )


@dataclass(frozen=True)
class ParsedMove:
    """Ход из PGN. `clock` — остаток времени на часах перед ходом (если есть)."""

    ply: int
    san: str
    clock: float | None


@dataclass(frozen=True)
class ParsedGame:
    game_id: str
    white: str
    black: str
    white_elo: int | None
    black_elo: int | None
    result: GameResult
    initial_fen: str | None
    moves: tuple[ParsedMove, ...]

    def side_of(self, player: str) -> Side | None:
        """Определяет сторону игрока по нику (регистронезависимо, с синонимами)."""
        key = player.strip().lower()
        if key in {"white", "белые"}:
            return Side.WHITE
        if key in {"black", "чёрные", "черные"}:
            return Side.BLACK
        if key == self.white.strip().lower():
            return Side.WHITE
        if key == self.black.strip().lower():
            return Side.BLACK
        return None

    def name_of(self, side: Side) -> str:
        return self.white if side is Side.WHITE else self.black


@dataclass(frozen=True)
class PlyEval:
    """Оценка позиции до и после конкретного полухода (cp, с точки зрения белых)."""

    ply: int
    san: str
    side: Side
    phase: Phase
    score_before: int
    score_after: int
    cp_loss: int
    clock: float | None


@dataclass(frozen=True)
class ErrorEvent:
    ply: int
    san: str
    side: Side
    phase: Phase
    cp_loss: int
    severity: Severity
    score_before: int
    score_after: int
    clock: float | None


@dataclass(frozen=True)
class BreakingPoint:
    """Гипотеза «надлома»: полуход, после которого качество игры резко падает."""

    ply: int
    san: str
    phase: Phase
    window_cp_loss: int
    score_before: int
    score_after: int


@dataclass(frozen=True)
class PhaseStats:
    phase: Phase
    moves: int
    avg_cp_loss: float
    errors: int


@dataclass(frozen=True)
class GameMetrics:
    accuracy: float
    avg_cp_loss: float
    blunders: int
    mistakes: int
    inaccuracies: int
    phases: tuple[PhaseStats, ...]


@dataclass(frozen=True)
class GameAnalysis:
    game: ParsedGame
    side: Side
    plies: tuple[PlyEval, ...]
    errors: tuple[ErrorEvent, ...]
    breaking_point: BreakingPoint | None
    metrics: GameMetrics


@dataclass(frozen=True)
class Evidence:
    game_id: str
    ply: int | None = None
    san: str | None = None
    detail: str = ""


@dataclass(frozen=True)
class PatternHit:
    """Сквозной паттерн: ошибка, повторяющаяся от партии к партии."""

    code: str
    title: str
    summary: str
    severity: float
    confidence: float
    sample_size: int
    evidence: tuple[Evidence, ...]

    @property
    def weight(self) -> float:
        return self.severity * self.confidence
