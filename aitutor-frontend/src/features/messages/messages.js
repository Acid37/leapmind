// Converted from messages.ts to JavaScript
// Removed TypeScript types; logic remains the same.

// Note: Type definitions are provided in the adjacent messages.d.ts for TS users.

// ChatGPT API message shape (see messages.d.ts for types)
// export type Message = { role: 'assistant' | 'system' | 'user'; content: string }

const talkStyles = ["talk", "happy", "sad", "angry", "fear", "surprised"];

const emotions = ["neutral", "happy", "angry", "sad", "relaxed"];

export const splitSentence = (text) => {
  const splitMessages = text.split(/(?<=[。．！？\n])/g);
  return splitMessages.filter((msg) => msg !== "");
};

export const textsToScreenplay = (texts, koeiroParam) => {
  const screenplays = [];
  let prevExpression = "neutral";
  for (let i = 0; i < texts.length; i++) {
    const text = texts[i];

    const match = text.match(/\[(.*?)\]/);

    const tag = (match && match[1]) || prevExpression;

    const message = text.replace(/\[(.*?)\]/g, "");

    let expression = prevExpression;
    if (emotions.includes(tag)) {
      expression = tag;
      prevExpression = tag;
    }

    screenplays.push({
      expression,
      talk: {
        style: emotionToTalkStyle(expression),
        speakerX: koeiroParam.speakerX,
        speakerY: koeiroParam.speakerY,
        message: message,
      },
    });
  }

  return screenplays;
};

const emotionToTalkStyle = (emotion) => {
  switch (emotion) {
    case "angry":
      return "angry";
    case "happy":
      return "happy";
    case "sad":
      return "sad";
    default:
      return "talk";
  }
};