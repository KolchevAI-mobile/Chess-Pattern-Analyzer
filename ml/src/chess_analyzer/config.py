"""Конфигурация ML-ядра.

Все параметры читаются из переменных окружения (и .env при локальном запуске),
чтобы один и тот же код работал и в docker-compose, и в ручном режиме.
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    service_name: str = "chess-pattern-ml"
    version: str = "0.1.0"

    # --- Движок -------------------------------------------------------
    # Если путь не задан или файла нет — используется эвристический
    # оценщик (материал), чтобы пайплайн оставался рабочим без Stockfish.
    stockfish_path: str | None = None
    stockfish_depth: int = 10
    # Жёсткий потолок глубины на MVP: на бесплатных 1-2 vCPU depth 12+ превращает
    # разбор 20 партий в 3-5 минут. Поднимается вместе с переездом на выделенный сервер.
    max_depth: int = 10
    stockfish_threads: int = 1
    stockfish_hash_mb: int = 64

    # --- Ограничения запроса ------------------------------------------
    max_games_per_request: int = 10
    max_plies_per_game: int = 140

    # --- Определения фаз и ошибок -------------------------------------
    opening_plies: int = 20
    endgame_major_minor_limit: int = 6

    inaccuracy_cp: int = 50
    mistake_cp: int = 100
    blunder_cp: int = 200

    low_clock_seconds: float = 30.0
    normal_clock_seconds: float = 60.0

    @property
    def stockfish_available(self) -> bool:
        return bool(self.stockfish_path) and Path(self.stockfish_path).is_file()


@lru_cache
def get_settings() -> Settings:
    return Settings()
