from __future__ import annotations

from pathlib import Path

import pytest

from app.services.llm.transformers_local import TransformersLocalProvider


def test_local_provider_rejects_missing_model_directory(tmp_path: Path):
    missing = tmp_path / 'model-not-installed'
    with pytest.raises(FileNotFoundError, match='Local model directory'):
        TransformersLocalProvider(missing)


def test_local_provider_validates_generation_limits_without_loading_model(tmp_path: Path):
    model_dir = tmp_path / 'placeholder-model'
    model_dir.mkdir()
    provider = TransformersLocalProvider(model_dir)

    with pytest.raises(ValueError, match='messages cannot be empty'):
        provider.generate([])

    with pytest.raises(ValueError, match='max_new_tokens'):
        provider.generate([{'role': 'user', 'content': 'Hola'}], max_new_tokens=0)


def test_local_provider_rejects_unsupported_message_roles(tmp_path: Path):
    model_dir = tmp_path / 'placeholder-model'
    model_dir.mkdir()
    provider = TransformersLocalProvider(model_dir)

    with pytest.raises(ValueError, match='supported role'):
        provider.generate([{'role': 'tool', 'content': 'not allowed'}])
