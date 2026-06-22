# ShopAI Agent Platform — 开发者入职指南

> 自动生成自知识图谱 (`.understand-anything/knowledge-graph.json`)  
> 生成时间: 2026-06-15 | Git Commit: `4f26453`

---

## 1. 项目概览

| 属性 | 值 |
|------|-----|
| **项目名** | shopai-agent |
| **描述** | 基于 Java + Spring Boot + LangChain4j 构建的轻量级 AI Agent 平台，以电商智能客服为业务场景，实现 LLM 调用、工具注册与调度、ReAct 推理循环等核心 Agent 能力，前端采用 React + TypeScript + Tailwind CSS 提供交互界面 |
| **语言** | Java (主), TypeScript, Mustache, SQL, YAML, JSON, CSS, HTML, JavaScript, Markdown |
| **框架** | Spring Boot 4.0.6, LangChain4j 1.16.2, React 18, Vite 5.3, Tailwind CSS 3.4, Zustand 4.5, Mustache 0.9.11 |
| **复杂度** | moderate (71 个文件, 115 知识节点, 221 关系边) |

### 核心架构

```
用户浏览器 (React + SSE)
      │  REST / SSE
      ▼
ChatController ── SessionController
      │
      ▼
  AgentEngine (ReAct 循环, max 10 次)
   │      │       │       │
   ▼      ▼       ▼       ▼
LlmAdapter  PromptEngine  MemoryManager  ToolRegistry
(LangChain4j) (Mustache)   (H2)        (3 Tools)
```

**ReAct 执行流程:**
```
User Input → Prompt 构建 → LLM 调用 → 响应解析
                                          │
              ┌───────────────────────────┼───────────────────────────┐
              ▼                           ▼                           ▼
          THOUGHT                     TOOL_CALL                     FINAL
         记录思考过程                 执行工具获取数据              返回最终答案
              │                           │
              └───────────────────────────┘
                        循环迭代（≤10 次）
```

---

## 2. 架构层

### API 层 (`layer-api`)
**REST API 端点层** — ChatController（异步消息发送与 SSE 流式推送）、SessionController（会话 CRUD），作为前端与后端服务层之间的桥梁。

| 文件 | 职责 |
|------|------|
| `ChatController.java` | `POST /api/chat/send` + `GET /api/chat/stream/{messageId}` (SSE) |
| `SessionController.java` | `GET/POST/DELETE /api/sessions` 会话 CRUD |

### 服务层 (`layer-service`)
**核心业务逻辑与 AI Agent 执行引擎** — 基于接口-实现分离的适配器模式设计，封装 Agent 推理循环的核心逻辑。

| 文件 | 职责 |
|------|------|
| `AgentEngine.java` | Agent 引擎接口，定义 `execute(AgentRequest) -> AgentResponse` |
| `ReActAgentEngine.java` | ⚡ **核心** — ReAct 循环实现 (THOUGHT/TOOL_CALL/FINAL) |
| `LlmResponseParser.java` | LLM 原始文本 → 结构化 LlmResponse 解析 |
| `LlmAdapter.java` | LLM 适配器接口 |
| `LangChain4jAdapter.java` | LangChain4j 实现，封装 OpenAI 兼容 API 调用 |
| `PromptEngine.java` | Prompt 引擎接口 |
| `MustachePromptEngine.java` | Mustache 模板实现，预编译模板缓存 |
| `MemoryManager.java` | 会话记忆管理接口 |
| `H2MemoryManager.java` | H2 数据库实现 (JdbcTemplate) |
| `react-system.mustache` | ReAct 系统 Prompt 模板 |
| `tool-result.mustache` | 工具结果注入模板 |

### 数据层 (`layer-data`)
**领域模型与数据定义层** — Java Record 不可变数据载体 + 数据库 DDL + Mock 种子数据。

| 分类 | 文件 |
|------|------|
| 消息系统 | `Message.java`, `Role.java` |
| Agent 交互 | `AgentRequest.java`, `AgentResponse.java`, `StepRecord.java` |
| LLM 通信 | `ChatRequest.java`, `LlmResponse.java`, `DecisionType.java` |
| 工具体系 | `ToolDefinition.java`, `ToolCall.java`, `ToolResult.java`, `ToolParameters.java`, `ParamSchema.java` |
| 执行追踪 | `StepType.java`, `TokenUsage.java` |
| 数据库 | `schema.sql` (4 张表: conversation, message, product, customer_order) |
| 种子数据 | `products.json` (10款), `orders.json` (5条) |

### 工具层 (`layer-tool`)
**Agent 工具实现与注册中心** — 3 个电商业务工具，线程安全注册表。

| 文件 | 职责 |
|------|------|
| `ToolRegistry.java` | 工具注册表接口 |
| `DefaultToolRegistry.java` | ConcurrentHashMap 线程安全实现 |
| `ProductSearchTool.java` | 产品搜索 (keyword/category/price) |
| `OrderQueryTool.java` | 订单查询 (byOrderNo/byCustomerName) |
| `CalculatorTool.java` | 数学表达式求值 (ScriptEngine) |

### RAG 模块 (`com.shopai.agent.rag`)

**向量检索 + 知识库管理层** — 基于 Chroma + BAAI/bge-small-zh-v1.5 实现政策文档的向量化存储与语义检索。

| 文件 | 职责 |
|------|------|
| `Text2VecEmbeddingModel.java` | 实现 LC4j `EmbeddingModel`，通过 HTTP 连接用户自管的 Python sidecar |
| `PolicyRagService.java` | 检索编排: 向量化查询 → Chroma 搜索 → 结果格式化 |
| `DocumentIndexService.java` | 使用 `EmbeddingStoreIngestor` 构建索引：加载文档 → 分段 → 向量化 → 存入 Chroma |
| `ResultFormatter.java` | 将 `EmbeddingMatch` 列表格式化为 LLM 可读的政策条款文本 |
| `PolicyQueryTool.java` | LC4j `@Tool` 注解工具，供 Agent 在需要时调用 RAG 检索 |
| `PolicyController.java` | REST API: 文档上传/删除/列表 + 索引重建 + 分块预览 |

**数据流:**
```
政策 .md 文档 → DocumentIndexService → EmbeddingStoreIngestor → Chroma
用户提问 → PolicyRagService → Text2VecEmbeddingModel → Chroma 查询 → ResultFormatter → LLM
```

### 配置层 (`layer-config`)
**应用配置与工程基础设施** — Bean 装配、数据初始化、构建配置。

| 文件 | 职责 |
|------|------|
| `AgentConfig.java` | ⚡ **装配中枢** — 5 个 @Bean 方法连接全部组件 |
| `DataInitializer.java` | 启动时加载 JSON 种子数据到 H2 |
| `application.yml` | LLM/DB/Agent 参数配置 |
| `pom.xml` | Maven 依赖管理 |
| `package.json` | 前端依赖 + 脚本 |
| `tsconfig.json` | TypeScript 严格模式配置 |
| `vite.config.ts` | Vite 构建 + API 代理 |
| `tailwind.config.js` | Tailwind CSS 配置 |

### UI 层 (`layer-ui`)
**React 前端用户界面** — TypeScript + Vite + Tailwind + Zustand。

| 文件 | 职责 |
|------|------|
| `main.tsx` | ReactDOM.createRoot 入口 |
| `App.tsx` | 根组件 (Flex 布局: Sidebar + ChatArea) |
| `chatStore.ts` | Zustand 全局状态 (sessions/messages/steps/isStreaming) |
| `useSSE.ts` | SSE EventSource 自定义 Hook |
| `chat.ts` | REST API 客户端 |
| `types/index.ts` | TypeScript 接口定义 |
| `Sidebar.tsx` | 会话列表 + CRUD (105 行) |
| `ChatArea.tsx` | 聊天区域容器 |
| `MessageList.tsx` | 消息列表 (自动滚动 + 空状态) |
| `MessageBubble.tsx` | 消息气泡 (角色样式 + Agent 步骤) |
| `InputBar.tsx` | 输入栏 (多行 + Enter 发送 + 自动伸缩, 83 行) |
| `AgentSteps.tsx` | Agent 执行步骤可视化 |
| `LoadingDots.tsx` | 等待动画 |

### 入口点 (`layer-entry`)
**应用启动入口** — Spring Boot + React 入口。

| 文件 | 职责 |
|------|------|
| `ShopAiApplication.java` | Spring Boot main 入口 |
| `frontend/src/main.tsx` | React DOM 挂载 |
| `frontend/index.html` | SPA 外壳 |

### 测试层 (`layer-test`)
**自动化测试** — JUnit 5 + Mockito + @WebMvcTest。

| 文件 | 覆盖 |
|------|------|
| `ReActAgentEngineTest.java` | ReAct 循环 FINAL/TOOL_CALL 两条路径 |
| `ChatControllerTest.java` | API 端点 MockMvc 验证 |
| `ToolRegistryTest.java` | 工具注册表 CRUD + 执行 (5 tests) |

### 文档层 (`layer-doc`)
**项目文档** — 全部中文撰写。

| 文件 | 内容 |
|------|------|
| `README.md` | 项目总览 / 技术栈 / 架构图 / 快速启动 / API 文档 |
| `phase1.md` | Phase 1 实现计划 (3262 行, 13 个任务) |
| `platform-design.md` | 平台设计文档 (579 行, 8 大章节) |

---

## 3. 核心概念

### 设计模式

| 模式 | 应用场景 |
|------|----------|
| **适配器模式** | `LlmAdapter` 接口 + `LangChain4jAdapter` 实现 — 封装不同 LLM 提供商差异 |
| **策略模式** | `AgentEngine` / `PromptEngine` / `MemoryManager` / `ToolRegistry` 全部基于接口-实现分离 |
| **注册表模式** | `ToolRegistry` — 统一管理工具的定义和执行 |
| **依赖注入 (IoC)** | Spring @Configuration + @Bean 装配全部组件链 (AgentConfig) |
| **观察者模式** | SSE (Server-Sent Events) 流式推送 Agent 执行步骤 |

### AI Agent 核心概念

| 概念 | 说明 |
|------|------|
| **ReAct 循环** | Reasoning + Acting: LLM 在每轮迭代中选择 THOUGHT(思考)/TOOL_CALL(调用工具)/FINAL(最终答案), 最多 10 轮 |
| **Function Calling** | LLM 通过 JSON Schema 定义的工具接口调用真实的业务代码 |
| **Prompt 工程** | Mustache 模板构建系统提示词，注入角色/工具/历史/用户输入 |
| **对话记忆** | H2 持久化会话历史，按 sessionId 管理上下文窗口 (max 20 条) |
| **流式通信** | SSE 单向推送，逐步骤展示 Agent 推理过程 |

### 技术要点

- **Java Record**: 15 个不可变 DTO，线程安全，零样板代码
- **ConcurrentHashMap**: 工具注册表和 ChatController 临时数据容器的线程安全保障
- **JdbcTemplate**: Spring 对 JDBC 的轻量封装，参数化查询防 SQL 注入
- **Zustand**: 极简全局状态管理，无 Provider/Reducer 概念
- **EventSource API**: 浏览器内置 SSE 客户端，自动重连
- **Vite Proxy**: 开发环境 API 代理解决 CORS

---

## 4. 学习路径 (Guided Tour)

建议按以下 11 步顺序阅读代码，每步建立在前一步的理解之上：

| # | 主题 | 核心文件 | 要点 |
|---|------|----------|------|
| 1 | **项目概览** | `README.md` | 理解项目定位、技术栈、架构图 |
| 2 | **后端启动入口** | `ShopAiApplication.java` | Spring Boot 引导，@SpringBootApplication |
| 3 | **核心领域模型** | `domain/*.java` (15 个 Record) | 不可变数据契约，五大分类 |
| 4 | **Agent 引擎** | `ReActAgentEngine.java`, `AgentEngine.java`, `LlmResponseParser.java` | ⚡ ReAct 循环核心 |
| 5 | **LLM 适配器** | `LlmAdapter.java`, `LangChain4jAdapter.java` | 适配器模式封装大模型 |
| 6 | **Prompt 引擎** | `PromptEngine.java`, `MustachePromptEngine.java`, `*.mustache` | Mustache 模板构建提示词 |
| 7 | **记忆管理器** | `MemoryManager.java`, `H2MemoryManager.java`, `schema.sql` | H2 持久化对话历史 |
| 8 | **工具系统** | `ToolRegistry.java`, `DefaultToolRegistry.java`, 4 个 Tool | Agent 的行动能力 |
| 8.5 | **RAG 模块** | `Text2VecEmbeddingModel.java`, `PolicyRagService.java`, `DocumentIndexService.java` | 向量检索 + 知识库管理 |
| 9 | **Web API 层** | `ChatController.java`, `SessionController.java`, `PolicyController.java` | REST + SSE 通信 |
| 10 | **前端 UI** | `App.tsx`, 7 组件, `useSSE.ts`, `chatStore.ts` | React 聊天界面 |
| 11 | **配置装配与测试** | `AgentConfig.java`, `application.yml`, 3 个 Test | 全栈串联 + 验证 |

---

## 5. 复杂度热点

以下文件复杂度较高，新开发者需重点关注：

| 文件 | 复杂度 | 原因 |
|------|--------|------|
| `ReActAgentEngine.java` | **complex** | ReAct 循环主逻辑，包含多轮迭代、状态分发、超时处理 |
| `ChatController.java` | moderate | 异步线程池 + SSE 轮询 + ConcurrentHashMap 管理 |
| `DataInitializer.java` | moderate | JSON 解析 + JdbcTemplate upsert + @Transactional |
| `LlmResponseParser.java` | moderate | LLM 原始文本解析，字符串匹配 + JSON 反序列化 |
| `H2MemoryManager.java` | moderate | JdbcTemplate SQL + Jackson 序列化 + 结果集映射 |
| `ProductSearchTool.java` | moderate | 动态 SQL 拼接 + 参数化查询 (防注入) |
| `Sidebar.tsx` | moderate | 105 行前端最复杂组件，会话 CRUD + 状态联动 |
| `InputBar.tsx` | moderate | 83 行多行输入 + Enter 发送 + SSE 集成 |
| `Text2VecEmbeddingModel.java` | low | HTTP 调用用户自管的 Python sidecar，无进程管理 |
| `PolicyRagService.java` | moderate | 向量化 → Chroma 检索 → 结果格式化全流程编排 |

---

## 6. 快速开始

### 环境要求

- **JDK 21+** | **Maven 3.8+** | **Node.js 18+** | **OpenAI API Key** (或兼容 API) | **Docker Desktop** (Chroma) | **Python 3.9+** (embedding sidecar)

### 启动步骤

```bash
# 1. 配置 LLM (编辑 application.yml 中的 api-key)
# 2. 启动后端
cd backend && mvn spring-boot:run
# → http://localhost:8080

# 3. 启动前端
cd frontend && npm install && npm run dev
# → http://localhost:3000

# 4. 运行测试
cd backend && mvn test
# → 预期: 8 tests, 0 failures
```

### 关键端点

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat/send` | 发送消息 → `{messageId, streamUrl}` |
| `GET` | `/api/chat/stream/{messageId}` | SSE 流式接收 Agent 步骤 |
| `GET` | `/api/sessions` | 获取所有会话 |
| `POST` | `/api/sessions` | 创建新会话 |
| `DELETE` | `/api/sessions/{id}` | 删除会话 |

---

## 7. 开发路线

- **Phase 1** ✅ 已完成 — 核心闭环 (15 领域模型 + 引擎 + 适配器 + 工具 + Web + UI + 测试)
- **Phase 2** ✅ 已完成 — RAG (Chroma + BAAI/bge-small-zh-v1.5 + PolicyRagService) + 知识库管理 UI + PolicyQueryTool
- **Phase 3** 🔜 计划中 — Docker 容器化 + 监控 + 限流 + 认证

---

> 🤖 本指南由 [Understand Anything](https://github.com/Egonex-AI/Understand-Anything) 自动生成。  
> 知识图谱: `.understand-anything/knowledge-graph.json` (115 节点 / 221 边 / 9 层)
