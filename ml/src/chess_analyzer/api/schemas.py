"""Схемы HTTP API (pydantic)."""

from __future__ import annotations

from pydantic import BaseModel, Field

from chess_analyzer.domain.models import Phase, Severity, Side


class EngineOut(BaseModel):
    name: str
    available: bool
    depth: int
    note: str


class HealthOut(BaseModel):
    status: str
    service: str
    version: str
    engine: EngineOut


class ServiceCardOut(BaseModel):
    service: str
    part: str
    version: str
    endpoints: list[str]


class AnalyzeRequest(BaseModel):
    pgn: str = Field(..., min_length=10, description="PGN: одна партия или несколько подряд")
    player: str = Field(
        ...,
        min_length=1,
        description="Ник игрока как в PGN (или 'white'/'black'), чьи ошибки анализируем",
    )
    depth: int | None = Field(None, ge=4, le=24, description="Глубина Stockfish; по умолчанию из настроек")
    max_games: int | None = Field(None, ge=1, le=20, description="Сколько партий взять из PGN")


class ErrorOut(BaseModel):
    ply: int
    san: str
    side: Side
    phase: Phase
    cp_loss: int
    severity: Severity
    score_before: int
    score_after: int
    clock: float | None = None


class BreakingPointOut(BaseModel):
    ply: int
    san: str
    phase: Phase
    window_cp_loss: int
    score_before: int
    score_after: int


class PhaseStatOut(BaseModel):
    phase: Phase
    moves: int
    avg_cp_loss: float
    errors: int


class GameOut(BaseModel):
    game_id: str
    white: str
    black: str
    white_elo: int | None = None
    black_elo: int | None = None
    result: str
    player_side: Side
    accuracy: float
    avg_cp_loss: float
    blunders: int
    mistakes: int
    inaccuracies: int
    phases: list[PhaseStatOut]
    breaking_point: BreakingPointOut | None = None
    errors: list[ErrorOut]


class EvidenceOut(BaseModel):
    game_id: str
    ply: int | None = None
    san: str | None = None
    detail: str = ""


class PatternOut(BaseModel):
    code: str
    title: str
    summary: str
    severity: float
    confidence: float
    sample_size: int
    evidence: list[EvidenceOut]


class BreakingPointSummaryOut(BaseModel):
    games: int
    games_with_breaking_point: int
    median_ply: float | None = None
    dominant_phase: Phase | None = None


class MetaOut(BaseModel):
    player: str
    games_total: int
    games_analyzed: int
    skipped_games: list[str]
    engine: str
    depth: int
    duration_ms: int


class AnalyzeResponse(BaseModel):
    meta: MetaOut
    games: list[GameOut]
    patterns: list[PatternOut]
    breaking_point: BreakingPointSummaryOut | None = None
