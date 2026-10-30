from __future__ import annotations

from contextlib import contextmanager

from sqlcipher3 import dbapi2 as sqlite
from app.config import DB_KEY, DB_PATH

@contextmanager
def connection():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite.connect(str(DB_PATH))
    try:
        # SQLCipher expects the key to be set immediately after opening.
        escaped = DB_KEY.replace("'", "''")
        conn.execute(f"PRAGMA key = '{escaped}'")
        version = conn.execute("PRAGMA cipher_version").fetchone()
        if not version or not version[0]:
            raise RuntimeError("SQLCipher is unavailable; refusing to use an unencrypted database.")
        conn.execute("PRAGMA foreign_keys = ON")
        yield conn
    finally:
        conn.close()

def initialize_database() -> None:
    from pathlib import Path
    schema = Path(__file__).resolve().parents[1] / "database" / "schema.sql"
    with connection() as conn:
        conn.executescript(schema.read_text(encoding="utf-8"))
        conn.commit()
        result = conn.execute("PRAGMA integrity_check").fetchone()
        if not result or result[0] != "ok":
            raise RuntimeError("Database integrity check failed.")
