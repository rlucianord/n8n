from __future__ import annotations

from pathlib import Path


class LlamaCppLocalProvider:
    """Offline GGUF inference adapter backed by llama.cpp.

    This adapter is suitable for Codespace experiments. The Android app will
    use llama.cpp through its native Android/JNI runtime, not this Python
    wrapper.
    """

    def __init__(
        self,
        model_path: str | Path,
        *,
        context_size: int = 4096,
        threads: int = 4,
    ):
        self.model_path = Path(model_path).expanduser().resolve()
        if not self.model_path.is_file():
            raise FileNotFoundError(
                f"Local GGUF model file does not exist: {self.model_path}"
            )
        if not 512 <= context_size <= 32768:
            raise ValueError("context_size must be between 512 and 32768.")
        if not 1 <= threads <= 64:
            raise ValueError("threads must be between 1 and 64.")

        self.context_size = context_size
        self.threads = threads
        self._model = None

    def _load(self) -> None:
        if self._model is not None:
            return
        try:
            from llama_cpp import Llama
        except ImportError as exc:
            raise RuntimeError(
                "llama-cpp-python is not installed. Install the optional "
                "local GGUF runtime requirements in a compatible environment."
            ) from exc

        # CPU-only, local file only. No network endpoint or hosted fallback.
        self._model = Llama(
            model_path=str(self.model_path),
            n_ctx=self.context_size,
            n_threads=self.threads,
            n_gpu_layers=0,
            verbose=False,
        )

    def generate(
        self,
        messages: list[dict[str, str]],
        *,
        max_new_tokens: int = 256,
    ) -> str:
        if not messages:
            raise ValueError("messages cannot be empty.")
        if any(
            item.get("role") not in {"system", "user", "assistant"}
            or not isinstance(item.get("content"), str)
            for item in messages
        ):
            raise ValueError(
                "Each message needs a supported role and text content."
            )
        if not 1 <= max_new_tokens <= 1024:
            raise ValueError("max_new_tokens must be between 1 and 1024.")

        self._load()
        result = self._model.create_chat_completion(
            messages=messages,
            max_tokens=max_new_tokens,
            temperature=0.2,
        )
        answer = result["choices"][0]["message"]["content"]
        if not isinstance(answer, str) or not answer.strip():
            raise RuntimeError("The local GGUF model returned an empty response.")
        return answer.strip()
