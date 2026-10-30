from __future__ import annotations

from typing import Literal

from fastapi import FastAPI, HTTPException, Query, Response, status
from pydantic import BaseModel, ConfigDict, Field

from app.config import validate_config
from app.database import connection, initialize_database
from app.services import context as context_service
from app.services import memory
from app.services import agency

validate_config()
initialize_database()
agency.initialize_agency()

app = FastAPI(
    title="Mi Doble Local API",
    version="0.4.0",
    description=(
        "Local-first companion service with encrypted memory and user-controlled "
        "agency policies. External communication connectors are enabled separately."
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


class ContextRequest(StrictModel):
    current_message: str = Field(min_length=1, max_length=20000)
    message_limit: int = Field(default=20, ge=1, le=100)
    memory_limit: int = Field(default=8, ge=0, le=30)


class AgencySettingsUpdate(StrictModel):
    values: dict[str, str | bool] = Field(min_length=1)


class AgencyPolicyUpdate(StrictModel):
    enabled: bool
    requires_confirmation: bool = True


class AgencyDecisionRequest(StrictModel):
    action: str = Field(min_length=1, max_length=80)
    user_confirmed: bool = False


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
        "agency": "configured",
        "external_connectors": "not_connected",
    }


@app.get("/agency/settings", tags=["agency"])
def get_agency_settings():
    return agency.get_settings()


@app.put("/agency/settings", tags=["agency"])
def put_agency_settings(payload: AgencySettingsUpdate):
    try:
        return agency.update_settings(payload.values)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.get("/agency/policies", tags=["agency"])
def get_agency_policies():
    return agency.get_policies()


@app.put("/agency/policies/{action:path}", tags=["agency"])
def put_agency_policy(action: str, payload: AgencyPolicyUpdate):
    try:
        return agency.update_policy(
            action, payload.enabled, payload.requires_confirmation
        )
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.post("/agency/decisions/evaluate", tags=["agency"])
def evaluate_agency_decision(payload: AgencyDecisionRequest):
    return agency.evaluate_action(payload.action, payload.user_confirmed)


@app.get("/agency/model-catalog", tags=["agency"])
def get_model_catalog():
    return {
        "active_model": agency.get_settings().get("active_model", "Qwen3"),
        "models": [
            {"name": "Phi-4", "available": False, "status": "catalog_only"},
            {"name": "Qwen3", "available": True, "status": "current_android_candidate"},
            {"name": "Llama 3.2", "available": False, "status": "catalog_only"},
            {"name": "Gemma 3", "available": False, "status": "catalog_only"},
        ],
        "note": "Catalog entries do not imply that model files or Android loaders are installed.",
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


@app.post("/conversations/{conversation_id}/context", tags=["context"])
def create_context(conversation_id: str, payload: ContextRequest):
    try:
        return context_service.build_context(
            conversation_id=conversation_id,
            current_message=payload.current_message,
            message_limit=payload.message_limit,
            memory_limit=payload.memory_limit,
        )
    except LookupError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
