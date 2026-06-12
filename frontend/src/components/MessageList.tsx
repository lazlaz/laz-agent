import { useEffect, useRef } from 'react';
import { useChatStore } from '../store/chatStore';
import MessageBubble from './MessageBubble';
import LoadingDots from './LoadingDots';

export default function MessageList() {
  const { messages, isStreaming, currentSteps } = useChatStore();
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, currentSteps]);

  if (messages.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center text-gray-400">
        <div className="text-center">
          <p className="text-4xl mb-3">ShopAI Agent</p>
          <p className="text-sm">发送消息开始对话，Agent 会自动调用工具回答您的问题</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 overflow-y-auto px-4 py-4">
      {messages.map((msg, i) => (
        <MessageBubble
          key={msg.id || i}
          message={msg}
          isStreaming={i === messages.length - 1 && isStreaming}
        />
      ))}
      {isStreaming && currentSteps.length > 0 && (
        <div className="text-xs text-gray-400 text-center italic">
          Agent 正在思考...
        </div>
      )}
      {isStreaming && currentSteps.length === 0 && <LoadingDots />}
      <div ref={bottomRef} />
    </div>
  );
}
