export type Role = 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL';

export interface Message {
  id: string;
  role: Role;
  content: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
}

export type StepType = 'THOUGHT' | 'TOOL_CALL' | 'TOOL_RESULT' | 'FINAL';

export interface StepRecord {
  iteration: number;
  type: StepType;
  thought?: string;
  toolName?: string;
  arguments?: Record<string, unknown>;
  result?: {
    success: boolean;
    content: string;
  };
}

export interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
}

export interface AgentFinal {
  messageId: string;
  content: string;
  steps: StepRecord[];
  tokenUsage: TokenUsage;
  latencyMs: number;
}

export interface SSEStepEvent {
  iteration: number;
  type: StepType;
  thought?: string;
  toolName?: string;
  arguments?: Record<string, unknown>;
  result?: { success: boolean; content: string };
}

export interface Conversation {
  sessionId: string;
  title: string;
  createdAt: string;
}
