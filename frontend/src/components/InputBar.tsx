import { useState, useRef, useEffect, KeyboardEvent } from 'react';
import { useChatStore } from '../store/chatStore';
import { sendMessage } from '../api/chat';
import { useSSE } from '../hooks/useSSE';
import type { ExecutionMode } from '../types';

export default function InputBar() {
  const [input, setInput] = useState('');
  const [mode, setMode] = useState<ExecutionMode>('react');
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const {
    currentSessionId,
    isStreaming,
    addMessage,
    setExecutionMode,
  } = useChatStore();
  const { connect } = useSSE();

  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || isStreaming) return;

    const sessionId = currentSessionId || crypto.randomUUID();
    if (!currentSessionId) {
      useChatStore.getState().setCurrentSession(sessionId);
    }

    addMessage({
      id: crypto.randomUUID(),
      role: 'user',
      content: trimmed,
    });

    setInput('');
    setExecutionMode(mode);

    try {
      const { streamUrl } = await sendMessage(sessionId, trimmed, mode);
      connect(streamUrl);
    } catch (err) {
      console.error('Failed to send message:', err);
      addMessage({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '抱歉，发送消息失败，请稍后重试。',
      });
    }
  };

  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 120) + 'px';
    }
  }, [input]);

  return (
    <div className="border-t border-gray-200 bg-white px-4 py-3">
      <div className="flex items-end gap-2 max-w-3xl mx-auto">
        <select
          value={mode}
          onChange={(e) => setMode(e.target.value as ExecutionMode)}
          className="text-xs border border-gray-300 rounded-lg px-2 py-2.5 bg-white text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-500"
          disabled={isStreaming}
          title="执行模式"
        >
          <option value="react">ReAct</option>
          <option value="plan-execute">Plan-Execute</option>
        </select>
        <textarea
          ref={textareaRef}
          className="flex-1 resize-none rounded-xl border border-gray-300 px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
          rows={1}
          placeholder="输入您的问题，Agent 会帮您查询..."
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={isStreaming}
        />
        <button
          onClick={handleSend}
          disabled={isStreaming || !input.trim()}
          className="px-5 py-2.5 bg-blue-600 text-white rounded-xl text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          发送
        </button>
      </div>
    </div>
  );
}
