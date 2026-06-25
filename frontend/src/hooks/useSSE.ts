import { useEffect, useRef } from 'react';
import { useChatStore } from '../store/chatStore';
import { fetchSessions } from '../api/chat';
import type { Conversation, PlanStepData } from '../types';

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

    // ── ReAct mode: token streaming ─────────────────────────────────

    es.addEventListener('token', (e: MessageEvent) => {
      const token = e.data as string;
      useChatStore.getState().updateLastAssistant((prev) => prev + token);
    });

    // ── Done (shared by both modes) ─────────────────────────────────

    es.addEventListener('done', () => {
      setIsStreaming(false);
      es.close();
      refreshSessions();
    });

    // ── Plan-Execute: planning phase ─────────────────────────────────

    es.addEventListener('plan_start', () => {
      useChatStore.getState().setPlanPhase('planning');
      useChatStore.getState().clearSteps();
    });

    es.addEventListener('plan', (e: MessageEvent) => {
      const data = JSON.parse(e.data);
      const steps: PlanStepData[] = data.steps || [];
      useChatStore.getState().setPlanSteps(steps);
      useChatStore.getState().setPlanPhase('executing');
    });

    // ── Plan-Execute: step execution ─────────────────────────────────

    es.addEventListener('step_start', (e: MessageEvent) => {
      const data = JSON.parse(e.data);
      useChatStore.getState().setActiveStepIndex(data.stepIndex);
      useChatStore.getState().addStep({
        iteration: data.stepIndex,
        type: data.type === 'tool_call' ? 'TOOL_CALL' : 'THOUGHT',
        thought: data.description,
        toolName: data.tool || undefined,
        arguments: data.args,
      });
    });

    es.addEventListener('step', (e: MessageEvent) => {
      const data = JSON.parse(e.data);
      useChatStore.getState().addStep({
        iteration: data.stepIndex,
        type: data.type === 'tool_call' ? 'TOOL_RESULT' : 'FINAL',
        toolName: data.tool || undefined,
        result: {
          success: data.success,
          content: data.output,
        },
      });
    });

    // ── Plan-Execute: synthesis phase ────────────────────────────────

    es.addEventListener('synthesis', () => {
      useChatStore.getState().setPlanPhase('synthesizing');
    });

    // ── Error ───────────────────────────────────────────────────────

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
