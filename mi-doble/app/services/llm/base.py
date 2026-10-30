from __future__ import annotations

from typing import Protocol


class LanguageModelProvider(Protocol):
    def generate(
        self,
        messages: list[dict[str, str]],
        *,
        max_new_tokens: int = 256,
    ) -> str:
        """Generate a response from role/content messages."""
        ...
