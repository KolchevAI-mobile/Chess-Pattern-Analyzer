from __future__ import annotations

from chess_analyzer.config import Settings
from chess_analyzer.domain.models import GameResult, Phase, Side
from chess_analyzer.patterns.detectors import (
    detect_conversion_collapse,
    detect_opening_leak,
    detect_patterns,
    detect_phase_gap,
    detect_time_trouble,
)

from factories import make_analysis


def _settings() -> Settings:
    return Settings(_env_file=None, stockfish_path=None)


def test_conversion_collapse_detected() -> None:
    # Игрок имел +4.0, закончил вничью.
    analysis = make_analysis(
        losses=[10] * 10,
        evals=[0, 100, 400, 420, 380, 200, 100, 50, 0, -50, -100],
        result=GameResult.DRAW,
    )

    hit = detect_conversion_collapse((analysis,), _settings())

    assert hit is not None
    assert hit.code == "conversion_collapse"
    assert hit.evidence
    assert "4.2" in hit.evidence[0].detail


def test_conversion_collapse_not_reported_without_advantage() -> None:
    analysis = make_analysis(losses=[10] * 10, evals=[0] * 11, result=GameResult.DRAW)
    assert detect_conversion_collapse((analysis,), _settings()) is None


def test_conversion_collapse_not_reported_when_game_won() -> None:
    analysis = make_analysis(
        losses=[10] * 10,
        evals=[0, 250, 300, 320, 300, 280, 260, 240, 220, 200, 200],
        result=GameResult.WHITE_WIN,
    )
    assert detect_conversion_collapse((analysis,), _settings()) is None


def test_time_trouble_detected() -> None:
    analysis = make_analysis(
        losses=[200] * 10 + [20] * 10,
        clocks=[10.0] * 10 + [120.0] * 10,
    )

    hit = detect_time_trouble((analysis,), _settings())

    assert hit is not None
    assert hit.code == "time_trouble"
    assert hit.severity > 0.5
    assert len(hit.evidence) == 3


def test_time_trouble_requires_enough_samples() -> None:
    analysis = make_analysis(losses=[200] * 5, clocks=[10.0] * 5)
    assert detect_time_trouble((analysis,), _settings()) is None


def test_time_trouble_ignores_games_without_clocks() -> None:
    analysis = make_analysis(losses=[200] * 20)
    assert detect_time_trouble((analysis,), _settings()) is None


def test_opening_leak_detected() -> None:
    analyses = tuple(
        make_analysis(
            losses=[60] * 8,
            phases=[Phase.OPENING] * 8,
            game_id=f"g{index}",
        )
        for index in range(2)
    )

    hit = detect_opening_leak(analyses, _settings())

    assert hit is not None
    assert hit.code == "opening_leak"
    assert hit.sample_size == 2


def test_opening_leak_needs_clean_enough_games() -> None:
    analysis = make_analysis(losses=[10] * 8, phases=[Phase.OPENING] * 8)
    assert detect_opening_leak((analysis,), _settings()) is None


def test_phase_gap_detected() -> None:
    analysis = make_analysis(
        losses=[10] * 10 + [120] * 10,
        phases=[Phase.OPENING] * 10 + [Phase.ENDGAME] * 10,
    )

    hit = detect_phase_gap((analysis,), _settings())

    assert hit is not None
    assert hit.code == "phase_gap"
    assert "эндшпиль" in hit.title


def test_detect_patterns_sorted_by_weight() -> None:
    analysis = make_analysis(
        losses=[200] * 10 + [20] * 10,
        clocks=[10.0] * 10 + [120.0] * 10,
        evals=[0, 400] + [0] * 19,
        result=GameResult.DRAW,
    )

    hits = detect_patterns((analysis,), _settings())

    assert hits
    weights = [hit.weight for hit in hits]
    assert weights == sorted(weights, reverse=True)


def test_black_side_losses_are_interpreted_correctly() -> None:
    # Для чёрных рост белой оценки — потеря; -600..-200 в белых cp = рост для чёрных.
    analysis = make_analysis(
        side=Side.BLACK,
        losses=[10] * 10,
        evals=[-600, -500, -200, -100, -50, 0, 50, 100, 150, 200, 250],
        result=GameResult.DRAW,
    )

    hit = detect_conversion_collapse((analysis,), _settings())

    assert hit is not None
    assert hit.code == "conversion_collapse"
