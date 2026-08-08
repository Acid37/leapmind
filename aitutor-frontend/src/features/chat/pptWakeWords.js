// Wake-word utilities and question parsing

export const WAKE_WORDS = ["小思老师", "老师", "小思"];

// Check if transcript contains any wake word; can be toggled with enable flag
export function containsWakeWord(transcript, enableWakeWordDetection = true) {
  if (!enableWakeWordDetection) return true;
  const text = String(transcript || "").toLowerCase().trim();
  return WAKE_WORDS.some((w) => text.includes(String(w).toLowerCase()));
}

// Remove wake words from the beginning and return the remaining question text
export function extractQuestionFromTranscript(transcript) {
  let text = String(transcript || "").trim();
  for (const wakeWord of WAKE_WORDS) {
    const patterns = [
      new RegExp(`^${wakeWord}[，,。.！!？?\\s]*`, "i"),
      new RegExp(`^${wakeWord}`, "i"),
    ];
    for (const pattern of patterns) {
      if (pattern.test(text)) {
        text = text.replace(pattern, "").trim();
        break;
      }
    }
  }
  return text;
}

// Heuristic to determine if a transcript is a question
export function isValidQuestion(transcript) {
  const text = String(transcript || "").toLowerCase().trim();
  const questionKeywords = [
    "请问",
    "什么",
    "为什么",
    "怎么",
    "如何",
    "能否",
    "可以",
    "问题",
    "疑问",
    "不懂",
    "不明白",
    "解释",
    "说明",
    "帮助",
    "？",
    "?",
    "吗",
    "呢",
    "啊",
    "吧",
  ];
  const questionPatterns = [
    /^(请问)/,
    /[？?]$/,
    /(什么|为什么|怎么|如何|能否|可以)/,
    /(不懂|不明白|不理解)/,
    /(解释|说明|讲解).*?[吗呢啊吧]?$/,
  ];
  const hasKeyword = questionKeywords.some((k) => text.includes(k));
  const matchesPattern = questionPatterns.some((p) => p.test(text));
  const hasValidLength = text.length >= 2;
  return (hasKeyword || matchesPattern) && hasValidLength;
}


