"""Точка входа FastAPI-приложения ML-ядра.

Запуск: `uvicorn chess_analyzer.main:app --reload --port 8000`
"""

from __future__ import annotations

from fastapi import FastAPI

from chess_analyzer.api.routes import router
from chess_analyzer.config import get_settings
from chess_analyzer.service import AnalysisService


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="Chess Pattern Analyzer — ML core",
        version=settings.version,
        description=(
            "Анализ шахматных партий и поиск сквозных паттернов ошибок. "
            "MVP: cp-loss по ходам, фазы, цейтнот, гипотеза надлома."
        ),
    )
    app.state.settings = settings
    app.state.service = AnalysisService(settings)
    app.include_router(router)
    return app


app = create_app()
