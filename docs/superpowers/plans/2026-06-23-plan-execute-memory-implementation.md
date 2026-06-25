# Plan-Execute 双模式 + 多轮对话优化 — 实施记录

> **日期:** 2026-06-23
> **状态:** ✅ 已完成
> **关联设计:** `docs/superpowers/specs/2026-06-23-plan-execute-memory-design.md`

---

## 新增文件

| 文件 | 说明 |
|------|------|
| `backend/.../engine/ExecutionPlan.java` | 执行计划 record |
| `backend/.../engine/PlanStep.java` | 计划步骤 record |
| `backend/.../engine/StepResult.java` | 步骤执行结果 record |
| `backend/.../engine/PlanExecuteEvent.java` | SSE 事件 sealed interface（8 种事件） |
| `backend/.../engine/ToolRegistry.java` | 反射式工具注册表，扫描 `@Tool` 注解 |
| `backend/.../engine/PlanExecuteEngine.java` | Plan-Execute 三阶段引擎 |
| `backend/.../memory/SummarizingMemoryProvider.java` | 摘要记忆，超阈值自动 LLM 摘要 |

## 修改文件

| 文件 | 改动 |
|------|------|
| `backend/.../llm/LangChain4jAdapter.java` | 新增 `createChatModel()` 同步模型工厂 |
| `backend/.../config/AgentConfig.java` | 新增 `ChatModel`、`ToolRegistry` Bean；`chatMemoryProvider` 按 `memoryMode` 条件切换 |
| `backend/.../web/ChatController.java` | `mode` 参数路由（react / plan-execute）；新增 6 种 SSE 事件；重构 `runReAct()` / `runPlanExecute()` |
| `backend/.../resources/application.yml` | 新增 `shopai.agent.memory.*` 和 `shopai.agent.plan-execute.*` 配置 |
| `frontend/src/types/index.ts` | 新增 `PlanStepData`、`StepResultData`、`ExecutionMode` |
| `frontend/src/api/chat.ts` | `sendMessage()` 增加 `mode` 参数 |
| `frontend/src/store/chatStore.ts` | 新增 Plan-Execute 状态（`executionMode`, `planSteps`, `planPhase`, `activeStepIndex`） |
| `frontend/src/hooks/useSSE.ts` | 处理 5 种新 SSE 事件（plan_start/plan/step_start/step/synthesis） |
| `frontend/src/components/InputBar.tsx` | 新增 ReAct / Plan-Execute 模式下拉选择器 |
| `frontend/src/components/AgentSteps.tsx` | 增强为 Plan-Execute 计划清单 + 进度可视化 |
| `frontend/src/components/MessageBubble.tsx` | 传递 Plan-Execute 上下文到 AgentSteps |
| `README.md` | 更新架构说明、开发路线、项目结构、JD 技能覆盖 |

---

## API 变更

### POST /api/chat/send

新增 `mode` 字段：

```json
{
  "sessionId": "uuid",
  "message": "5000块推荐拍照好的手机",
  "mode": "plan-execute"    // 新增，默认 "react"
}
```

### GET /api/chat/stream/{messageId}

Plan-Execute 模式下新增 SSE 事件：

```
plan_start → plan → step_start → step → ... → synthesis → token* → done
```

ReAct 模式 SSE 事件不变：`token* → done`

---

## 验证

| 检查项 | 结果 |
|--------|------|
| 后端编译 (`mvn compile`) | ✅ BUILD SUCCESS |
| 前端类型检查 (`tsc --noEmit`) | ✅ No errors |
| ReAct 模式回归 | ✅ 默认 mode="react"，行为不变 |
| Plan-Execute 模式 | ✅ 三阶段 SSE 事件正常发射 |
| 记忆模式切换 (`memory.mode`) | ✅ window / summarizing 切换正常 |
