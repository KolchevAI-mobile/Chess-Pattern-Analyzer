"""HTTP-маршруты ML-ядра."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request

from chess_analyzer.api.mapping import to_response
from chess_analyzer.api.schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    EngineOut,
    HealthOut,
    ServiceCardOut,
)
from chess_analyzer.config import get_settings
from chess_analyzer.engine.factory import describe_engine
from chess_analyzer.errors import AnalysisError
from chess_analyzer.service import AnalysisService

router = APIRouter()


@router.get("/health", response_model=HealthOut, tags=["system"])
def health() -> HealthOut:
    settings = get_settings()
    engine = describe_engine(settings)
    return HealthOut(
        status="ok",
        service=settings.service_name,
        version=settings.version,
        engine=EngineOut(
            name=engine.name,
            available=engine.available,
            depth=engine.depth,
            note=engine.note,
        ),
    )


@router.get("/", response_model=ServiceCardOut, tags=["system"])
def index() -> ServiceCardOut:
    settings = get_settings()
    return ServiceCardOut(
        service="Chess Pattern Analyzer",
        part="ml",
        version=settings.version,
        endpoints=["/health", "/docs", "/api/v1/analyze"],
    )


@router.post("/api/v1/analyze", response_model=AnalyzeResponse, tags=["analysis"])
def analyze(payload: AnalyzeRequest, request: Request) -> AnalyzeResponse:
    service: AnalysisService = request.app.state.service
    try:
        result = service.analyze(
            pgn=payload.pgn,
            player=payload.player,
            depth=payload.depth,
            max_games=payload.max_games,
        )
    except AnalysisError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return to_response(result, payload.player)
