from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

def test_sqlcipher_database_is_not_plain_sqlite(tmp_path: Path):
    env = os.environ.copy()
    env["MIDOBLE_DB_KEY"] = "a" * 64
    env["MIDOBLE_DB_PATH"] = str(tmp_path / "test.db")
    env["MIDOBLE_HOST"] = "127.0.0.1"
    code = """
from app.database import initialize_database, DB_PATH
initialize_database()
print(DB_PATH)
"""
    root = Path(__file__).resolve().parents[1]
    subprocess.run([sys.executable, "-c", code], cwd=root, env=env, check=True)
    header = (tmp_path / "test.db").read_bytes()[:16]
    assert header != b"SQLite format 3\x00"
