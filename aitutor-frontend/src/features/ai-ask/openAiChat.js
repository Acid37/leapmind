// OpenAI Chat 调用 - 纯 JS 版本

export async function getChatResponse(messages, apiKey) {
  if (!apiKey) {
    throw new Error("Invalid API Key");
  }

  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${apiKey}`,
  };

  const res = await fetch("https://api.openai.com/v1/chat/completions", {
    headers,
    method: "POST",
    body: JSON.stringify({
      model: "gpt-3.5-turbo",
      messages,
    }),
  });

  if (!res.ok) {
    throw new Error("OpenAI API error");
  }

  const data = await res.json();
  const [aiRes] = data.choices;
  const message = (aiRes && aiRes.message && aiRes.message.content) || "エラーが発生しました";
  return { message };
}

export async function getChatResponseStream(messages, apiKey) {
  if (!apiKey) {
    throw new Error("Invalid API Key");
  }

  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${apiKey}`,
  };

  const res = await fetch("https://api.openai.com/v1/chat/completions", {
    headers,
    method: "POST",
    body: JSON.stringify({
      model: "gpt-3.5-turbo",
      messages,
      stream: true,
      max_tokens: 200,
    }),
  });

  const reader = res.body && res.body.getReader ? res.body.getReader() : null;
  if (res.status !== 200 || !reader) {
    throw new Error("Something went wrong");
  }


  //流式响应
  const stream = new ReadableStream({
    async start(controller) {
      const decoder = new TextDecoder("utf-8");
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          const data = decoder.decode(value);
          const chunks = data
            .split("data:")
            .filter((val) => !!val && val.trim() !== "[DONE]");
          for (const chunk of chunks) {
            const json = JSON.parse(chunk);
            const messagePiece = json.choices && json.choices[0] && json.choices[0].delta && json.choices[0].delta.content;
            if (messagePiece) {
              controller.enqueue(messagePiece);
            }
          }
        }
      } catch (error) {
        controller.error(error);
      } finally {
        reader.releaseLock();
        controller.close();
      }
    },
  });

  return stream;
}


