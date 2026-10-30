from __future__ import annotations

from contextlib import contextmanager
from uuid import uuid4

from sqlcipher3 import dbapi2 as sqlite

from app.database import connection


@contextmanager
def _db():
    with connection() as conn:
        conn.row_factory = sqlite.Row
        yield conn


def _as_dict(row):
    return dict(row) if row is not None else None


def _audit(conn, action: str, decision: str = "allowed", reason: str | None = None) -> None:
    conn.execute(
        "INSERT INTO audit_log (action, decision, reason) VALUES (?, ?, ?)",
        (action, decision, reason),
    )


def get_profile() -> dict:
    with _db() as conn:
        conn.execute("INSERT OR IGNORE INTO profile (id) VALUES (1)")
        conn.commit()
        return _as_dict(conn.execute("SELECT * FROM profile WHERE id = 1").fetchone())


def update_profile(
    display_name: str | None = None,
    language: str | None = None,
) -> dict:
    updates = {}
    if display_name is not None:
        value = display_name.strip()
        if not value:
            raise ValueError("display_name cannot be empty.")
        updates["display_name"] = value
    if language is not None:
        value = language.strip()
        if not value:
            raise ValueError("language cannot be empty.")
        updates["language"] = value
    if not updates:
        return get_profile()

    with _db() as conn:
        conn.execute("INSERT OR IGNORE INTO profile (id) VALUES (1)")
        if "display_name" in updates:
            conn.execute(
                "UPDATE profile SET display_name = ? WHERE id = 1",
                (updates["display_name"],),
            )
        if "language" in updates:
            conn.execute(
                "UPDATE profile SET language = ? WHERE id = 1",
                (updates["language"],),
            )
        _audit(conn, "profile.update")
        conn.commit()
        return _as_dict(conn.execute("SELECT * FROM profile WHERE id = 1").fetchone())


def create_conversation(title: str | None = None) -> dict:
    conversation_id = str(uuid4())
    normalized_title = title.strip() if title and title.strip() else None
    with _db() as conn:
        conn.execute(
            "INSERT INTO conversations (id, title) VALUES (?, ?)",
            (conversation_id, normalized_title),
        )
        _audit(conn, "conversation.create", reason=conversation_id)
        conn.commit()
        return _as_dict(
            conn.execute(
                "SELECT * FROM conversations WHERE id = ?",
                (conversation_id,),
            ).fetchone()
        )


def get_conversation(conversation_id: str) -> dict | None:
    with _db() as conn:
        return _as_dict(
            conn.execute(
                "SELECT * FROM conversations WHERE id = ?",
                (conversation_id,),
            ).fetchone()
        )


def list_conversations(limit: int = 50, offset: int = 0) -> list[dict]:
    if not 1 <= limit <= 500 or offset < 0:
        raise ValueError("Invalid pagination values.")
    with _db() as conn:
        rows = conn.execute(
            """
            SELECT * FROM conversations
            ORDER BY updated_at DESC, rowid DESC
            LIMIT ? OFFSET ?
            """,
            (limit, offset),
        ).fetchall()
        return [_as_dict(row) for row in rows]


def save_message(conversation_id: str, role: str, content: str) -> dict:
    if role not in {"system", "user", "assistant", "tool"}:
        raise ValueError("Invalid message role.")
    if not content or not content.strip():
        raise ValueError("Message content cannot be empty.")

    message_id = str(uuid4())
    with _db() as conn:
        exists = conn.execute(
            "SELECT 1 FROM conversations WHERE id = ?",
            (conversation_id,),
        ).fetchone()
        if not exists:
            raise LookupError("Conversation not found.")

        conn.execute(
            """
            INSERT INTO messages (id, conversation_id, role, content)
            VALUES (?, ?, ?, ?)
            """,
            (message_id, conversation_id, role, content),
        )
        conn.execute(
            "UPDATE conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (conversation_id,),
        )
        _audit(conn, "message.create", reason=conversation_id)
        conn.commit()
        return _as_dict(
            conn.execute(
                "SELECT * FROM messages WHERE id = ?",
                (message_id,),
            ).fetchone()
        )


def get_messages(
    conversation_id: str,
    limit: int = 100,
    offset: int = 0,
) -> list[dict]:
    if not 1 <= limit <= 500 or offset < 0:
        raise ValueError("Invalid pagination values.")
    with _db() as conn:
        rows = conn.execute(
            """
            SELECT * FROM messages
            WHERE conversation_id = ?
            ORDER BY created_at ASC, rowid ASC
            LIMIT ? OFFSET ?
            """,
            (conversation_id, limit, offset),
        ).fetchall()
        return [_as_dict(row) for row in rows]


def save_memory(category: str, content: str, source: str = "user") -> dict:
    category = category.strip()
    source = source.strip()
    if not category or not content or not content.strip() or not source:
        raise ValueError("Category, content and source are required.")

    memory_id = str(uuid4())
    with _db() as conn:
        conn.execute(
            """
            INSERT INTO memories (id, category, content, source)
            VALUES (?, ?, ?, ?)
            """,
            (memory_id, category, content, source),
        )
        _audit(conn, "memory.create", reason=memory_id)
        conn.commit()
        return _as_dict(
            conn.execute(
                "SELECT * FROM memories WHERE id = ?",
                (memory_id,),
            ).fetchone()
        )


def search_memories(
    query: str | None = None,
    category: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> list[dict]:
    if not 1 <= limit <= 500 or offset < 0:
        raise ValueError("Invalid pagination values.")

    conditions = []
    parameters = []
    if query and query.strip():
        conditions.append("instr(lower(content), lower(?)) > 0")
        parameters.append(query.strip())
    if category and category.strip():
        conditions.append("category = ?")
        parameters.append(category.strip())

    sql = "SELECT * FROM memories"
    if conditions:
        sql += " WHERE " + " AND ".join(conditions)
    sql += " ORDER BY updated_at DESC, rowid DESC LIMIT ? OFFSET ?"
    parameters.extend([limit, offset])

    with _db() as conn:
        rows = conn.execute(sql, parameters).fetchall()
        return [_as_dict(row) for row in rows]


def delete_memory(memory_id: str) -> bool:
    with _db() as conn:
        cursor = conn.execute("DELETE FROM memories WHERE id = ?", (memory_id,))
        if cursor.rowcount:
            _audit(conn, "memory.delete", reason=memory_id)
        conn.commit()
        return cursor.rowcount > 0


def log_action(action: str, decision: str, reason: str | None = None) -> int:
    if not action.strip() or not decision.strip():
        raise ValueError("Action and decision are required.")
    with _db() as conn:
        cursor = conn.execute(
            "INSERT INTO audit_log (action, decision, reason) VALUES (?, ?, ?)",
            (action.strip(), decision.strip(), reason),
        )
        conn.commit()
        return int(cursor.lastrowid)


def get_audit_log(limit: int = 100, offset: int = 0) -> list[dict]:
    if not 1 <= limit <= 500 or offset < 0:
        raise ValueError("Invalid pagination values.")
    with _db() as conn:
        rows = conn.execute(
            """
            SELECT * FROM audit_log
            ORDER BY id DESC
            LIMIT ? OFFSET ?
            """,
            (limit, offset),
        ).fetchall()
        return [_as_dict(row) for row in rows]
