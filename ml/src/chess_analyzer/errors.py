"""Ошибки домена, которые API превращает в HTTP-ответы."""

from __future__ import annotations


class AnalysisError(Exception):
    """Базовая ошибка анализа (ожидаемая, не 500)."""


class EmptyPgnError(AnalysisError):
    """В запросе нет ни одной партии."""


class PlayerNotFoundError(AnalysisError):
    """Указанный игрок не встречается ни в одной партии."""
