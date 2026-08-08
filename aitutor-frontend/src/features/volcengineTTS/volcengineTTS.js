// 从 TypeScript 转换为 JavaScript 版本。移除了类型注解与接口定义，保留原有逻辑与导出。

export async function volcengineTTS(params) {
  const {
    text,
    voice_type = "BV001_streaming", // 默认音色
    emotion = "neutral",
    speed = 1.0,
    volume = 1.0,
    pitch = 1.0,
  } = params;

  // 火山引擎TTS请求体 - 正确的API格式
  const body = {
    app: {
      appid: process.env.VOLCENGINE_APP_ID,
      token: process.env.VOLCENGINE_ACCESS_TOKEN,
      cluster: process.env.VOLCENGINE_CLUSTER || "volcano_tts",
    },
    user: {
      uid: "user_001",
    },
    audio: {
      voice_type: voice_type,
      encoding: "mp3",
      speed_ratio: speed,
      loudness_ratio: volume, // 火山引擎使用loudness_ratio
      emotion: emotion,
    },
    request: {
      reqid: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`, // 确保唯一性
      text: text,
      text_type: "plain",
      operation: "query",
    },
  };

  try {
    console.log("火山引擎TTS请求体:", JSON.stringify(body, null, 2));

    // 火山引擎官方认证方式：Bearer;${token} (注意使用分号分隔)
    const response = await fetch("https://openspeech.bytedance.com/api/v1/tts", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer;${process.env.VOLCENGINE_ACCESS_TOKEN}`,
      },
      body: JSON.stringify(body),
    });

    const data = await response.json();

    console.log("火山引擎TTS响应:", data);

    if (data.code === 3000 && data.data) {
      // 火山引擎成功码是3000
      return {
        audio: data.data, // base64编码的音频数据
        success: true,
      };
    } else {
      throw new Error(data.message || "TTS转换失败");
    }
  } catch (error) {
    console.error("火山引擎TTS错误:", error);
    return {
      audio: null,
      success: false,
      error: error instanceof Error ? error.message : "未知错误",
    };
  }
}

// 映射原有的风格到火山引擎的情感参数
export function mapStyleToEmotion(style) {
  const styleMap = {
    talk: "neutral",
    happy: "happy",
    sad: "sad",
    angry: "angry",
    fear: "fearful",
    surprised: "neutral", // 火山引擎可能没有对应的surprised情感，使用neutral
  };
  return styleMap[style] || "neutral";
}

// 兼容原有接口的包装函数
export async function volcengineCompatibleTTS(message, speakerX, speakerY, style) {
  // 根据speakerX选择不同中文音色（使用基础免费音色）
  const voiceTypes = [
    "BV001_streaming", // 基础女声
    "BV002_streaming", // 基础男声
    "BV404_streaming", // 基础女声2
    "BV405_streaming", // 基础男声2
  ];

  const voice_type = voiceTypes[Math.floor(speakerX * voiceTypes.length)] || voiceTypes[0];

  // 根据speakerY调整语速 (0.5-2.0)
  const speed = 0.5 + speakerY * 1.5;

  const emotion = mapStyleToEmotion(style);

  return await volcengineTTS({
    text: message,
    voice_type,
    emotion,
    speed,
  });
}