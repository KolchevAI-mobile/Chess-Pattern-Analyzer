from __future__ import annotations

import chess

from chess_analyzer.domain.phase import classify_phase, major_minor_count
from chess_analyzer.domain.models import Phase


def test_major_minor_count_start_position() -> None:
    assert major_minor_count(chess.Board()) == 14


def test_start_position_is_opening() -> None:
    assert classify_phase(chess.Board(), ply=1) is Phase.OPENING


def test_full_board_after_opening_is_middlegame() -> None:
    # Позиция после 1.e4, но полуход уже далеко за дебютным окном.
    board = chess.Board()
    board.push_san("e4")
    assert classify_phase(board, ply=30) is Phase.MIDDLEGAME


def test_bare_kings_endgame() -> None:
    board = chess.Board("8/4k3/8/8/8/8/4K3/8 w - - 0 40")
    assert classify_phase(board, ply=60) is Phase.ENDGAME


def test_opening_wins_over_endgame_detection() -> None:
    # Ранний размен, но номер полухода ещё дебютный.
    board = chess.Board("8/4k3/8/8/8/8/4K3/8 w - - 0 1")
    assert classify_phase(board, ply=3) is Phase.OPENING
