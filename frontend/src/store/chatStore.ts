import { create } from 'zustand';
import type { Conversation, ExecutionMode, PlanStepData, StepRecord } from '../types';

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  steps?: StepRecord[];
  tokenUsage?: { inputTokens: number; outputTokens: number };
  latencyMs?: number;
}

interface ChatState {
  // Sessions
  sessions: Conversation[];
  currentSessionId: string | null;
  setSessions: (sessions: Conversation[]) => void;
  setCurrentSession: (id: string) => void;

  // Messages
  messages: ChatMessage[];
  addMessage: (msg: ChatMessage) => void;
  updateLastAssistant: (content: string | ((prev: string) => string)) => void;
  clearMessages: () => void;

  // Streaming state
  isStreaming: boolean;
  setIsStreaming: (v: boolean) => void;
  currentSteps: StepRecord[];
  addStep: (step: StepRecord) => void;
  clearSteps: () => void;

  // Plan-Execute state
  executionMode: ExecutionMode;
  setExecutionMode: (mode: ExecutionMode) => void;
  planSteps: PlanStepData[];
  setPlanSteps: (steps: PlanStepData[]) => void;
  planPhase: 'idle' | 'planning' | 'executing' | 'synthesizing';
  setPlanPhase: (phase: 'idle' | 'planning' | 'executing' | 'synthesizing') => void;
  activeStepIndex: number;
  setActiveStepIndex: (idx: number) => void;
}

export const useChatStore = create<ChatState>((set) => ({
  sessions: [],
  currentSessionId: null,
  setSessions: (sessions) => set({ sessions }),
  setCurrentSession: (id) => set({ currentSessionId: id }),

  messages: [],
  addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  updateLastAssistant: (content) =>
    set((s) => {
      const msgs = [...s.messages];
      const last = msgs[msgs.length - 1];
      if (last && last.role === 'assistant') {
        const newContent = typeof content === 'function'
          ? (content as (prev: string) => string)(last.content)
          : content;
        msgs[msgs.length - 1] = { ...last, content: newContent };
      }
      return { messages: msgs };
    }),
  clearMessages: () => set({ messages: [] }),

  isStreaming: false,
  setIsStreaming: (v) => set({ isStreaming: v }),
  currentSteps: [],
  addStep: (step) => set((s) => ({ currentSteps: [...s.currentSteps, step] })),
  clearSteps: () => set({ currentSteps: [] }),

  // Plan-Execute state
  executionMode: 'react',
  setExecutionMode: (mode) => set({ executionMode: mode }),
  planSteps: [],
  setPlanSteps: (steps) => set({ planSteps: steps }),
  planPhase: 'idle',
  setPlanPhase: (phase) => set({ planPhase: phase }),
  activeStepIndex: -1,
  setActiveStepIndex: (idx) => set({ activeStepIndex: idx }),
}));
