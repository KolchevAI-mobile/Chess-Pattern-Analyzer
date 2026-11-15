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


def test_meta_contains_analysis_and_engine_versions() -> None:
    app = create_app()
    app.state.service = AnalysisService(Settings(_env_file=None, stockfish_path=None))
    client = TestClient(app)

    response = client.post("/api/v1/analyze", json={"pgn": PGN, "player": "Bob"})

    assert response.status_code == 200
    meta = response.json()["meta"]
    assert meta["analysis_version"] == "1"
    assert meta["engine_version"]
