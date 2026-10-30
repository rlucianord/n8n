from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def _run_provider_check(tmp_path: Path, snippet: str) -> None:
    env = os.environ.copy()
    env['MIDOBLE_TEST_MODEL_DIR'] = str(tmp_path / 'placeholder-model')
    (tmp_path / 'placeholder-model').mkdir()
    root = Path(__file__).resolve().parents[1]
    subprocess.run(
        [sys.executable, '-c', snippet],
        cwd=root,
        env=env,
        check=True,
    )


def test_local_provider_rejects_missing_model_directory(tmp_path: Path):
    snippet = r'''
from app.services.llm.transformers_local import TransformersLocalProvider
from pathlib import Path
import os
missing = Path(os.environ['MIDOBLE_TEST_MODEL_DIR']) / 'missing'
try:
    TransformersLocalProvider(missing)
except FileNotFoundError as exc:
    assert 'Local model directory' in str(exc)
else:
    raise AssertionError('Expected FileNotFoundError')
'''
    # The helper creates the placeholder; this check targets a separate path.
    _run_provider_check(tmp_path, snippet)


def test_local_provider_validates_generation_limits_without_loading_model(tmp_path: Path):
    snippet = r'''
import os
from app.services.llm.transformers_local import TransformersLocalProvider
provider = TransformersLocalProvider(os.environ['MIDOBLE_TEST_MODEL_DIR'])
try:
    provider.generate([])
except ValueError as exc:
    assert 'messages cannot be empty' in str(exc)
else:
    raise AssertionError('Expected empty-message validation')
try:
    provider.generate([{'role': 'user', 'content': 'Hola'}], max_new_tokens=0)
except ValueError as exc:
    assert 'max_new_tokens' in str(exc)
else:
    raise AssertionError('Expected token-limit validation')
'''
    _run_provider_check(tmp_path, snippet)


def test_local_provider_rejects_unsupported_message_roles(tmp_path: Path):
    snippet = r'''
import os
from app.services.llm.transformers_local import TransformersLocalProvider
provider = TransformersLocalProvider(os.environ['MIDOBLE_TEST_MODEL_DIR'])
try:
    provider.generate([{'role': 'tool', 'content': 'not allowed'}])
except ValueError as exc:
    assert 'supported role' in str(exc)
else:
    raise AssertionError('Expected role validation')
'''
    _run_provider_check(tmp_path, snippet)
