import { wait } from "@/utils/wait.js";
import { synthesizeVoiceApi } from "./synthesizeVoice.js";

const createSpeakCharacter = () => {
  let lastTime = 0;
  let prevFetchPromise = Promise.resolve();
  let prevSpeakPromise = Promise.resolve();

  return (screenplay, viewer, koeiroApiKey, onStart, onComplete) => {
    const fetchPromise = prevFetchPromise.then(async () => {
      const now = Date.now();
      if (now - lastTime < 1000) {
        await wait(1000 - (now - lastTime));
      }

      const buffer = await fetchAudio(screenplay.talk, koeiroApiKey).catch(
        () => null
      );
      lastTime = Date.now();
      return buffer;
    });

    prevFetchPromise = fetchPromise;
    prevSpeakPromise = Promise.all([fetchPromise, prevSpeakPromise]).then(
      ([audioBuffer]) => {
        onStart && onStart();
        if (!audioBuffer) {
          return;
        }
        return viewer.model?.speak(audioBuffer, screenplay);
      }
    );
    prevSpeakPromise.then(() => {
      onComplete && onComplete();
    });
  };
};

export const speakCharacter = createSpeakCharacter();

export const fetchAudio = async (talk, apiKey) => {
  const ttsVoice = await synthesizeVoiceApi(
    talk.message,
    talk.speakerX,
    talk.speakerY,
    talk.style,
    apiKey
  );
  const audioData = ttsVoice.audio;

  if (audioData == null) {
    throw new Error("Something went wrong");
  }

  // 判断是URL还是base64数据
  if (audioData.startsWith("http") || audioData.startsWith("blob:")) {
    // 如果是URL，使用原来的逻辑
    const resAudio = await fetch(audioData);
    const buffer = await resAudio.arrayBuffer();
    return buffer;
  } else {
    // 如果是base64数据（火山引擎返回格式），直接解码
    const binaryString = atob(audioData);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
  }
};