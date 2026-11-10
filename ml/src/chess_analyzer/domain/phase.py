"""Определение фазы партии.

Фаза — грубая, но воспроизводимая эвристика. Она нужна для того, чтобы
сравнивать качество игры по фазам и находить сквозные паттерны
(«проседает эндшпиль»), поэтому важнее стабильность, чем точность.
"""

from __future__ import annotations

import chess

from chess_analyzer.domain.models import Phase

MAJOR_MINOR_TYPES = (chess.QUEEN, chess.ROOK, chess.BISHOP, chess.KNIGHT)


def major_minor_count(board: chess.Board) -> int:
    """Число фигур (кроме королей и пешек) на доске — обе стороны."""
    return sum(
        1
        for piece in board.piece_map().values()
        if piece.piece_type in MAJOR_MINOR_TYPES
    )


def classify_phase(
    board: chess.Board,
    ply: int,
    opening_plies: int = 20,
    endgame_major_minor_limit: int = 6,
) -> Phase:
    """Фаза позиции после хода `ply`.

    Порядок проверок важен: дебют определяется по номеру полухода,
    эндшпиль — по количеству фигур, всё остальное — миттельшпиль.
    """
    if ply <= opening_plies:
        return Phase.OPENING
    if major_minor_count(board) <= endgame_major_minor_limit:
        return Phase.ENDGAME
    return Phase.MIDDLEGAME
