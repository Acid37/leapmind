"""对话困惑表达提取测试。"""

import unittest

from landppt.user_profile.confusion import extractConfusionSignals


class ConfusionExtractionTest(unittest.TestCase):
  """验证中文学习困惑模式匹配。"""

  def testExtractsMultipleConfusionPatternsInTextOrder(self):
    """一条消息中的多个困惑表达应按出现顺序返回。"""
    result = extractConfusionSignals(
      "我不懂为什么这里要换元，这个符号是什么意思？",
    )

    self.assertTrue(result.isConfused)
    self.assertEqual(
      ("不懂", "为什么", "什么意思"),
      result.matchedPatterns,
    )

  def testReturnsNotConfusedForOrdinaryMessage(self):
    """没有困惑模式的普通消息不应被误判。"""
    result = extractConfusionSignals("我已经理解这道题了")

    self.assertFalse(result.isConfused)
    self.assertEqual((), result.matchedPatterns)

  def testNormalizesRepeatedWhitespace(self):
    """连续空白应被归一化，便于后续保存证据摘要。"""
    result = extractConfusionSignals("  为什么   要这样做？ ")

    self.assertEqual("为什么 要这样做？", result.normalizedText)
    self.assertEqual(("为什么",), result.matchedPatterns)

  def testRejectsBlankConversationText(self):
    """空白对话不能作为困惑证据。"""
    with self.assertRaisesRegex(ValueError, "对话文本不能为空"):
      extractConfusionSignals("   ")


if __name__ == "__main__":
  unittest.main()
