from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parents[1]
load_dotenv(BASE_DIR / ".env")

DB_KEY = os.getenv("MIDOBLE_DB_KEY", "").strip()
DB_PATH = Path(os.getenv("MIDOBLE_DB_PATH", str(BASE_DIR / "data" / "mi_doble.db"))).expanduser()
HOST = os.getenv("MIDOBLE_HOST", "127.0.0.1")
PORT = int(os.getenv("MIDOBLE_PORT", "8765"))

def validate_config() -> None:
    if HOST not in {"127.0.0.1", "localhost", "::1"}:
        raise RuntimeError("Mi Doble bootstrap only permits loopback binding.")
    if len(DB_KEY) < 32:
        raise RuntimeError("MIDOBLE_DB_KEY is missing or too short; refusing to start.")
