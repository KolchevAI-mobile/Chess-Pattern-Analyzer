"""Эвристический оценщик — запасной путь, когда Stockfish недоступен.

Он не заменяет движок: цель — чтобы весь пайплайн (PGN → ошибки →
паттерны → API) работал и был проверяем без внешнего бинарника.
Оценка = материал + бонус за пару слонов; точность такой оценки
ограничена, и API честно сообщает, что работает в degraded-режиме.
"""

from __future__ import annotations

import chess

from chess_analyzer.engine.base import CP_LIMIT, EvalScore, Evaluator, clamp_cp

PIECE_VALUES = {
    chess.PAWN: 100,
    chess.KNIGHT: 320,
    chess.BISHOP: 330,
    chess.ROOK: 500,
    chess.QUEEN: 900,
    chess.KING: 0,
}


class HeuristicEvaluator(Evaluator):
    name = "heuristic"

    def evaluate(self, board: chess.Board) -> EvalScore:
        if board.is_checkmate():
            return EvalScore(
                cp=-CP_LIMIT if board.turn == chess.WHITE else CP_LIMIT,
                mate=-1 if board.turn == chess.WHITE else 1,
            )
        if board.is_game_over(claim_draw=False):
            return EvalScore(cp=0)

        material = 0
        bishops = {chess.WHITE: 0, chess.BLACK: 0}
        for square, piece in board.piece_map().items():
            value = PIECE_VALUES[piece.piece_type]
            material += value if piece.color == chess.WHITE else -value
            if piece.piece_type == chess.BISHOP:
                bishops[piece.color] += 1

        if bishops[chess.WHITE] >= 2:
            material += 30
        if bishops[chess.BLACK] >= 2:
            material -= 30

        return EvalScore(cp=clamp_cp(material))
