import { create } from 'zustand';
import type { Conversation, StepRecord } from '../types';

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
  updateLastAssistant: (content: string) => void;
  clearMessages: () => void;

  // Streaming state
  isStreaming: boolean;
  setIsStreaming: (v: boolean) => void;
  currentSteps: StepRecord[];
  addStep: (step: StepRecord) => void;
  clearSteps: () => void;
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
        msgs[msgs.length - 1] = { ...last, content };
      }
      return { messages: msgs };
    }),
  clearMessages: () => set({ messages: [] }),

  isStreaming: false,
  setIsStreaming: (v) => set({ isStreaming: v }),
  currentSteps: [],
  addStep: (step) => set((s) => ({ currentSteps: [...s.currentSteps, step] })),
  clearSteps: () => set({ currentSteps: [] }),
}));
