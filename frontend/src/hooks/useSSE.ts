import { useEffect, useRef } from 'react';
import { useChatStore } from '../store/chatStore';
import type { SSEStepEvent, AgentFinal, StepRecord } from '../types';

export function useSSE() {
  const { addStep, clearSteps, addMessage, setIsStreaming } = useChatStore();
  const eventSourceRef = useRef<EventSource | null>(null);

  const connect = (streamUrl: string) => {
    disconnect();
    clearSteps();

    const es = new EventSource(streamUrl);
    eventSourceRef.current = es;
    setIsStreaming(true);

    es.addEventListener('step', (e: MessageEvent) => {
      const data: SSEStepEvent = JSON.parse(e.data);
      const step: StepRecord = {
        iteration: data.iteration,
        type: data.type,
        thought: data.thought,
        toolName: data.toolName,
        arguments: data.arguments,
        result: data.result,
      };
      addStep(step);
    });

    es.addEventListener('final', (e: MessageEvent) => {
      const data: AgentFinal = JSON.parse(e.data);
      addMessage({
        id: data.messageId,
        role: 'assistant',
        content: data.content,
        steps: data.steps,
        tokenUsage: data.tokenUsage,
        latencyMs: data.latencyMs,
      });
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
