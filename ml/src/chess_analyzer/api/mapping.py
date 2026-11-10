"""Преобразование доменных объектов в ответы API."""

from __future__ import annotations

from chess_analyzer.api.schemas import (
    AnalyzeResponse,
    BreakingPointOut,
    BreakingPointSummaryOut,
    ErrorOut,
    EvidenceOut,
    GameOut,
    MetaOut,
    PatternOut,
    PhaseStatOut,
)
from chess_analyzer.domain.models import GameAnalysis
from chess_analyzer.patterns.breaking_point import summarize_breaking_points
from chess_analyzer.service import AnalysisResult

MAX_ERRORS_PER_GAME = 8


def _game_out(analysis: GameAnalysis) -> GameOut:
    game = analysis.game
    metrics = analysis.metrics
    breaking = analysis.breaking_point
    top_errors = sorted(analysis.errors, key=lambda error: error.cp_loss, reverse=True)

    return GameOut(
        game_id=game.game_id,
        white=game.white,
        black=game.black,
        white_elo=game.white_elo,
        black_elo=game.black_elo,
        result=game.result.value,
        player_side=analysis.side,
        accuracy=metrics.accuracy,
        avg_cp_loss=metrics.avg_cp_loss,
        blunders=metrics.blunders,
        mistakes=metrics.mistakes,
        inaccuracies=metrics.inaccuracies,
        phases=[
            PhaseStatOut(
                phase=phase.phase,
                moves=phase.moves,
                avg_cp_loss=phase.avg_cp_loss,
                errors=phase.errors,
            )
            for phase in metrics.phases
        ],
        breaking_point=(
            BreakingPointOut(
                ply=breaking.ply,
                san=breaking.san,
                phase=breaking.phase,
                window_cp_loss=breaking.window_cp_loss,
                score_before=breaking.score_before,
                score_after=breaking.score_after,
            )
            if breaking
            else None
        ),
        errors=[
            ErrorOut(
                ply=error.ply,
                san=error.san,
                side=error.side,
                phase=error.phase,
                cp_loss=error.cp_loss,
                severity=error.severity,
                score_before=error.score_before,
                score_after=error.score_after,
                clock=error.clock,
            )
            for error in top_errors[:MAX_ERRORS_PER_GAME]
        ],
    )


def to_response(result: AnalysisResult, player: str) -> AnalyzeResponse:
    summary = summarize_breaking_points(result.games)
    return AnalyzeResponse(
        meta=MetaOut(
            player=player,
            games_total=len(result.games) + len(result.skipped),
            games_analyzed=len(result.games),
            skipped_games=list(result.skipped),
            engine=result.engine,
            depth=result.depth,
            duration_ms=result.duration_ms,
        ),
        games=[_game_out(analysis) for analysis in result.games],
        patterns=[
            PatternOut(
                code=hit.code,
                title=hit.title,
                summary=hit.summary,
                severity=hit.severity,
                confidence=hit.confidence,
                sample_size=hit.sample_size,
                evidence=[
                    EvidenceOut(
                        game_id=item.game_id,
                        ply=item.ply,
                        san=item.san,
                        detail=item.detail,
                    )
                    for item in hit.evidence
                ],
            )
            for hit in result.patterns
        ],
        breaking_point=(
            BreakingPointSummaryOut(
                games=summary.games,
                games_with_breaking_point=summary.games_with_breaking_point,
                median_ply=summary.median_ply,
                dominant_phase=summary.dominant_phase,
            )
            if summary
            else None
        ),
    )
