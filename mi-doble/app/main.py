from fastapi import FastAPI

from app.config import validate_config
from app.database import connection, initialize_database

validate_config()
initialize_database()

app = FastAPI(
    title="Mi Doble Local API",
    version="0.1.0",
    description="Local-first companion service. No hosted AI provider is configured.",
)

@app.get("/health")
def health():
    with connection() as conn:
        cipher = conn.execute("PRAGMA cipher_version").fetchone()[0]
    return {"status": "ok", "database": "encrypted", "cipher_version": cipher}
