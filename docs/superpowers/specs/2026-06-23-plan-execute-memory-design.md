# Plan-Execute 双模式 + 多轮对话优化 — 需求设计文档

> **日期:** 2026-06-23
> **状态:** 已实现
> **前置:** Phase 1 (ReAct 核心闭环) + Phase 2 (RAG) 已完成

---

## 1. 背景与目标

### 1.1 当前状态

ShopAI Agent Platform 已通过 LangChain4j AiServices 实现了 ReAct 模式 Agent，但存在两个关键 JD 技能空白：

| 空白 | 当前 | 目标 |
|------|------|------|
| Agent 执行模式 | 仅 ReAct（AiServices 自动循环） | ReAct + **Plan-Execute** 双模式 |
| 对话记忆管理 | MessageWindowChatMemory 简单滑动窗口 | **SummarizingMemoryProvider** 自动摘要 |

### 1.2 设计原则

- **并行共存**: Plan-Execute 不替换 ReAct，两者通过 `mode` 参数切换
- **共享基础设施**: 工具、LLM 模型、记忆存储复用
- **解耦事件**: PlanExecuteEngine 通过 `Consumer<PlanExecuteEvent>` 回调与 SSE 传输层解耦

---

## 2. Plan-Execute 引擎设计

### 2.1 三阶段流程

```
POST /api/chat/send  {mode: "plan-execute"}
        │
        ▼
GET /api/chat/stream/{messageId}
        │
        ▼ SSE 事件流
  ┌─────────────────────────────────────────────┐
  │ Phase 1: PLANNING                           │
  │   LLM (ChatModel, sync) 生成 JSON 执行计划    │
  │   SSE: plan_start → plan(steps)             │
  ├─────────────────────────────────────────────┤
  │ Phase 2: EXECUTION                          │
  │   逐步执行: tool_call → ToolRegistry.invoke  │
  │            reasoning → 记录描述              │
  │   SSE: step_start → step (per step)         │
  ├─────────────────────────────────────────────┤
  │ Phase 3: SYNTHESIS                          │
  │   LLM (StreamingChatModel) 流式汇总          │
  │   SSE: synthesis → token* → done            │
  └─────────────────────────────────────────────┘
```

### 2.2 核心类型

```java
// 执行计划
record ExecutionPlan(List<PlanStep> steps)

// 计划步骤
record PlanStep(int index, String type, String description, String tool, Map<String,Object> args)

// 步骤结果
record StepResult(int stepIndex, String type, String tool, String output, boolean success)

// SSE 事件（sealed interface）
sealed interface PlanExecuteEvent permits
    PlanStart, PlanReady, StepStart, StepDone,
    SynthesisStart, SynthesisToken, SynthesisDone, PlanError
```

### 2.3 ToolRegistry（反射式工具注册）

Plan-Execute 不能使用 AiServices 自动工具调用，需要独立的工具执行机制：

- 扫描 `@Tool` 注解的 Spring Bean 方法
- 以 `method.getName()` 为工具名（如 `searchProducts`、`queryOrders`）
- `invoke(toolName, args)` 通过 Jackson 类型强制转换后反射调用
- 所有工具返回 `String`（与 AiServices 的 `@Tool` 约定一致）

### 2.4 Planning Prompt 关键约束

- 输出 ONLY valid JSON（两次重试容错）
- 包含可用工具列表 + 参数 schema
- 每步标注 type（tool_call / reasoning）
- Fallback：JSON 解析失败 → 单步 reasoning

### 2.5 SSE 事件映射

| PlanExecuteEvent | SSE Event | Data |
|---|---|---|
| PlanStart | `plan_start` | `{phase: "planning"}` |
| PlanReady | `plan` | `{steps: [...]}` |
| StepStart | `step_start` | `{stepIndex, type, tool, description}` |
| StepDone | `step` | `{stepIndex, type, tool, output, success}` |
| SynthesisStart | `synthesis` | `{}` |
| SynthesisToken | `token` | raw string |
| SynthesisDone | `done` | `{messageId, content, tokenUsage, latencyMs}` |
| PlanError | `error` | `{phase, message}` |

---

## 3. SummarizingMemory 设计

### 3.1 策略

```
总消息数 ≤ maxBeforeSummary(15) → 原样返回
总消息数 >  maxBeforeSummary(15) → 
    oldest = messages[0 .. total-keepRecent(10)]
    recent  = messages[total-keepRecent .. end]
    summary = LLM.chat(summarizePrompt(oldest))
    return [SystemMessage("摘要: " + summary), ...recent]
```

### 3.2 关键决策

| 决策 | 理由 |
|------|------|
| 摘要不持久化 | 避免污染 ChatMemoryStore，仅在读取时注入 |
| 缓存 by (sessionId, messageCount) | 消息数未变时复用缓存，避免重复 LLM 调用 |
| LLM 失败 fallback | 截断到 keepRecentCount，不阻断对话 |
| 通过 `shopai.agent.memory.mode` 切换 | 默认 `window`，零改动兼容现有行为 |

---

## 4. ChatController 模式路由

```
send() 接受 {sessionId, message, mode}
    mode 默认 "react"

stream() 路由：
    mode == "plan-execute" → runPlanExecute()
    else                   → runReAct()
```

---

## 5. 前端设计

### 5.1 新增类型

```typescript
type ExecutionMode = 'react' | 'plan-execute'

interface PlanStepData {
  index: number; type: 'tool_call' | 'reasoning'
  description: string; tool: string; args: Record<string, unknown>
}
```

### 5.2 新增 Store 状态

| 字段 | 类型 | 用途 |
|------|------|------|
| `executionMode` | `ExecutionMode` | 当前模式 |
| `planSteps` | `PlanStepData[]` | 计划步骤列表 |
| `planPhase` | `'idle'\|'planning'\|'executing'\|'synthesizing'` | 当前阶段 |
| `activeStepIndex` | `number` | 正在执行的步骤 |

### 5.3 UI 改动

- **InputBar**: 添加 `<select>` 模式切换（ReAct / Plan-Execute）
- **AgentSteps**: Plan-Execute 模式下渲染计划清单（✓完成 / ⋯执行中 / ○等待 / ✗失败）
- **useSSE**: 新增 5 个 SSE 事件监听器

---

## 6. 配置

```yaml
shopai:
  agent:
    memory:
      mode: window  # window | summarizing
      summarizing:
        max-messages-before-summary: 15
        keep-recent-count: 10
    plan-execute:
      max-steps: 10
```

---

## 7. 成功标准

1. ✅ Plan-Execute 模式：输入"5000块推荐拍照好的手机"，前端显示执行计划 → 工具调用 → 流式答案
2. ✅ 摘要记忆：长对话 30+ 轮后，旧消息被摘要注入，上下文不丢失
3. ✅ ReAct 回归：默认模式行为完全不变
4. ✅ 双模式共存在同一 ChatController 中
