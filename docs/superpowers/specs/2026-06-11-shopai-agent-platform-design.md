# ShopAI Agent Platform — 需求设计文档

> **日期:** 2026-06-11
> **状态:** Draft
> **目标:** 通过自建迷你 Agent 平台练手项目，掌握 AI Agent 开发招聘所需的核心技能

---

## 1. 项目概述

### 1.1 项目定位

自建一个迷你 AI Agent 平台，以**电商智能客服（ShopAI）** 为业务场景驱动，从零实现 Agent 执行引擎、工具注册与调用框架、对话记忆管理、Prompt 模板引擎、RAG 链路、LLM 适配、可观测性等核心组件。

### 1.2 业务场景

模拟电商平台 **ShopAI**（电子产品在线商城）的智能客服系统，支持 4 类对话场景：

| 场景 | 示例对话 | 涉及组件 |
|------|---------|---------|
| 订单查询 | "我的订单 20240611001 到哪了？" | Memory + Tool + Prompt |
| 政策问答 | "耳机用了3天坏了可以退吗？" | RAG + Memory + Prompt |
| 产品推荐 | "预算5000，推荐拍照好的手机" | Tool + RAG + Prompt |
| 退换货申请 | "我要退订单里的手机壳" | RAG + Tool + Memory + Plan-Execute |

### 1.3 对标 JD 技能覆盖

| JD 要求 | 平台对应组件 |
|---------|------------|
| Agent 执行引擎 | `engine/` — ReAct / Plan-and-Execute 循环 |
| 工具注册与调用框架 | `tool/` — 统一 ToolDefinition + ToolRegistry |
| 对话记忆管理 | `memory/` — 短期历史 + 长期摘要 + H2 持久化 |
| Prompt 模板引擎 | `prompt/` — 模板管理 + 变量注入 |
| LangChain4j / Spring AI 集成 | `llm/` — LlmAdapter + LangChain4j |
| RAG 链路 | `rag/` — 切片 → 向量化 → 检索 → 排序 → 注入 |
| 性能与稳定性 | `observe/` — 日志 / Token 统计 / 延迟监控 |
| 流式对话 | SSE 端点 + 前端 SSE 接收 |
| Function Calling | LLMResponse 中的 TOOL_CALL 决策解析 |

---

## 2. 技术栈

### 2.1 后端

| 项 | 选型 | 说明 |
|----|------|------|
| 语言 | Java 17+ | 对标 JD |
| 框架 | Spring Boot 3.x + Spring MVC | REST API + SSE |
| AI 框架 | LangChain4j | LLM 适配层，Agent 核心逻辑自建 |
| 数据库 | H2 (embedded) | 会话/消息/订单/产品/文档分块持久化 |
| 向量存储 | H2 (Phase 1) → Pgvector (Phase 2) | Phase 1 用内存向量模拟 |
| LLM | OpenAI API 兼容协议 | 通过配置切换 OpenAI / Ollama / 国产模型 |
| 构建 | Maven | 标准 Spring Boot 项目 |
| 测试 | JUnit 5 + Mockito | 单元测试 + 集成测试 |

### 2.2 前端

| 项 | 选型 | 说明 |
|----|------|------|
| 框架 | React 18 + TypeScript | 独立前端项目 |
| 构建 | Vite | 快速开发构建 |
| 样式 | Tailwind CSS | 聊天 UI 快速构建 |
| 状态管理 | Zustand | 轻量级全局状态 |
| HTTP/SSE | fetch + EventSource | SSE 流式接收 Agent 执行过程 |

### 2.3 项目结构

```
shopai-agent-platform/
├── backend/                          # Spring Boot 后端
│   ├── src/main/java/com/shopai/agent/
│   │   ├── engine/                   # AgentEngine 核心 ReAct 循环
│   │   ├── tool/                     # ToolRegistry + Tool 实现
│   │   ├── memory/                   # MemoryManager + H2 持久化
│   │   ├── prompt/                   # PromptEngine + 模板管理
│   │   ├── llm/                      # LlmAdapter + LangChain4j
│   │   ├── rag/                      # RAG Pipeline (Phase 2)
│   │   ├── observe/                  # 监控日志 (Phase 3)
│   │   ├── web/                      # REST Controller + SSE
│   │   └── config/                   # Spring 配置
│   ├── src/main/resources/
│   │   ├── prompts/                  # Prompt 模板文件
│   │   ├── data/                     # Mock 数据 (JSON)
│   │   ├── docs/                     # RAG 知识库文档
│   │   ├── application.yml
│   │   └── schema.sql
│   └── pom.xml
├── frontend/                         # React 前端
│   ├── src/
│   │   ├── components/
│   │   │   ├── Sidebar.tsx           # 会话列表侧栏
│   │   │   ├── ChatArea.tsx          # 聊天主区域
│   │   │   ├── MessageList.tsx       # 消息列表
│   │   │   ├── MessageBubble.tsx     # 单条消息气泡
│   │   │   ├── AgentSteps.tsx        # Agent 推理步骤（可展开）
│   │   │   ├── InputBar.tsx          # 输入框 + 发送
│   │   │   └── LoadingDots.tsx       # 思考动画
│   │   ├── hooks/
│   │   │   ├── useSSE.ts             # SSE 流式接收 Hook
│   │   │   └── useChat.ts            # 聊天状态管理 Hook
│   │   ├── store/
│   │   │   └── chatStore.ts          # Zustand 全局状态
│   │   ├── api/
│   │   │   └── chat.ts               # 后端 API 调用封装
│   │   ├── types/
│   │   │   └── index.ts              # TypeScript 类型定义
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   └── tsconfig.json
└── README.md
```

---

## 3. 核心组件设计

### 3.1 Agent Execution Engine（执行引擎）

**职责：** 驱动 ReAct / Plan-and-Execute 循环，是 Agent 唯一调度中心。

**核心接口：**

```java
public interface AgentEngine {
    AgentResponse execute(AgentRequest request);
}

public record AgentRequest(
    String sessionId,
    String userInput,
    Map<String, Object> context
) {}

public record AgentResponse(
    String content,                    // 最终回复
    List<StepRecord> steps,            // 完整推理追踪
    TokenUsage totalUsage,
    long latencyMs
) {}

public record StepRecord(
    int iteration,
    StepType type,       // THOUGHT | TOOL_CALL | FINAL
    String thought,
    ToolCall toolCall,
    ToolResult toolResult
) {}
```

**ReAct 循环流程：**

```
1. User Input 进入 Engine
2. Engine → Memory.loadHistory(sessionId) 加载上下文
3. Engine → PromptEngine.build(REACT_SYSTEM, vars) 构建 Prompt
4. Engine → LlmAdapter.chat(ChatRequest + ToolDefinitions) 调用 LLM
5. LLM 返回 DecisionType:
   - THOUGHT → 继续循环
   - TOOL_CALL → Engine → ToolRegistry.execute(call) → 结果注入 → 回到步骤 3
   - FINAL → 跳出循环
6. Engine → Memory.append(msg) 保存消息
7. Engine → User 返回最终回复
```

**循环控制规则：**
- 最大迭代次数: 默认 10 轮（可配置），防止无限循环
- 每次 LLM 调用携带完整 ToolDefinitions + 最近 N 轮对话历史
- Token 使用量实时累加，超限（默认 8K output）触发截断

### 3.2 Tool Registry（工具注册与调用框架）

**职责：** 定义统一的 Tool 接口，管理工具的注册、发现、调用。

**核心接口：**

```java
public interface ToolRegistry {
    void register(ToolDefinition tool);
    Optional<ToolDefinition> get(String name);
    ToolResult execute(ToolCall call);
    List<ToolDefinition> listAll();
}

public record ToolDefinition(
    String name,                        // 工具唯一名称
    String description,                 // LLM 可理解的功能描述
    ToolParameters params,              // JSON Schema 参数定义
    Function<Map<String, Object>, ToolResult> handler  // 执行函数
) {}

public record ToolParameters(
    String type,                        // 默认 "object"
    Map<String, ParamSchema> properties,
    List<String> required
) {}

public record ToolCall(String name, Map<String, Object> arguments) {}

public record ToolResult(boolean success, String content, Map<String, Object> metadata) {}
```

**Phase 1 工具清单：**

| 工具名 | 功能 | 数据源 |
|--------|------|--------|
| OrderQueryTool | 根据订单号/用户查询订单状态、物流 | H2 orders 表 |
| ProductSearchTool | 按关键词/分类/价格搜索产品 | H2 products 表 |
| CalculatorTool | 简单数学计算（演示基础工具） | 本地计算 |

**Phase 2 扩展工具：**
- RefundTool — 创建退换货申请
- InventoryTool — 查询实时库存

### 3.3 Memory Manager（对话记忆管理）

**职责：** 管理会话的短期记忆（近期对话历史）和长期记忆（摘要），通过 H2 持久化。

**核心接口：**

```java
public interface MemoryManager {
    List<Message> loadHistory(String sessionId, int maxMessages);
    void append(String sessionId, Message msg);
    String summarize(String sessionId, int keepRecent);
    void clear(String sessionId);
}

public record Message(
    String id,
    Role role,       // USER | ASSISTANT | SYSTEM | TOOL
    String content,
    Map<String, Object> metadata,
    Instant timestamp
) {}
```

**存储策略：**
- 短期记忆: 最近 20 条消息完整保留，用于构建 LLM 上下文
- 长期记忆: 超出 20 条的部分通过 LLM 生成摘要，注入 System Prompt
- H2 持久化: 所有消息写入 `message` 表，服务重启不丢失

### 3.4 Prompt Template Engine（Prompt 模板引擎）

**职责：** 管理 Prompt 模板的注册、加载和变量注入。

**核心接口：**

```java
public interface PromptEngine {
    ChatRequest build(TemplateName name, Map<String, Object> vars);
    void register(TemplateName name, String template);
}

public enum TemplateName {
    REACT_SYSTEM,       // Agent 系统提示词（含工具描述 + 输出格式）
    TOOL_RESULT,        // 工具结果注入格式
    FINAL_SUMMARIZE     // 最终回复格式化
}

public record ChatRequest(
    String systemPrompt,
    List<Message> messages,
    List<ToolDefinition> toolsAvailable
) {}
```

**模板示例（Mustache 风格）：**

```
You are a customer service AI for ShopAI, an electronics e-commerce platform.

## Available Tools
{{#tools}}
- {{name}}: {{description}}
  Parameters: {{parameters}}
{{/tools}}

## Conversation History
{{#history}}
{{role}}: {{content}}
{{/history}}

## Current Task
User: {{userInput}}

Respond strictly in this format:
THOUGHT: [your reasoning about what to do]
ACTION: tool_name({"param": "value"})   // or FINAL if ready to answer

Rules:
- Call only one tool per ACTION
- Use FINAL only when you have all information needed
- Always cite sources when using policy documents
```

### 3.5 LLM Adapter（LLM 适配层）

**职责：** 封装 LLM 调用，支持同步/流式聊天，解析 LLM 输出为结构化决策。

**核心接口：**

```java
public interface LlmAdapter {
    LlmResponse chat(ChatRequest request);
    Flux<LlmChunk> chatStream(ChatRequest request);
}

public record LlmResponse(
    String content,
    DecisionType decision,   // THOUGHT | TOOL_CALL | FINAL
    ToolCall toolCall,
    TokenUsage usage
) {}

public enum DecisionType { THOUGHT, TOOL_CALL, FINAL }

public record TokenUsage(int inputTokens, int outputTokens) {}
```

**LangChain4j 集成方式：**
- 使用 LangChain4j 的 `ChatLanguageModel` 接口作为底层 LLM 访问通道
- Agent 不直接依赖 LangChain4j 的 Agent/Tool 高层抽象，核心逻辑自建
- LangChain4j 仅作为"模型适配器"——方便切换不同 LLM 提供商

### 3.6 RAG Pipeline（检索增强生成，Phase 2）

**职责:** 文档切片、向量化存储、语义检索、召回排序、上下文注入。

**流程：**
```
原始文档 → 切片(Chunking) → 向量化(Embedding) → 存储(H2/Pgvector)
用户提问 → 向量化 → 相似度检索 → 重排序(Rerank) → 注入 Prompt
```

### 3.7 Observability（可观测性，Phase 3）

**职责:** 日志记录、Token 用量统计、延迟监控、异常告警。

---

## 4. 前端设计

### 4.1 页面结构

```
┌──────────┬──────────────────────────────────┐
│ Sidebar  │  ChatArea                        │
│          │  ┌────────────────────────────┐  │
│ 会话列表 │  │  MessageList               │  │
│          │  │  ┌ MessageBubble (User) ┐  │  │
│ + 新会话 │  │  ├ MessageBubble (AI)   │  │  │
│          │  │  │  └ AgentSteps (展开) │  │  │
│          │  │  ├ MessageBubble (User) ┐  │  │
│          │  │  └ MessageBubble (AI)   │  │  │
│          │  └────────────────────────────┘  │
│          │  ┌ InputBar ──────────────────┐  │
│          │  │ [输入框]          [发送]   │  │
│          │  └────────────────────────────┘  │
└──────────┴──────────────────────────────────┘
```

### 4.2 React 组件树

```
<App>
  └── <ChatPage>
        ├── <Sidebar>           — 会话列表、新建/删除
        └── <ChatArea>
              ├── <MessageList>
              │     └── <MessageBubble> × N
              │           └── <AgentSteps>   — 可展开的推理步骤
              └── <InputBar>                  — 输入 + 发送
```

### 4.3 前端-后端通信

**REST API：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/send` | 发送消息，返回 messageId + streamUrl |
| GET | `/api/chat/stream/{messageId}` | SSE 流式接收 Agent 执行过程 |
| GET | `/api/sessions` | 获取会话列表 |
| GET | `/api/sessions/{id}/messages` | 获取会话消息历史 |
| POST | `/api/sessions` | 创建新会话 |
| DELETE | `/api/sessions/{id}` | 删除会话 |

**SSE 事件类型：**

| 事件 | 数据 | 说明 |
|------|------|------|
| `step` | `{iteration, type, thought?, toolName?, arguments?, result?}` | Agent 推理步骤（THOUGHT / TOOL_CALL / TOOL_RESULT） |
| `token` | `{text}` | 流式逐字输出（FINAL 阶段的实时文本） |
| `final` | `{messageId, content, steps, tokenUsage, latencyMs}` | Agent 执行完成，含完整追踪 |
| `error` | `{message, code}` | 执行异常 |

---

## 5. 数据库设计（H2）

### 5.1 表结构

```sql
-- 会话表
CREATE TABLE conversation (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    title VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE message (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,       -- USER / ASSISTANT / SYSTEM / TOOL
    content TEXT NOT NULL,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_msg_session ON message(session_id, created_at);

-- 产品表（Mock 业务数据）
CREATE TABLE product (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50),            -- phone / computer / accessory
    price DECIMAL(10, 2),
    stock INT DEFAULT 0,
    specs JSON,                      -- 规格参数
    description TEXT
);

-- 订单表（Mock 业务数据）
CREATE TABLE customer_order (
    id VARCHAR(36) PRIMARY KEY,
    order_no VARCHAR(20) UNIQUE NOT NULL,
    customer_name VARCHAR(100),
    status VARCHAR(20),              -- paid / shipped / delivered / returning / returned
    total_amount DECIMAL(10, 2),
    items JSON,                      -- 订单商品列表
    logistics VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档分块表（RAG, Phase 2）
CREATE TABLE document_chunk (
    id VARCHAR(36) PRIMARY KEY,
    doc_name VARCHAR(200),
    chunk_index INT,
    content TEXT NOT NULL,
    token_count INT,
    embedding ARRAY,                 -- 向量 (Phase 2)
    metadata JSON
);
```

### 5.2 Mock 数据规模

| 实体 | 数量 | 说明 |
|------|------|------|
| 产品 | ~50 | 手机/电脑/配件各若干 |
| 订单 | ~100 | 覆盖各种状态 |
| 政策文档 | ~10 篇 | 退换货、保修、物流、隐私等 |

---

## 6. 分阶段交付计划

### Phase 1: Core Loop（核心链路跑通）

**目标：** 搭建 Agent 执行引擎 + 2-3 个工具 + 基础记忆 + LLM 适配 + 前端对话窗口。

**交付物：**

| 模块 | 内容 |
|------|------|
| Agent Engine | ReAct 循环实现，支持 THOUGHT / TOOL_CALL / FINAL 三种决策 |
| Tool Registry | 统一 ToolDefinition 接口，OrderQueryTool + ProductSearchTool + CalculatorTool |
| Memory Manager | H2 持久化的会话记忆，支持加载最近 N 条消息 |
| Prompt Engine | 基础模板系统，支持变量注入 |
| LLM Adapter | LangChain4j 接入，支持同步 chat |
| Web Layer | REST endpoints + SSE 流式输出 |
| Frontend | React 对话窗口 + SSE 接收 + AgentSteps 显示 |
| Mock Data | 50 产品 + 100 订单初始化脚本 |

**不做：** RAG Pipeline、流式 Token 级输出、监控日志、持久化存储以外的存储方案

### Phase 2: RAG + Tools（知识驱动）

**目标：** RAG 链路 + 扩展工具生态 + Prompt 模板优化。

**交付物：**
- RAG Pipeline: 文档切片 + 向量化 + 语义检索 + 排序 + Prompt 注入
- 新工具: RefundTool + InventoryTool
- Prompt 模板优化: 多场景模板（客服/推荐/退换货）
- 向量存储方案评估与接入

### Phase 3: Production（工程化打磨）

**目标：** 可观测性 + 性能优化 + 文档完善。

**交付物：**
- Observability: 日志 + Token 统计 + 延迟监控
- 并发优化: 线程池调优 + 连接池
- 流式对话: 真正的 Token 级 SSE 流式输出
- 技术文档: API 文档 + 架构说明 + 开发指南
- 测试覆盖: 单元测试 + 集成测试

---

## 7. 前置条件与环境

### 7.1 开发环境

- JDK 17+
- Maven 3.8+
- Node.js 18+
- IDE: IntelliJ IDEA / VS Code

### 7.2 LLM 配置

通过 `application.yml` 切换 LLM 后端：

```yaml
shopai:
  llm:
    provider: openai           # openai | ollama | custom
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini         # 推荐使用轻量模型降低开发成本
    base-url: https://api.openai.com/v1
    timeout: 30s
```

### 7.3 前端代理

Vite 开发服务器配置代理到后端：

```typescript
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
})
```

---

## 8. 成功标准

1. **Phase 1 验收:** 在 React 对话窗口输入"查订单 20240611001"，Agent 自动调用 OrderQueryTool 并返回订单状态，对话历史可查
2. **Phase 2 验收:** 输入"耳机坏了怎么退"，Agent 检索退换货政策文档 + 调用 RefundTool，给出完整退换流程
3. **Phase 3 验收:** 监控面板可查看每次对话的 Token 用量和延迟，支持并发对话

---

## 附录: 与 JD 技能映射

| JD 关键词 | 项目对应 |
|-----------|---------|
| Agent 执行引擎 | `engine/AgentEngine.java` — 自建 ReAct Loop |
| 工具注册与调用框架 | `tool/ToolRegistry.java` — 统一 Tool 接入规范 |
| 对话记忆管理 | `memory/MemoryManager.java` — H2 持久化 |
| Prompt 模板引擎 | `prompt/PromptEngine.java` — 模板 + 变量注入 |
| LangChain4j | `llm/LlmAdapter.java` — 框架仅作适配层 |
| RAG 检索增强生成 | `rag/` — 切片 → 向量化 → 检索 → 注入 |
| 流式对话 | SSE endpoint + `useSSE.ts` |
| 函数调用 | LLMResponse 中 TOOL_CALL 决策解析 |
| 微服务/数据库封装为 Tool | 各 Tool 实现封装 H2 查询 |
| 监控日志 / Token 成本 | `observe/` — 非侵入式 AOP |
| ReAct / Plan-and-Execute | `engine/` — 双模式支持 |
| 高并发 | Phase 3 线程池优化 |
