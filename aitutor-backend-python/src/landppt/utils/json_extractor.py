"""
Robust JSON extractor for AI responses.

Handles various edge cases from LLM outputs:
- Markdown code blocks (```json ... ```)
- Leading/trailing explanatory text
- Truncated JSON (attempts best-effort parsing)
- Nested JSON within text
- Multiple JSON objects (extracts the first valid one)
"""
import json
import re
import logging
from typing import Any, Optional

logger = logging.getLogger(__name__)


class JSONExtractionError(ValueError):
    """Raised when JSON extraction fails after all strategies."""
    pass


class JSONExtractor:
    """Multi-strategy JSON extractor from unstructured text."""

    @staticmethod
    def extract(text: str, fallback: Optional[Any] = None) -> Any:
        """Extract a JSON value from text using multiple strategies.

        Args:
            text: Raw text potentially containing JSON.
            fallback: Value to return if all strategies fail.

        Returns:
            Parsed JSON value, or fallback if extraction fails.

        Raises:
            JSONExtractionError: If fallback is None and all strategies fail.
        """
        if not text or not text.strip():
            return JSONExtractor._handle_failure("Empty input", fallback)

        strategies = [
            JSONExtractor._extract_code_block,
            JSONExtractor._extract_first_json,
            JSONExtractor._extract_loose_json,
            JSONExtractor._repair_and_parse,
        ]

        last_error = ""
        for strategy in strategies:
            try:
                result = strategy(text.strip())
                if result is not None:
                    return result
            except json.JSONDecodeError as e:
                last_error = str(e)
                continue
            except Exception as e:
                last_error = str(e)
                continue

        return JSONExtractor._handle_failure(last_error, fallback)

    @staticmethod
    def extract_dict(text: str, fallback: Optional[dict] = None) -> dict:
        """Extract a JSON object (dict) from text."""
        result = JSONExtractor.extract(text, fallback=None)
        if isinstance(result, dict):
            return result
        if fallback is not None:
            return fallback
        raise JSONExtractionError(
            f"Extracted value is not a dict, got {type(result).__name__}"
        )

    @staticmethod
    def _extract_code_block(text: str) -> Optional[Any]:
        """Strategy 1: Extract content from ```json ... ``` block."""
        # Match ```json ... ``` with optional language identifier
        match = re.search(
            r'```(?:json)?\s*\n?(.*?)\n?```',
            text,
            re.DOTALL,
        )
        if match:
            candidate = match.group(1).strip()
            if candidate:
                return json.loads(candidate)
        # Also try single backtick blocks
        match = re.search(
            r'`{3,}\s*\n?(.*?)\n?`{3,}',
            text,
            re.DOTALL,
        )
        if match:
            candidate = match.group(1).strip()
            if candidate:
                return json.loads(candidate)
        return None

    @staticmethod
    def _extract_first_json(text: str) -> Optional[Any]:
        """Strategy 2: Find the first complete JSON object or array."""
        # 按最早出现的 '{' 或 '[' 作为起点（不能固定优先 '{'：
        # 若 AI 输出 JSON 数组，text.find('{') 会命中数组内第一个对象，
        # 只提取出单个对象而丢掉整个数组）
        starts = []
        idx = text.find("{")
        if idx != -1:
            starts.append((idx, "{", "}"))
        idx = text.find("[")
        if idx != -1:
            starts.append((idx, "[", "]"))
        starts.sort(key=lambda x: x[0])

        for start_idx, start_char, end_char in starts:
            depth = 0
            for i in range(start_idx, len(text)):
                ch = text[i]
                if ch == start_char:
                    depth += 1
                elif ch == end_char:
                    depth -= 1
                    if depth == 0:
                        candidate = text[start_idx : i + 1]
                        try:
                            return json.loads(candidate)
                        except json.JSONDecodeError:
                            # 该起点不是完整JSON，尝试下一个起点
                            break
        return None

    @staticmethod
    def _extract_loose_json(text: str) -> Optional[Any]:
        """Strategy 3: Find any substring that parses as JSON."""
        # Find all { } blocks and try each one
        candidates = []
        start = 0
        while True:
            start_idx = text.find("{", start)
            if start_idx == -1:
                break
            depth = 0
            for i in range(start_idx, len(text)):
                if text[i] == "{":
                    depth += 1
                elif text[i] == "}":
                    depth -= 1
                    if depth == 0:
                        candidates.append(text[start_idx : i + 1])
                        start = i + 1
                        break
            else:
                # Unmatched opening brace — try with len(text) as boundary
                candidates.append(text[start_idx:])
                break

        # Try shortest first — more likely to be clean JSON
        candidates.sort(key=len)
        for candidate in candidates:
            try:
                return json.loads(candidate)
            except json.JSONDecodeError:
                continue

        # Same for arrays
        start = 0
        while True:
            start_idx = text.find("[", start)
            if start_idx == -1:
                break
            depth = 0
            for i in range(start_idx, len(text)):
                if text[i] == "[":
                    depth += 1
                elif text[i] == "]":
                    depth -= 1
                    if depth == 0:
                        candidate = text[start_idx : i + 1]
                        try:
                            return json.loads(candidate)
                        except json.JSONDecodeError:
                            start = i + 1
                            break
            else:
                break

        return None

    @staticmethod
    def _repair_and_parse(text: str) -> Optional[Any]:
        """Strategy 4: Attempt to repair common JSON issues and parse."""
        repairs = [
            # Remove trailing commas
            lambda s: re.sub(r",\s*([}\]])", r"\1", s),
            # Replace single quotes with double quotes (but not inside double-quoted strings)
            lambda s: re.sub(r"(?<!\\)'", '"', s),
            # Remove unescaped newlines inside strings
            lambda s: re.sub(r'(?<="[^"]*)\n(?=[^"]*")', "\\n", s),
            # Wrap bare keys in quotes (keys without quotes)
            lambda s: re.sub(
                r"(?<![\\\"])(\b[a-zA-Z_][a-zA-Z0-9_]*\b)\s*:",
                r'"\1":',
                s,
            ),
        ]

        for repair in repairs:
            try:
                repaired = repair(text)
                # Try strategies 2 and 3 on repaired text
                result = JSONExtractor._extract_first_json(repaired)
                if result is not None:
                    return result
                result = JSONExtractor._extract_loose_json(repaired)
                if result is not None:
                    return result
            except Exception:
                continue

        return None

    @staticmethod
    def _handle_failure(error_msg: str, fallback: Optional[Any]) -> Any:
        """Handle extraction failure based on config."""
        logger.warning(f"JSON extraction failed: {error_msg}")
        if fallback is not None:
            return fallback
        raise JSONExtractionError(
            f"Failed to extract JSON from text after all strategies: {error_msg}"
        )
