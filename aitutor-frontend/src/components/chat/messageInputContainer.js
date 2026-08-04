import { MessageInput } from "@/components/messageInput";
import { useState, useEffect, useCallback } from "react";

/**
 * 提供文本输入和语音输入功能
 *
 * 语音识别完成时自动发送，在生成回复时禁用输入
 */
export const MessageInputContainer = ({
  isChatProcessing,
  onChatProcessStart,
}) => {
  const [userMessage, setUserMessage] = useState("");
  const [speechRecognition, setSpeechRecognition] = useState();
  const [isMicRecording, setIsMicRecording] = useState(false);

  // 处理语音识别结果
  const handleRecognitionResult = useCallback(
    (event) => {
      const text = event.results[0][0].transcript;
      setUserMessage(text);

      // 说话结束时
      if (event.results[0].isFinal) {
        setUserMessage(text);
        // 开始生成回复文本
        onChatProcessStart(text);
      }
    },
    [onChatProcessStart]
  );

  // 持续静音时也结束
  const handleRecognitionEnd = useCallback(() => {
    setIsMicRecording(false);
  }, []);

  const handleClickMicButton = useCallback(() => {
    if (isMicRecording) {
      if (speechRecognition && typeof speechRecognition.abort === "function") {
        speechRecognition.abort();
      }
      setIsMicRecording(false);

      return;
    }
//保护判断
    if (speechRecognition && typeof speechRecognition.start === "function") {
      speechRecognition.start();
      setIsMicRecording(true);
    }
  }, [isMicRecording, speechRecognition]);

  const handleClickSendButton = useCallback(() => {
    onChatProcessStart(userMessage);
  }, [onChatProcessStart, userMessage]);

  useEffect(() => {
    const SpeechRecognitionCtor =
      window.webkitSpeechRecognition || window.SpeechRecognition;

    // 非支持环境（如 Firefox）
    if (!SpeechRecognitionCtor) {
      return;
    }
    const recognition = new SpeechRecognitionCtor();
    recognition.lang = "zh-CN"; // 中文简体
    recognition.interimResults = true; // 返回识别的中间结果
    recognition.continuous = false; // 说话结束时结束识别

    recognition.addEventListener("result", handleRecognitionResult);
    recognition.addEventListener("end", handleRecognitionEnd);

    setSpeechRecognition(recognition);

    return () => {
      recognition.removeEventListener("result", handleRecognitionResult);
      recognition.removeEventListener("end", handleRecognitionEnd);
      try {
        if (typeof recognition.abort === "function") {
          recognition.abort();
        }
      } catch {}
    };
  }, [handleRecognitionResult, handleRecognitionEnd]);

  useEffect(() => {
    if (!isChatProcessing) {
      setUserMessage("");
    }
  }, [isChatProcessing]);

  return (
    <MessageInput
      userMessage={userMessage}
      isChatProcessing={isChatProcessing}
      isMicRecording={isMicRecording}
      onChangeUserMessage={(e) => setUserMessage(e.target.value)}
      onClickMicButton={handleClickMicButton}
      onClickSendButton={handleClickSendButton}
    />
  );
};


