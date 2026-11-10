from __future__ import annotations

from typing import Sequence

import chess

from chess_analyzer.analysis.game import analyze_game, cp_loss, move_accuracy
from chess_analyzer.config import Settings
from chess_analyzer.domain.models import Severity, Side
from chess_analyzer.engine.base import EvalScore, Evaluator
from chess_analyzer.engine.heuristic import HeuristicEvaluator
from chess_analyzer.ingest.pgn import parse_pgn

SCHOLARS_MATE = """
[Event "Scholar"]
[Site "https://lichess.org/scholar"]
[White "Alice"]
[Black "Bob"]
[Result "1-0"]

1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0
"""


class ScriptedEvaluator(Evaluator):
    """Отдаёт заранее заданные оценки по порядку вызовов (детерминированный тест)."""

    name = "scripted"

    def __init__(self, scores: Sequence[int]) -> None:
        self._scores = list(scores)
        self._index = 0

    def evaluate(self, board: chess.Board) -> EvalScore:
        position = min(self._index, len(self._scores) - 1)
        self._index += 1
        return EvalScore(cp=self._scores[position])


def _settings() -> Settings:
    return Settings(_env_file=None, stockfish_path=None)


def test_cp_loss_is_side_relative() -> None:
    # Белые потеряли 200 cp (оценка ушла с +100 до -100).
    assert cp_loss(before_cp=100, after_cp=-100, side=Side.WHITE) == 200
    # Для чёрных тот же сдвиг — это выигрыш, потерь нет.
    assert cp_loss(before_cp=100, after_cp=-100, side=Side.BLACK) == 0


def test_cp_loss_is_capped() -> None:
    assert cp_loss(before_cp=3000, after_cp=-3000, side=Side.WHITE) == 1000


def test_move_accuracy_in_range() -> None:
    perfect = move_accuracy(50, 50, Side.WHITE)
    blunder = move_accuracy(100, -500, Side.WHITE)
    assert 0.0 <= blunder < perfect <= 100.0


def test_analyze_marks_blunder_when_eval_collapses() -> None:
    # Оценки по позициям: старт, после каждого из 7 ходов.
    game = parse_pgn(SCHOLARS_MATE)[0]
    evaluator = ScriptedEvaluator([0, 0, 0, 0, 0, 100, 2500, 3000])

    analysis = analyze_game(game, Side.BLACK, evaluator, _settings())

    assert len(analysis.plies) == 7
    blunders = [error for error in analysis.errors if error.severity is Severity.BLUNDER]
    assert len(blunders) == 1
    assert blunders[0].ply == 6  # 3...Nf6 — ход, после которого виден мат
    assert blunders[0].cp_loss == 1000  # потеря ограничена сверху
    assert analysis.metrics.blunders == 1
    assert analysis.metrics.mistakes == 0


def test_analyze_returns_errors_only_for_requested_side() -> None:
    game = parse_pgn(SCHOLARS_MATE)[0]
    evaluator = ScriptedEvaluator([0, 0, 0, 0, 0, 100, 2500, 3000])

    analysis = analyze_game(game, Side.BLACK, evaluator, _settings())

    assert analysis.errors
    assert all(error.side is Side.BLACK for error in analysis.errors)


def test_heuristic_evaluator_reports_mate_score() -> None:
    # Эвристика видит только материал, но мат детектирует корректно.
    game = parse_pgn(SCHOLARS_MATE)[0]
    analysis = analyze_game(game, Side.BLACK, HeuristicEvaluator(), _settings())

    assert analysis.plies[-1].score_after == 3000
    assert analysis.plies[-1].san == "Qxf7#"


def test_metrics_are_aggregated_by_phase() -> None:
    game = parse_pgn(SCHOLARS_MATE)[0]
    evaluator = ScriptedEvaluator([0, 0, 0, 0, 0, 100, 2500, 3000])

    analysis = analyze_game(game, Side.BLACK, evaluator, _settings())

    assert analysis.metrics.phases
    total_moves = sum(phase.moves for phase in analysis.metrics.phases)
    assert total_moves == 3  # три хода чёрных
