"""对话困惑表达规则提取。"""

import re
from dataclasses import dataclass

CONFUSION_ALGORITHM_VERSION = "confusion-pattern-v1"
CONFUSION_PATTERNS = (
  ("为什么", re.compile(r"为什么")),
  ("不懂", re.compile(r"不懂|不明白|没听懂|看不懂")),
  ("什么意思", re.compile(r"什么意思|什么含义")),
  ("不会", re.compile(r"不会|不知道怎么|怎么理解")),
)


@dataclass(frozen=True)
class ConfusionExtraction:
  """单条对话的可解释困惑提取结果。"""

  isConfused: bool
  matchedPatterns: tuple[str, ...]
  normalizedText: str


def extractConfusionSignals(text: str) -> ConfusionExtraction:
  """匹配中文显式困惑表达，并返回命中模式。"""
  if not isinstance(text, str) or not text.strip():
    raise ValueError("对话文本不能为空")

  normalizedText = re.sub(r"\s+", " ", text.strip())
  matches = []
  for label, pattern in CONFUSION_PATTERNS:
    match = pattern.search(normalizedText)
    if match:
      matches.append((match.start(), label))

  matches.sort(key=lambda item: item[0])
  matchedPatterns = tuple(label for _, label in matches)
  return ConfusionExtraction(
    isConfused=bool(matchedPatterns),
    matchedPatterns=matchedPatterns,
    normalizedText=normalizedText,
  )
