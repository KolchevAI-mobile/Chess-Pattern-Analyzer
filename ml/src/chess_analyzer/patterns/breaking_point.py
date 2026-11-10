"""Детектор «надлома» (breaking point).

Формального определения в паспорте проекта пока нет, поэтому здесь
сознательно простой, объяснимый метод сдвига средних: ищем такой момент,
после которого средняя потеря на ход резко выше, чем до него. Гипотеза
уточняется на накопленных данных (см. Roadmap.md, веха M3).
"""

from __future__ import annotations

from dataclasses import dataclass
from statistics import mean, median

from chess_analyzer.domain.models import BreakingPoint, GameAnalysis, Phase, PlyEval, Side

WINDOW = 8
MIN_SWING_CP = 150.0


@dataclass(frozen=True)
class BreakingPointSummary:
    games: int
    games_with_breaking_point: int
    median_ply: float | None
    dominant_phase: Phase | None


def detect_breaking_point(
    plies: tuple[PlyEval, ...],
    side: Side,
    window: int = WINDOW,
    min_swing: float = MIN_SWING_CP,
) -> BreakingPoint | None:
    player_plies = [ply for ply in plies if ply.side is side]
    losses = [ply.cp_loss for ply in player_plies]
    if len(losses) < window * 2:
        return None

    best_swing = 0.0
    best_index: int | None = None
    for index in range(window, len(losses) - window + 1):
        before = mean(losses[index - window : index])
        after = mean(losses[index : index + window])
        swing = after - before
        if swing > best_swing:
            best_swing = swing
            best_index = index

    if best_index is None or best_swing < min_swing:
        return None

    anchor = player_plies[best_index]
    end = player_plies[min(best_index + window - 1, len(player_plies) - 1)]
    return BreakingPoint(
        ply=anchor.ply,
        san=anchor.san,
        phase=anchor.phase,
        window_cp_loss=sum(losses[best_index : best_index + window]),
        score_before=anchor.score_before,
        score_after=end.score_after,
    )


def summarize_breaking_points(analyses: tuple[GameAnalysis, ...]) -> BreakingPointSummary | None:
    """Сводка: как часто и на каком ходу случается надлом (сырая метрика M3)."""
    points = [analysis.breaking_point for analysis in analyses if analysis.breaking_point]
    if not points:
        return None

    plies = [point.ply for point in points if point]
    phases = [point.phase for point in points if point]
    dominant = max(set(phases), key=phases.count) if phases else None

    return BreakingPointSummary(
        games=len(analyses),
        games_with_breaking_point=len(points),
        median_ply=round(float(median(plies)), 1) if plies else None,
        dominant_phase=dominant,
    )
