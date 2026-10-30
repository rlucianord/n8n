from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def _run_check(tmp_path: Path, snippet: str, *, create_model: bool = True) -> None:
    model_path = tmp_path / "placeholder.gguf"
    if create_model:
        model_path.write_bytes(b"test placeholder; no model is loaded")
    env = os.environ.copy()
    env["MIDOBLE_TEST_GGUF_PATH"] = str(model_path)
    root = Path(__file__).resolve().parents[1]
    subprocess.run(
        [sys.executable, "-c", snippet],
        cwd=root,
        env=env,
        check=True,
    )


def test_gguf_provider_rejects_missing_model_file(tmp_path: Path):
    snippet = r'''
import os
from app.services.llm.llama_cpp_local import LlamaCppLocalProvider
try:
    LlamaCppLocalProvider(os.environ["MIDOBLE_TEST_GGUF_PATH"])
except FileNotFoundError as exc:
    assert "Local GGUF model file" in str(exc)
else:
    raise AssertionError("Expected FileNotFoundError")
'''
    _run_check(tmp_path, snippet, create_model=False)


def test_gguf_provider_validates_configuration_and_messages(tmp_path: Path):
    snippet = r'''
import os
from app.services.llm.llama_cpp_local import LlamaCppLocalProvider
path = os.environ["MIDOBLE_TEST_GGUF_PATH"]
try:
    LlamaCppLocalProvider(path, context_size=128)
except ValueError as exc:
    assert "context_size" in str(exc)
else:
    raise AssertionError("Expected context-size validation")

provider = LlamaCppLocalProvider(path)
for messages, tokens, expected in [
    ([], 10, "messages cannot be empty"),
    ([{"role": "tool", "content": "x"}], 10, "supported role"),
    ([{"role": "user", "content": "x"}], 0, "max_new_tokens"),
]:
    try:
        provider.generate(messages, max_new_tokens=tokens)
    except ValueError as exc:
        assert expected in str(exc)
    else:
        raise AssertionError(f"Expected validation: {expected}")
'''
    _run_check(tmp_path, snippet)
