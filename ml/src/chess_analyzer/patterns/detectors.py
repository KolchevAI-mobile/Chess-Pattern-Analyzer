"""Детекторы сквозных паттернов.

Каждый детектор — чистая функция от разобранных партий одного игрока.
Это позволяет тестировать логику без движка и API, а позже заменять
эвристики на статистические/ML-модели по одному.
"""

from __future__ import annotations

from statistics import mean

from chess_analyzer.config import Settings
from chess_analyzer.domain.models import (
    Evidence,
    GameAnalysis,
    PatternHit,
    Phase,
    Side,
)

ADVANTAGE_CP = 200
SAFE_CP = 50
MIN_OPENING_MOVES = 6


def _player_pov(cp: int, side: Side) -> int:
    return cp if side is Side.WHITE else -cp


def _player_plies(analysis: GameAnalysis):
    return [ply for ply in analysis.plies if ply.side is analysis.side]


def detect_conversion_collapse(
    analyses: tuple[GameAnalysis, ...],
    settings: Settings,
) -> PatternHit | None:
    """Потеря перевеса: была позиция +2.0 и больше, а партия не выиграна."""
    with_advantage: list[GameAnalysis] = []
    collapsed: list[GameAnalysis] = []
    evidence: list[Evidence] = []

    for analysis in analyses:
        plies = _player_plies(analysis)
        if not plies:
            continue
        peak_value, peak_ply = max(
            (
                (
                    max(
                        _player_pov(ply.score_before, analysis.side),
                        _player_pov(ply.score_after, analysis.side),
                    ),
                    ply,
                )
                for ply in plies
            ),
            key=lambda item: item[0],
        )
        if peak_value < ADVANTAGE_CP:
            continue

        with_advantage.append(analysis)
        final = _player_pov(plies[-1].score_after, analysis.side)
        won = analysis.game.result.is_win_for(analysis.side)
        if not won or final < SAFE_CP:
            collapsed.append(analysis)
            evidence.append(
                Evidence(
                    game_id=analysis.game.game_id,
                    ply=peak_ply.ply,
                    san=peak_ply.san,
                    detail=(
                        f"перевес {peak_value / 100:+.1f} → финал {final / 100:+.1f}, "
                        f"результат {analysis.game.result.value}"
                    ),
                )
            )

    if not with_advantage or not collapsed:
        return None

    rate = len(collapsed) / len(with_advantage)
    return PatternHit(
        code="conversion_collapse",
        title="Потеря перевеса",
        summary=(
            f"В {len(collapsed)} из {len(with_advantage)} партий с перевесом "
            f"≥ +{ADVANTAGE_CP / 100:.1f} позиция была упущена."
        ),
        severity=round(rate, 3),
        confidence=round(min(1.0, len(with_advantage) / 5.0), 3),
        sample_size=len(with_advantage),
        evidence=tuple(evidence[:5]),
    )


def detect_time_trouble(
    analyses: tuple[GameAnalysis, ...],
    settings: Settings,
) -> PatternHit | None:
    """Цейтнот: в низком остатке времени ошибки заметно дороже."""
    low_clock = []
    normal_clock = []
    for analysis in analyses:
        for ply in _player_plies(analysis):
            if ply.clock is None:
                continue
            if ply.clock <= settings.low_clock_seconds:
                low_clock.append((analysis.game.game_id, ply))
            elif ply.clock >= settings.normal_clock_seconds:
                normal_clock.append(ply)

    if len(low_clock) < 8 or len(normal_clock) < 8:
        return None

    avg_low = mean(ply.cp_loss for _, ply in low_clock)
    avg_normal = mean(ply.cp_loss for ply in normal_clock)
    if avg_low < 60:
        return None

    ratio = float("inf") if avg_normal <= 0 else avg_low / avg_normal
    if ratio < 1.5:
        return None

    worst = sorted(low_clock, key=lambda item: item[1].cp_loss, reverse=True)[:3]
    return PatternHit(
        code="time_trouble",
        title="Ошибки в цейтноте",
        summary=(
            f"При остатке ≤ {settings.low_clock_seconds:.0f} с потеря на ход "
            f"в {ratio:.1f}× выше ({avg_low:.0f} cp против {avg_normal:.0f} cp)."
        ),
        severity=round(min(1.0, (ratio - 1.0) / 1.5), 3),
        confidence=round(min(1.0, len(low_clock) / 20.0), 3),
        sample_size=len(low_clock),
        evidence=tuple(
            Evidence(
                game_id=game_id,
                ply=ply.ply,
                san=ply.san,
                detail=f"{ply.cp_loss} cp при {ply.clock:.0f} с на часах",
            )
            for game_id, ply in worst
        ),
    )


def detect_opening_leak(
    analyses: tuple[GameAnalysis, ...],
    settings: Settings,
) -> PatternHit | None:
    """Дебютные потери: ошибки в начале партии повторяются из раза в раз."""
    losses: list[int] = []
    evidence: list[Evidence] = []
    games = 0

    for analysis in analyses:
        opening = [ply for ply in _player_plies(analysis) if ply.phase is Phase.OPENING]
        if len(opening) < MIN_OPENING_MOVES:
            continue
        games += 1
        losses.extend(ply.cp_loss for ply in opening)
        worst = max(opening, key=lambda ply: ply.cp_loss)
        if worst.cp_loss >= settings.inaccuracy_cp:
            evidence.append(
                Evidence(
                    game_id=analysis.game.game_id,
                    ply=worst.ply,
                    san=worst.san,
                    detail=f"{worst.cp_loss} cp в дебюте",
                )
            )

    if games == 0 or not losses:
        return None

    average = mean(losses)
    if average < 40 or len(evidence) < 2:
        return None

    return PatternHit(
        code="opening_leak",
        title="Дебютные потери",
        summary=(
            f"Средняя потеря в дебюте — {average:.0f} cp; "
            f"в {len(evidence)} партиях ошибки ≥ {settings.inaccuracy_cp} cp."
        ),
        severity=round(min(1.0, average / 120.0), 3),
        confidence=round(min(1.0, games / 4.0), 3),
        sample_size=games,
        evidence=tuple(evidence[:5]),
    )


def detect_phase_gap(
    analyses: tuple[GameAnalysis, ...],
    settings: Settings,
) -> PatternHit | None:
    """Фазовая слабость: одна фаза проседает сильнее остальных."""
    by_phase: dict[Phase, list[int]] = {}
    for analysis in analyses:
        for ply in _player_plies(analysis):
            by_phase.setdefault(ply.phase, []).append(ply.cp_loss)

    usable = {phase: values for phase, values in by_phase.items() if len(values) >= 8}
    if len(usable) < 2:
        return None

    worst_phase, worst_values = max(usable.items(), key=lambda item: mean(item[1]))
    other_values = [
        loss for phase, values in usable.items() if phase is not worst_phase for loss in values
    ]
    worst_avg = mean(worst_values)
    other_avg = mean(other_values)

    if worst_avg < max(40.0, other_avg * 1.4):
        return None

    evidence = []
    for analysis in analyses:
        candidates = [
            ply
            for ply in _player_plies(analysis)
            if ply.phase is worst_phase and ply.cp_loss >= settings.inaccuracy_cp
        ]
        if not candidates:
            continue
        worst = max(candidates, key=lambda ply: ply.cp_loss)
        evidence.append(
            Evidence(
                game_id=analysis.game.game_id,
                ply=worst.ply,
                san=worst.san,
                detail=f"{worst.cp_loss} cp в фазе «{worst_phase.title_ru}»",
            )
        )

    return PatternHit(
        code="phase_gap",
        title=f"Слабое место: {worst_phase.title_ru}",
        summary=(
            f"Средняя потеря в фазе «{worst_phase.title_ru}» — {worst_avg:.0f} cp "
            f"против {other_avg:.0f} cp в остальных фазах."
        ),
        severity=round(min(1.0, (worst_avg / max(other_avg, 10.0) - 1.0) / 2.0), 3),
        confidence=round(min(1.0, len(worst_values) / 25.0), 3),
        sample_size=len(worst_values),
        evidence=tuple(evidence[:5]),
    )


DETECTORS = (
    detect_conversion_collapse,
    detect_time_trouble,
    detect_opening_leak,
    detect_phase_gap,
)


def detect_patterns(
    analyses: tuple[GameAnalysis, ...],
    settings: Settings,
) -> list[PatternHit]:
    """Все сработавшие детекторы, отсортированные по силе сигнала."""
    hits = [
        hit
        for detector in DETECTORS
        if (hit := detector(analyses, settings)) is not None
    ]
    return sorted(hits, key=lambda hit: hit.weight, reverse=True)
