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

// ── Plan-Execute types ──────────────────────────────────────────────

export interface PlanStepData {
  index: number;
  type: 'tool_call' | 'reasoning';
  description: string;
  tool: string;
  args: Record<string, unknown>;
}

export interface StepResultData {
  stepIndex: number;
  type: string;
  tool: string;
  output: string;
  success: boolean;
}

export type ExecutionMode = 'react' | 'plan-execute';

// ── Conversation ────────────────────────────────────────────────────

export interface Conversation {
  sessionId: string;
  title: string;
  createdAt: string;
}
