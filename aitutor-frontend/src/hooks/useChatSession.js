import { useState, useEffect, useRef, useCallback } from 'react';
import { askStream, interrupt, getSession } from '../services/chatService';

/**
 * ChatPanel 对话状态管理 Hook
 *
 * 管理完整的对话生命周期：
 *   - SSE 流式消费（reader + chunks → 逐字累积）
 *   - 会话创建 / 恢复
 *   - 打断（abort reader + POST /interrupt）
 *   - localStorage 持久化 sessionId（刷新恢复）
 *   - 错误状态 + 重试
 *
 * @param {Object}  props
 * @param {string}  props.sceneType    doing_exercise | explaining | teaching | lesson_prep | general_qa
 * @param {Object}  props.context      场景上下文 { questionId?, currentSlide?, relatedKpId?, ... }
 * @param {number}  props.userId       用户 ID
 * @param {boolean} [props.autoRestore=true]  是否自动恢复上次会话
 *
 * @returns {{
 *   messages: Array<{role:string, content:string, isStreaming?:boolean, error?:boolean}>,
 *   isGenerating: boolean,
 *   sessionId: string|null,
 *   error: string|null,
 *   send: (text:string) => void,
 *   abort: () => void,
 *   retry: () => void,
 *   clear: () => void,
 * }}
 */
export function useChatSession({ sceneType, context, userId, autoRestore = true }) {
  const [messages, setMessages] = useState([]);
  const [isGenerating, setIsGenerating] = useState(false);
  const [sessionId, setSessionId] = useState(null);
  const [error, setError] = useState(null);

  // 保存最后一次请求参数，用于 retry
  const lastQuestionRef = useRef(null);
  // 当前流 reader 取消控制器
  const abortRef = useRef(null);
  // 流式消息累积 buffer
  const bufferRef = useRef('');
  // 同步锁，防止 send 被快速双击绕过 state 检查
  const isGeneratingRef = useRef(false);

  // -------- 初始化：尝试恢复会话 --------
  useEffect(() => {
    if (!autoRestore) return;
    const savedSessionId = localStorage.getItem('chatSessionId');
    if (savedSessionId) {
      getSession(savedSessionId)
        .then(session => {
          setSessionId(session.sessionId);
          const restored = (session.messages || []).map(msg => ({
            role: msg.role,
            content: msg.content,
          }));
          if (restored.length > 0) setMessages(restored);
        })
        .catch(() => {
          // 恢复失败，清除旧 session
          localStorage.removeItem('chatSessionId');
        });
    }
  }, [autoRestore]);

  // -------- 持久化 sessionId --------
  useEffect(() => {
    if (sessionId) {
      localStorage.setItem('chatSessionId', sessionId);
    }
  }, [sessionId]);

  // -------- 拦截流 chunk，逐字追加到最后一条 assistant 消息 --------
  const processChunk = useCallback((chunk) => {
    if (chunk.type === 'content' && chunk.chunk) {
      bufferRef.current += chunk.chunk;
      const captured = bufferRef.current; // ⚠️ 捕获快照，setMessages updater 异步执行时 ref 可能已被改
      setMessages(prev => {
        const copy = [...prev];
        const last = copy[copy.length - 1];
        if (last && last.isStreaming) {
          copy[copy.length - 1] = { ...last, content: captured };
        }
        return copy;
      });
    } else if (chunk.type === 'done') {
      // 标记流式结束
      const captured = bufferRef.current; // ⚠️ 必须捕获！下面立即清空 ref
      isGeneratingRef.current = false;
      setIsGenerating(false);
      setMessages(prev => {
        const copy = [...prev];
        const last = copy[copy.length - 1];
        if (last && last.isStreaming) {
          copy[copy.length - 1] = { role: 'assistant', content: captured };
        }
        return copy;
      });
      bufferRef.current = '';
    }
  }, []);

  // -------- 发送消息 --------
  const send = useCallback((text, { isRetry = false } = {}) => {
    if (!text.trim() || isGeneratingRef.current) return;
    setError(null);
    lastQuestionRef.current = text;
    isGeneratingRef.current = true;

    // retry 时不重复追加用户消息
    if (!isRetry) {
      const userMsg = { role: 'user', content: text };
      setMessages(prev => [...prev, userMsg]);
    }

    // 追加占位 assistant 消息（用于流式更新）
    const assistantMsg = { role: 'assistant', content: '', isStreaming: true };
    setMessages(prev => [...prev, assistantMsg]);
    bufferRef.current = '';
    setIsGenerating(true);

    // 启动流
    const stream = askStream({
      userId,
      sessionId,
      question: text,
      sceneType,
      context,
    });

    // 保存 sessionId（若后端返回新的）
    // 通过 reader 消费流
    const reader = stream.getReader();
    abortRef.current = {
      reader,
      abort: () => {
        try { reader.cancel(); } catch { /* ignore */ }
      },
    };

    function read() {
      reader.read().then(({ done, value }) => {
        if (done) {
          isGeneratingRef.current = false;
          setIsGenerating(false);
          return;
        }
        if (value && value.type) {
          // 如果是 done 事件，提取 sessionId
          if (value.type === 'done' && value.sessionId) {
            setSessionId(value.sessionId);
          }
          // 后端推送错误事件
          if (value.type === 'error') {
            setError(value.message || '生成失败');
            isGeneratingRef.current = false;
            setIsGenerating(false);
            const captured = bufferRef.current; // ⚠️ 捕获快照
            // 移除占位 assistant 消息（内容为空）
            setMessages(prev => {
              const copy = [...prev];
              const last = copy[copy.length - 1];
              if (last && last.isStreaming && !captured) {
                copy.pop();
              } else if (last && last.isStreaming) {
                copy[copy.length - 1] = { role: 'assistant', content: captured };
              }
              return copy;
            });
            bufferRef.current = '';
            return;
          }
          processChunk(value);
        }
        read();
      }).catch((err) => {
        // reader cancel（用户主动打断）走 abort 路径，不会进这里
        // 这里是网络/解析异常
        const captured = bufferRef.current; // ⚠️ 捕获快照
        isGeneratingRef.current = false;
        setIsGenerating(false);
        setError(err?.message || '连接异常，请重试');
        // 保留已生成内容
        setMessages(prev => {
          const copy = [...prev];
          const last = copy[copy.length - 1];
          if (last && last.isStreaming) {
            copy[copy.length - 1] = {
              role: 'assistant',
              content: captured || '生成失败',
              error: true,
            };
          }
          return copy;
        });
        bufferRef.current = '';
      });
    }
    read();
  }, [userId, sessionId, sceneType, context, processChunk]);

  // -------- 打断 --------
  const abort = useCallback(() => {
    // 取消流 reader
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    // 通知后端
    if (sessionId) {
      interrupt(sessionId).catch(() => { /* ignore */ });
    }
    // 保留已生成内容，去掉 streaming 标记
    const captured = bufferRef.current; // ⚠️ 捕获快照
    isGeneratingRef.current = false;
    setIsGenerating(false);
    setMessages(prev => {
      const copy = [...prev];
      const last = copy[copy.length - 1];
      if (last && last.isStreaming) {
        copy[copy.length - 1] = {
          role: 'assistant',
          content: (captured || '生成已中断'),
        };
      }
      return copy;
    });
    bufferRef.current = '';
  }, [sessionId]);

  // -------- 重试 --------
  const retry = useCallback(() => {
    if (!lastQuestionRef.current || isGeneratingRef.current) return;
    // 移除最后一条失败的 assistant 消息（带 error 标记）
    setMessages(prev => {
      const copy = [...prev];
      const last = copy[copy.length - 1];
      if (last && last.role === 'assistant' && last.error) {
        copy.pop();
      }
      return copy;
    });
    send(lastQuestionRef.current, { isRetry: true });
  }, [send]);

  // -------- 清空对话 --------
  const clear = useCallback(() => {
    // 若正在生成，先打断流
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    isGeneratingRef.current = false;
    setIsGenerating(false);
    setMessages([]);
    setSessionId(null);
    setError(null);
    bufferRef.current = '';
    localStorage.removeItem('chatSessionId');
  }, []);

  return {
    messages,
    isGenerating,
    sessionId,
    error,
    send,
    abort,
    retry,
    clear,
  };
}
