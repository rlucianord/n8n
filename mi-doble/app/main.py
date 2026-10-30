from __future__ import annotations

from typing import Literal

from fastapi import FastAPI, HTTPException, Query, Response, status
from pydantic import BaseModel, ConfigDict, Field

from app.config import validate_config
from app.database import connection, initialize_database
from app.services import memory

validate_config()
initialize_database()

app = FastAPI(
    title="Mi Doble Local API",
    version="0.2.0",
    description=(
        "Local-first companion service. Personal data is stored in an "
        "SQLCipher-encrypted database. No hosted AI provider is configured."
    ),
)


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ProfileUpdate(StrictModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=120)
    language: str | None = Field(default=None, min_length=2, max_length=20)


class ConversationCreate(StrictModel):
    title: str | None = Field(default=None, max_length=300)


class MessageCreate(StrictModel):
    role: Literal["system", "user", "assistant", "tool"]
    content: str = Field(min_length=1, max_length=50000)


class MemoryCreate(StrictModel):
    category: str = Field(min_length=1, max_length=80)
    content: str = Field(min_length=1, max_length=20000)
    source: str = Field(default="user", min_length=1, max_length=40)


@app.get("/health", tags=["system"])
def health():
    with connection() as conn:
        cipher_row = conn.execute("PRAGMA cipher_version").fetchone()
    if not cipher_row or not cipher_row[0]:
        raise HTTPException(status_code=503, detail="SQLCipher is unavailable.")
    return {
        "status": "ok",
        "database": "encrypted",
        "cipher_version": cipher_row[0],
    }


@app.get("/profile", tags=["profile"])
def read_profile():
    return memory.get_profile()


@app.put("/profile", tags=["profile"])
def write_profile(payload: ProfileUpdate):
    try:
        return memory.update_profile(**payload.model_dump(exclude_unset=True))
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.post(
    "/conversations",
    status_code=status.HTTP_201_CREATED,
    tags=["conversations"],
)
def create_conversation(payload: ConversationCreate):
    return memory.create_conversation(payload.title)


@app.get("/conversations", tags=["conversations"])
def read_conversations(
    limit: int = Query(default=50, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
):
    return memory.list_conversations(limit=limit, offset=offset)


@app.get("/conversations/{conversation_id}", tags=["conversations"])
def read_conversation(conversation_id: str):
    conversation = memory.get_conversation(conversation_id)
    if conversation is None:
        raise HTTPException(status_code=404, detail="Conversation not found.")
    return conversation


@app.post(
    "/conversations/{conversation_id}/messages",
    status_code=status.HTTP_201_CREATED,
    tags=["messages"],
)
def create_message(conversation_id: str, payload: MessageCreate):
    try:
        return memory.save_message(
            conversation_id=conversation_id,
            role=payload.role,
            content=payload.content,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.get("/conversations/{conversation_id}/messages", tags=["messages"])
def read_messages(
    conversation_id: str,
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
):
    if memory.get_conversation(conversation_id) is None:
        raise HTTPException(status_code=404, detail="Conversation not found.")
    return memory.get_messages(
        conversation_id=conversation_id,
        limit=limit,
        offset=offset,
    )


@app.post(
    "/memories",
    status_code=status.HTTP_201_CREATED,
    tags=["memories"],
)
def create_memory(payload: MemoryCreate):
    try:
        return memory.save_memory(
            category=payload.category,
            content=payload.content,
            source=payload.source,
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.get("/memories", tags=["memories"])
def read_memories(
    q: str | None = Query(default=None, max_length=500),
    category: str | None = Query(default=None, max_length=80),
    limit: int = Query(default=50, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
):
    return memory.search_memories(
        query=q,
        category=category,
        limit=limit,
        offset=offset,
    )


@app.delete("/memories/{memory_id}", status_code=status.HTTP_204_NO_CONTENT, tags=["memories"])
def remove_memory(memory_id: str):
    if not memory.delete_memory(memory_id):
        raise HTTPException(status_code=404, detail="Memory not found.")
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@app.get("/audit", tags=["audit"])
def read_audit_log(
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
):
    return memory.get_audit_log(limit=limit, offset=offset)
