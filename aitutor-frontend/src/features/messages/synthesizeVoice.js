export async function synthesizeVoice(message, speakerX, speakerY, style) {
  // 统一使用API路由调用火山引擎TTS
  return await synthesizeVoiceApi(message, speakerX, speakerY, style, "");
}

export async function synthesizeVoiceApi(message, speakerX, speakerY, style, apiKey) {
  const body = {
    message: message,
    speakerX: speakerX,
    speakerY: speakerY,
    style: style, // 火山引擎支持更多情感，不需要限制
  };

  const res = await fetch("/api/tts", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  
  const data = await res.json();
  
  if (data.error) {
    throw new Error(data.error);
  }

  return { audio: data.audio };
}