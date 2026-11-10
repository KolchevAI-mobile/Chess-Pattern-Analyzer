from __future__ import annotations

import sys
from pathlib import Path

# Тестовые фабрики лежат рядом и импортируются как модуль `factories`.
sys.path.insert(0, str(Path(__file__).parent))
