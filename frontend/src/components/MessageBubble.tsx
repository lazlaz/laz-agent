import type { ChatMessage } from '../store/chatStore';
import AgentSteps from './AgentSteps';

interface Props {
  message: ChatMessage;
  isStreaming?: boolean;
}

export default function MessageBubble({ message, isStreaming }: Props) {
  const isUser = message.role === 'user';

  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} mb-4`}>
      <div className={`max-w-[75%] ${isUser ? 'order-1' : ''}`}>
        {!isUser && (
          <span className="text-xs text-gray-400 ml-1 mb-1 block">
            ShopAI Agent
            {message.latencyMs != null && (
              <span className="ml-2 text-gray-300">{message.latencyMs}ms</span>
            )}
          </span>
        )}
        <div
          className={`px-4 py-2.5 rounded-2xl text-sm leading-relaxed whitespace-pre-wrap ${
            isUser
              ? 'bg-blue-600 text-white rounded-br-md'
              : 'bg-white border border-gray-200 text-gray-800 rounded-bl-md shadow-sm'
          }`}
        >
          {message.content}
        </div>
        {message.steps && message.steps.length > 0 && (
          <AgentSteps steps={message.steps} isStreaming={isStreaming ?? false} />
        )}
        {message.tokenUsage && (
          <div className="text-xs text-gray-300 mt-1 ml-1">
            Tokens: {message.tokenUsage.inputTokens} in / {message.tokenUsage.outputTokens} out
          </div>
        )}
      </div>
    </div>
  );
}
