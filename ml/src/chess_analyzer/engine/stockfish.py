"""Адаптер Stockfish (UCI) через python-chess."""

from __future__ import annotations

import chess
import chess.engine

from chess_analyzer.engine.base import CP_LIMIT, MATE_CP, EvalScore, Evaluator, clamp_cp


class StockfishEvaluator(Evaluator):
    name = "stockfish"

    def __init__(
        self,
        path: str,
        depth: int = 10,
        threads: int = 1,
        hash_mb: int = 64,
    ) -> None:
        self._depth = depth
        self._engine = chess.engine.SimpleEngine.popen_uci(path)
        try:
            self._engine.configure({"Threads": threads, "Hash": hash_mb})
        except chess.engine.EngineError:
            # Старые сборки могут не знать этих опций — не критично.
            pass

    def evaluate(self, board: chess.Board) -> EvalScore:
        if board.is_checkmate():
            # Мат уже случился: сторона хода — проигравшая.
            return EvalScore(
                cp=-CP_LIMIT if board.turn == chess.WHITE else CP_LIMIT,
                mate=-1 if board.turn == chess.WHITE else 1,
            )
        if board.is_game_over(claim_draw=False):
            return EvalScore(cp=0, mate=None)

        info = self._engine.analyse(board, chess.engine.Limit(depth=self._depth))
        pov = info.get("score")
        if pov is None:
            return EvalScore(cp=0, depth=self._depth)
        white = pov.white()
        mate = white.mate() if white.is_mate() else None
        cp = white.score(mate_score=MATE_CP) or 0
        return EvalScore(cp=clamp_cp(cp), mate=mate, depth=self._depth)

    def close(self) -> None:
        try:
            self._engine.quit()
        except chess.engine.EngineError:
            pass
