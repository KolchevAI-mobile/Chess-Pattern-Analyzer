"""Оценка партии: cp-loss по ходам, ошибки, точность, метрики по фазам."""

from __future__ import annotations

import math
from statistics import mean

import chess

from chess_analyzer.config import Settings
from chess_analyzer.domain.models import (
    ErrorEvent,
    GameAnalysis,
    GameMetrics,
    ParsedGame,
    Phase,
    PhaseStats,
    PlyEval,
    Severity,
    Side,
)
from chess_analyzer.domain.phase import classify_phase
from chess_analyzer.engine.base import EvalScore, Evaluator
from chess_analyzer.patterns.breaking_point import detect_breaking_point

CP_LOSS_CAP = 1_000


def cp_loss(before_cp: int, after_cp: int, side: Side) -> int:
    """Потеря в сантипешках для сыгравшей стороны (0..1000)."""
    delta = (before_cp - after_cp) if side is Side.WHITE else (after_cp - before_cp)
    return max(0, min(CP_LOSS_CAP, delta))


def win_percent(cp: int) -> float:
    """Отображение cp в «вероятность победы», как в Lichess."""
    bounded = max(-1_000, min(1_000, cp))
    return 50.0 + 50.0 * (2.0 / (1.0 + math.exp(-0.00368208 * bounded)) - 1.0)


def move_accuracy(before_cp: int, after_cp: int, side: Side) -> float:
    """Точность одного хода (0..100) по формуле Lichess."""
    if side is Side.BLACK:
        before_cp, after_cp = -before_cp, -after_cp
    delta = win_percent(before_cp) - win_percent(after_cp)
    accuracy = 103.1668 * math.exp(-0.04354 * delta) - 3.1669
    return max(0.0, min(100.0, accuracy))


def severity_of(loss: int, settings: Settings) -> Severity | None:
    if loss >= settings.blunder_cp:
        return Severity.BLUNDER
    if loss >= settings.mistake_cp:
        return Severity.MISTAKE
    if loss >= settings.inaccuracy_cp:
        return Severity.INACCURACY
    return None


def _evaluate(evaluator: Evaluator, board: chess.Board) -> EvalScore:
    return evaluator.evaluate(board)


def analyze_game(
    game: ParsedGame,
    side: Side,
    evaluator: Evaluator,
    settings: Settings,
) -> GameAnalysis:
    """Полный разбор одной партии с точки зрения стороны `side`."""
    board = chess.Board(game.initial_fen) if game.initial_fen else chess.Board()
    if not board.is_valid():
        raise ValueError(f"Некорректная стартовая позиция в партии {game.game_id}")

    scores: list[EvalScore] = [_evaluate(evaluator, board)]
    plies: list[PlyEval] = []

    for move in game.moves[: settings.max_plies_per_game]:
        mover = Side.WHITE if board.turn == chess.WHITE else Side.BLACK
        try:
            board.push_san(move.san)
        except ValueError:
            # Битый PGN: разбираем то, что успели прочитать.
            break
        scores.append(_evaluate(evaluator, board))
        before, after = scores[-2], scores[-1]
        plies.append(
            PlyEval(
                ply=move.ply,
                san=move.san,
                side=mover,
                phase=classify_phase(
                    board,
                    move.ply,
                    settings.opening_plies,
                    settings.endgame_major_minor_limit,
                ),
                score_before=before.cp,
                score_after=after.cp,
                cp_loss=cp_loss(before.cp, after.cp, mover),
                clock=move.clock,
            )
        )

    player_plies = tuple(ply for ply in plies if ply.side is side)
    errors = tuple(
        ErrorEvent(
            ply=ply.ply,
            san=ply.san,
            side=ply.side,
            phase=ply.phase,
            cp_loss=ply.cp_loss,
            severity=severity,  # type: ignore[arg-type]
            score_before=ply.score_before,
            score_after=ply.score_after,
            clock=ply.clock,
        )
        for ply in player_plies
        if (severity := severity_of(ply.cp_loss, settings)) is not None
    )

    return GameAnalysis(
        game=game,
        side=side,
        plies=tuple(plies),
        errors=errors,
        breaking_point=detect_breaking_point(plies, side),
        metrics=_build_metrics(player_plies, errors),
    )


def _build_metrics(plies: tuple[PlyEval, ...], errors: tuple[ErrorEvent, ...]) -> GameMetrics:
    if not plies:
        return GameMetrics(
            accuracy=0.0,
            avg_cp_loss=0.0,
            blunders=0,
            mistakes=0,
            inaccuracies=0,
            phases=(),
        )

    by_phase: dict[Phase, list[PlyEval]] = {}
    for ply in plies:
        by_phase.setdefault(ply.phase, []).append(ply)

    phase_stats = tuple(
        PhaseStats(
            phase=phase,
            moves=len(items),
            avg_cp_loss=round(mean(item.cp_loss for item in items), 1),
            errors=sum(
                1
                for item in items
                if item.cp_loss >= 50
            ),
        )
        for phase, items in sorted(by_phase.items(), key=lambda kv: list(Phase).index(kv[0]))
    )

    return GameMetrics(
        accuracy=round(mean(move_accuracy(ply.score_before, ply.score_after, ply.side) for ply in plies), 1),
        avg_cp_loss=round(mean(ply.cp_loss for ply in plies), 1),
        blunders=sum(1 for error in errors if error.severity is Severity.BLUNDER),
        mistakes=sum(1 for error in errors if error.severity is Severity.MISTAKE),
        inaccuracies=sum(1 for error in errors if error.severity is Severity.INACCURACY),
        phases=phase_stats,
    )
