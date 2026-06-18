import { useEffect, useRef } from 'react';
import { useChatStore } from '../store/chatStore';
import type { TokenUsage } from '../types';

interface SSEDonePayload {
  messageId: string;
  content: string;
  tokenUsage: TokenUsage;
  latencyMs: number;
}

export function useSSE() {
  const { addMessage, setIsStreaming } = useChatStore();
  const eventSourceRef = useRef<EventSource | null>(null);

  const connect = (streamUrl: string) => {
    disconnect();

    const es = new EventSource(streamUrl);
    eventSourceRef.current = es;
    setIsStreaming(true);

    // Create a placeholder assistant message that will be updated
    const messageId = crypto.randomUUID();
    addMessage({
      id: messageId,
      role: 'assistant',
      content: '',
    });

    es.addEventListener('token', (e: MessageEvent) => {
      const token = e.data as string;
      useChatStore.getState().updateLastAssistant((prev) => prev + token);
    });

    es.addEventListener('done', (e: MessageEvent) => {
      const data: SSEDonePayload = JSON.parse(e.data);
      setIsStreaming(false);
      es.close();
    });

    es.addEventListener('error', () => {
      setIsStreaming(false);
      es.close();
    });

    es.onerror = () => {
      setIsStreaming(false);
      es.close();
    };
  };

  const disconnect = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    setIsStreaming(false);
  };

  useEffect(() => {
    return () => {
      disconnect();
    };
  }, []);

  return { connect, disconnect };
}
