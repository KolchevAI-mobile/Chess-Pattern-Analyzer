from __future__ import annotations

from fastapi.testclient import TestClient

from chess_analyzer.config import Settings
from chess_analyzer.main import create_app
from chess_analyzer.service import AnalysisService

PGN = """
[Event "Scholar"]
[Site "https://lichess.org/scholar"]
[White "Alice"]
[Black "Bob"]
[Result "1-0"]

1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7# 1-0
"""


def _client() -> TestClient:
    app = create_app()
    app.state.service = AnalysisService(Settings(_env_file=None, stockfish_path=None))
    return TestClient(app)


def test_health() -> None:
    response = _client().get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert body["service"] == "chess-pattern-ml"
    assert body["engine"]["name"] in {"heuristic", "stockfish"}


def test_index() -> None:
    response = _client().get("/")
    assert response.status_code == 200
    assert response.json()["part"] == "ml"


def test_analyze_returns_games_and_meta() -> None:
    response = _client().post(
        "/api/v1/analyze",
        json={"pgn": PGN, "player": "Bob"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["meta"]["games_analyzed"] == 1
    assert body["meta"]["player"] == "Bob"
    assert body["games"][0]["player_side"] == "black"
    assert "errors" in body["games"][0]
    assert body["games"][0]["accuracy"] <= 100


def test_analyze_unknown_player_is_422() -> None:
    response = _client().post(
        "/api/v1/analyze",
        json={"pgn": PGN, "player": "Carol"},
    )
    assert response.status_code == 422
    assert "Carol" in response.json()["detail"]


def test_analyze_empty_pgn_is_validation_error() -> None:
    response = _client().post("/api/v1/analyze", json={"pgn": "   ", "player": "Bob"})
    assert response.status_code == 422


def test_depth_is_capped_by_settings() -> None:
    # На MVP глубина жёстко ограничена: 20 партий на слабом CPU иначе не уложатся.
    service = AnalysisService(Settings(_env_file=None, stockfish_path=None, max_depth=10))

    assert service.effective_depth(18) == 10
    assert service.effective_depth(None) == 10
    assert service.effective_depth(4) == 4
    # 0 и None означают «не задано» — берём глубину по умолчанию.
    assert service.effective_depth(0) == 10


def test_depth_cap_does_not_break_heuristic_mode() -> None:
    # Эвристика глубину не использует — в meta честно отдаём 0.
    response = _client().post(
        "/api/v1/analyze",
        json={"pgn": PGN, "player": "Bob", "depth": 18},
    )

    assert response.status_code == 200
    assert response.json()["meta"]["depth"] == 0
