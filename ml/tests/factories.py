"""Фабрики доменных объектов для тестов (без движка и без сети)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Sequence

from chess_analyzer.domain.models import (
    ErrorEvent,
    GameAnalysis,
    GameMetrics,
    GameResult,
    ParsedGame,
    ParsedMove,
    Phase,
    PhaseStats,
    PlyEval,
    Severity,
    Side,
)
from chess_analyzer.patterns.breaking_point import detect_breaking_point


def make_analysis(
    side: Side = Side.WHITE,
    *,
    losses: Sequence[int],
    evals: Sequence[int] | None = None,
    phases: Sequence[Phase] | None = None,
    clocks: Sequence[float | None] | None = None,
    result: GameResult = GameResult.DRAW,
    game_id: str = "g1",
) -> GameAnalysis:
    """Собирает GameAnalysis из последовательности потерь (cp) для одной стороны.

    `evals` — оценка с точки зрения анализируемого игрока перед каждым ходом
    (плюс финальная), чтобы проверять сценарии «был перевес → упустил».
    """
    count = len(losses)
    if evals is None:
        evals = [0] * (count + 1)
    assert len(evals) >= count + 1, "нужно на одну оценку больше, чем ходов"

    plies: list[PlyEval] = []
    for index, loss in enumerate(losses):
        before_pov = evals[index]
        after_pov = evals[index + 1]
        before = before_pov if side is Side.WHITE else -before_pov
        after = after_pov if side is Side.WHITE else -after_pov
        plies.append(
            PlyEval(
                ply=2 * index + 1 if side is Side.WHITE else 2 * index + 2,
                san="e4",
                side=side,
                phase=phases[index] if phases else Phase.MIDDLEGAME,
                score_before=before,
                score_after=after,
                cp_loss=loss,
                clock=clocks[index] if clocks else None,
            )
        )

    moves = tuple(
        ParsedMove(ply=ply.ply, san=ply.san, clock=ply.clock)
        for ply in plies
    )
    game = ParsedGame(
        game_id=game_id,
        white="TestPlayer" if side is Side.WHITE else "Opponent",
        black="Opponent" if side is Side.WHITE else "TestPlayer",
        white_elo=1500,
        black_elo=1500,
        result=result,
        initial_fen=None,
        moves=moves,
    )

    errors = tuple(
        ErrorEvent(
            ply=ply.ply,
            san=ply.san,
            side=ply.side,
            phase=ply.phase,
            cp_loss=ply.cp_loss,
            severity=(
                Severity.BLUNDER
                if ply.cp_loss >= 200
                else Severity.MISTAKE
                if ply.cp_loss >= 100
                else Severity.INACCURACY
            ),
            score_before=ply.score_before,
            score_after=ply.score_after,
            clock=ply.clock,
        )
        for ply in plies
        if ply.cp_loss >= 50
    )

    phase_stats = tuple(
        PhaseStats(
            phase=phase,
            moves=sum(1 for ply in plies if ply.phase is phase),
            avg_cp_loss=0.0,
            errors=0,
        )
        for phase in dict.fromkeys(ply.phase for ply in plies)
    )

    metrics = GameMetrics(
        accuracy=0.0,
        avg_cp_loss=sum(losses) / count if count else 0.0,
        blunders=sum(1 for error in errors if error.severity is Severity.BLUNDER),
        mistakes=sum(1 for error in errors if error.severity is Severity.MISTAKE),
        inaccuracies=sum(1 for error in errors if error.severity is Severity.INACCURACY),
        phases=phase_stats,
    )

    return GameAnalysis(
        game=game,
        side=side,
        plies=tuple(plies),
        errors=errors,
        breaking_point=detect_breaking_point(tuple(plies), side),
        metrics=metrics,
    )
