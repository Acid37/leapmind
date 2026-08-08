import { useState, useEffect, useRef, useCallback } from 'react';
import { askStream, interrupt, getSession, ChatError } from '../services/chatService';

const LEGACY_STORAGE_KEY = 'chatSessionId';

/**
 * 每个学习场景拥有独立会话：同一题/同一堂课可在刷新后恢复，
 * 但做题、讲题、讲课、备课之间绝不能互相复用上下文。
 */
function getSessionResourceId(sceneType, context = {}) {
  return {
    doing_exercise: context.questionId,
    explaining: context.wrongQuestionId,
    teaching: context.lectureId,
    lesson_prep: context.prepId,
  }[sceneType] ?? null;
}

function getSessionStorageKey(userId, sceneType, resourceId) {
  return `chatSessionId:${userId ?? 'anonymous'}:${sceneType || 'general_qa'}:${resourceId ?? 'default'}`;
}

function isMatchingSession(session, userId, sceneType, resourceId) {
  if (!session || String(session.userId) !== String(userId) || session.sceneType !== sceneType) return false;

  const keyByScene = {
    doing_exercise: 'questionId',
    explaining: 'wrongQuestionId',
    teaching: 'lectureId',
    lesson_prep: 'prepId',
  };
  const resourceKey = keyByScene[sceneType];

  return !resourceKey || String(session.context?.[resourceKey] ?? '') === String(resourceId ?? '');
}

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
 *   error: { message:string, code?:number }|null,
 *   send: (text:string) => void,
 *   abort: () => void,
 *   retry: () => void,
 *   clear: () => void,
 * }}
 */
export function useChatSession({ sceneType, context, userId, autoRestore = true }) {
  const [messages, setMessages] = useState([]);
  // 对话状态机（对齐 M7 对接文档）：idle / thinking / content / error / interrupted
  const [phase, setPhase] = useState('idle');
  const [sessionId, setSessionId] = useState(null);
  const [sessionStorageKey, setSessionStorageKey] = useState(null);
  const [error, setError] = useState(null);
  const resourceId = getSessionResourceId(sceneType, context);
  const storageKey = getSessionStorageKey(userId, sceneType, resourceId);

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
    let cancelled = false;
    setMessages([]);
    setSessionId(null);
    setSessionStorageKey(null);
    setError(null);

    if (!autoRestore) return () => { cancelled = true; };

    const savedSessionId = localStorage.getItem(storageKey);
    if (savedSessionId) {
      getSession(savedSessionId)
        .then(session => {
          if (cancelled) return;
          if (!isMatchingSession(session, userId, sceneType, resourceId)) {
            localStorage.removeItem(storageKey);
            return;
          }
          setSessionId(session.sessionId);
          setSessionStorageKey(storageKey);
          const restored = (session.messages || []).map(msg => ({
            role: msg.role,
            content: msg.content,
          }));
          if (restored.length > 0) setMessages(restored);
        })
        .catch(() => {
          // 恢复失败，清除旧 session
          localStorage.removeItem(storageKey);
        });
    }
    return () => { cancelled = true; };
  }, [autoRestore, storageKey, userId, sceneType, resourceId]);

  // -------- 持久化 sessionId --------
  useEffect(() => {
    if (sessionId && sessionStorageKey === storageKey) {
      localStorage.setItem(storageKey, sessionId);
    }
  }, [sessionId, sessionStorageKey, storageKey]);

  // -------- 立即切断当前 SSE 流（⚠️ 致命切断点：终端事件必须主动 cancel） --------
  // fetch 本身不会自动重连，天然安全；此处统一封装，若未来切换 @microsoft/fetch-event-source
  // 也必须在 done/error/interrupted 终端事件处手动 abort，防止重连打挂后端限流器。
  const stopStream = useCallback(() => {
    if (abortRef.current) {
      try { abortRef.current.abort(); } catch { /* ignore */ }
      abortRef.current = null;
    }
  }, []);

  // -------- 结束生成（清同步锁 + 置状态机） --------
  const finishGenerating = useCallback((nextPhase) => {
    isGeneratingRef.current = false;
    setPhase(nextPhase);
  }, []);

  // -------- 收尾最后一条 isStreaming 消息（保留已生成内容） --------
  const finalizeMessage = useCallback((captured, { markError = false } = {}) => {
    setMessages(prev => {
      const copy = [...prev];
      const last = copy[copy.length - 1];
      if (last && last.isStreaming) {
        copy[copy.length - 1] = markError
          ? { role: 'assistant', content: captured || '生成失败', error: true }
          : { role: 'assistant', content: captured };
      }
      return copy;
    });
  }, []);

  // -------- 移除空占位（内容为空时不用保留） --------
  const dropEmptyPlaceholder = useCallback(() => {
    setMessages(prev => {
      const copy = [...prev];
      const last = copy[copy.length - 1];
      if (last && last.isStreaming && !bufferRef.current) {
        copy.pop();
      }
      return copy;
    });
  }, []);

  // -------- 拦截流 chunk，逐字追加到最后一条 assistant 消息 --------
  const applyContent = useCallback((chunk) => {
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
    setPhase('thinking'); // 状态机 → thinking

    // 启动流
    const stream = askStream({
      userId,
      sessionId,
      question: text,
      sceneType,
      context,
    });

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
          // 流自然结束（无终端事件，如连接被服务端正常关闭）
          finishGenerating('idle');
          return;
        }
        if (value && value.type) {
          // ✅ SessionID 闭环：任意事件（thinking/content/done/...）都可能携带 sessionId，
          //    收到即保存，后续追问必须携带该 ID
          if (value.sessionId) {
            setSessionId(value.sessionId);
            setSessionStorageKey(storageKey);
          }

          if (value.type === 'thinking') {
            // thinking 态：骨架屏/Loading（ChatPanel 渲染）
            setPhase('thinking');
          } else if (value.type === 'content') {
            setPhase('content');
            applyContent(value);
          } else if (value.type === 'done') {
            // ⚠️ 终端事件：立即切断连接（防重连打挂限流器）+ 收尾消息
            stopStream();
            const captured = bufferRef.current; // ⚠️ 必须捕获！下面立即清空 ref
            finishGenerating('idle');
            finalizeMessage(captured);
            bufferRef.current = '';
            return; // 终端事件不再继续 read()
          } else if (value.type === 'error') {
            // ⚠️ 终端事件：立即切断连接 + 按 code 差异化处理
            stopStream();
            const captured = bufferRef.current;
            finishGenerating('error');
            if (!captured) dropEmptyPlaceholder();
            else finalizeMessage(captured);
            setError({
              message: value.message || '生成失败',
              code: value.code || undefined,
            });
            bufferRef.current = '';
            return; // 终端事件不再继续 read()
          } else if (value.type === 'interrupted') {
            // ⚠️ 终端事件：立即切断连接 + 保留半成品文本
            stopStream();
            const captured = bufferRef.current;
            finishGenerating('interrupted');
            finalizeMessage(captured);
            bufferRef.current = '';
            return; // 终端事件不再继续 read()
          }
        }
        // 非终端事件（thinking/content/未知）继续消费后续 chunk
        read();
      }).catch((err) => {
        // reader cancel（用户主动打断）走 abort 路径，不会进这里
        // 这里是网络/解析异常
        const captured = bufferRef.current; // ⚠️ 捕获快照
        finishGenerating('error');
        const isChatError = err instanceof ChatError || err?.code;
        setError({
          message: err?.message || '连接异常，请重试',
          code: isChatError ? (err.code || 1001) : undefined,
        });
        // 保留已生成内容
        finalizeMessage(captured, { markError: true });
        bufferRef.current = '';
      });
    }
    read();
  }, [userId, sessionId, sceneType, context, storageKey, applyContent, dropEmptyPlaceholder, finalizeMessage, finishGenerating, stopStream]);

  // -------- 打断（用户点击"停止生成"） --------
  const abort = useCallback(() => {
    // 1. 切断本地 SSE 流
    stopStream();
    // 2. 通知后端取消 Flux 订阅
    if (sessionId) {
      interrupt(sessionId).catch(() => { /* ignore */ });
    }
    // 3. 保留已生成内容，状态机 → interrupted
    const captured = bufferRef.current; // ⚠️ 捕获快照
    finishGenerating('interrupted');
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
  }, [sessionId, stopStream, finishGenerating]);

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
    stopStream();
    isGeneratingRef.current = false;
    setPhase('idle');
    setMessages([]);
    setSessionId(null);
    setSessionStorageKey(null);
    setError(null);
    bufferRef.current = '';
    localStorage.removeItem(storageKey);
    // 旧版全局键可能指向任意模块的历史；绝不迁移，直接丢弃。
    localStorage.removeItem(LEGACY_STORAGE_KEY);
  }, [storageKey, stopStream]);

  // isGenerating 派生自状态机（thinking/content 视为正在生成），保持对外兼容
  const isGenerating = phase === 'thinking' || phase === 'content';

  return {
    messages,
    phase,
    isGenerating,
    sessionId,
    error,
    send,
    abort,
    retry,
    clear,
  };
}
