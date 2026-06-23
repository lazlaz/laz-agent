import { useEffect, useRef } from 'react';
import { useChatStore } from '../store/chatStore';
import { fetchSessions } from '../api/chat';
import type { Conversation } from '../types';

export function useSSE() {
  const { addMessage, setIsStreaming, setSessions } = useChatStore();
  const eventSourceRef = useRef<EventSource | null>(null);

  const refreshSessions = async () => {
    try {
      const data = await fetchSessions();
      const convs: Conversation[] = data.map((d) => ({
        sessionId: d.SESSION_ID,
        title: d.TITLE,
        createdAt: d.CREATED_AT,
      }));
      setSessions(convs);
    } catch {
      // Silently ignore — session list refresh is best-effort
    }
  };

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

    es.addEventListener('done', () => {
      setIsStreaming(false);
      es.close();
      refreshSessions();
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
