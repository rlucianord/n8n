from __future__ import annotations

from pathlib import Path


class TransformersLocalProvider:
    """CPU-first Transformers adapter that only loads files already on disk."""

    def __init__(self, model_path: str | Path):
        self.model_path = Path(model_path).expanduser().resolve()
        if not self.model_path.is_dir():
            raise FileNotFoundError(
                f'Local model directory does not exist: {self.model_path}'
            )
        self._tokenizer = None
        self._model = None

    def _load(self) -> None:
        if self._model is not None:
            return
        try:
            import torch
            from transformers import AutoModelForCausalLM, AutoTokenizer
        except ImportError as exc:
            raise RuntimeError(
                'Local model dependencies are missing. Install the optional '
                'requirements-local-llm.txt on the target PC.'
            ) from exc

        # local_files_only prevents Hugging Face network downloads.
        self._tokenizer = AutoTokenizer.from_pretrained(
            str(self.model_path),
            local_files_only=True,
            trust_remote_code=False,
        )
        self._model = AutoModelForCausalLM.from_pretrained(
            str(self.model_path),
            local_files_only=True,
            trust_remote_code=False,
            torch_dtype=torch.float32,
        )
        self._model.to('cpu')
        self._model.eval()

    def generate(
        self,
        messages: list[dict[str, str]],
        *,
        max_new_tokens: int = 256,
    ) -> str:
        if not messages:
            raise ValueError('messages cannot be empty.')
        if not 1 <= max_new_tokens <= 1024:
            raise ValueError('max_new_tokens must be between 1 and 1024.')

        self._load()
        import torch

        if hasattr(self._tokenizer, 'apply_chat_template'):
            input_ids = self._tokenizer.apply_chat_template(
                messages,
                add_generation_prompt=True,
                return_tensors='pt',
            )
            attention_mask = torch.ones_like(input_ids)
        else:
            prompt = '\n'.join(
                f"{item['role']}: {item['content']}" for item in messages
            ) + '\nassistant:'
            encoded = self._tokenizer(prompt, return_tensors='pt')
            input_ids = encoded['input_ids']
            attention_mask = encoded.get(
                'attention_mask', torch.ones_like(input_ids)
            )

        with torch.inference_mode():
            output = self._model.generate(
                input_ids=input_ids,
                attention_mask=attention_mask,
                max_new_tokens=max_new_tokens,
                do_sample=False,
                pad_token_id=(
                    self._tokenizer.pad_token_id
                    if self._tokenizer.pad_token_id is not None
                    else self._tokenizer.eos_token_id
                ),
            )

        generated = output[0, input_ids.shape[-1]:]
        answer = self._tokenizer.decode(
            generated, skip_special_tokens=True
        ).strip()
        if not answer:
            raise RuntimeError('The local model returned an empty response.')
        return answer
