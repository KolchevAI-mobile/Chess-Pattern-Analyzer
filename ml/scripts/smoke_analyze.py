"""Смоук-скрипт ML-ядра: прогоняет PGN через работающий сервис и печатает отчёт.

Использование:
    python scripts/smoke_analyze.py                # http://127.0.0.1:8000
    python scripts/smoke_analyze.py --url http://host:port

Скрипт полезен и как пример вызова API, и как быстрый e2e-чек после изменений.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.request
from pathlib import Path

DEFAULT_PGN = Path(__file__).resolve().parent.parent / "samples" / "opera-game.pgn"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="http://127.0.0.1:8000")
    parser.add_argument("--pgn", default=str(DEFAULT_PGN))
    parser.add_argument("--player", default="Duke Karl")
    args = parser.parse_args()

    pgn = Path(args.pgn).read_text(encoding="utf-8")
    payload = json.dumps({"pgn": pgn, "player": args.player}).encode("utf-8")
    request = urllib.request.Request(
        f"{args.url}/api/v1/analyze",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    with urllib.request.urlopen(request, timeout=300) as response:
        report = json.load(response)

    meta = report["meta"]
    print(f"engine={meta['engine']} depth={meta['depth']} " f"games={meta['games_analyzed']} time={meta['duration_ms']}ms")

    for game in report["games"]:
        print(
            f"\n{game['white']} — {game['black']} ({game['result']}) "
            f"accuracy={game['accuracy']} avg_loss={game['avg_cp_loss']} "
            f"blunders={game['blunders']} mistakes={game['mistakes']}"
        )
        for error in game["errors"][:3]:
            print(f"  ply {error['ply']:>3} {error['san']:<7} {error['cp_loss']:>4} cp ({error['severity']})")
        if game["breaking_point"]:
            point = game["breaking_point"]
            print(f"  надлом: ход {point['ply']} {point['san']} ({point['phase']})")

    print("\nПаттерны:")
    if not report["patterns"]:
        print("  (не найдено — нужно больше партий или глубина выше)")
    for pattern in report["patterns"]:
        print(f"  [{pattern['severity']:.2f}/{pattern['confidence']:.2f}] {pattern['title']}: {pattern['summary']}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
