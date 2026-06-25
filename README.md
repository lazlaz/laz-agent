# ShopAI Agent Platform

基于 **Java + Spring Boot + LangChain4j** 自建的轻量级 AI Agent 平台，以**电商智能客服**为业务场景，实践 AI Agent 开发核心技术栈。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.0.6 + Java 21 |
| LLM 适配 | LangChain4j 0.31.0（OpenAI 兼容协议） |
| 数据库 | H2（Embedded，PostgreSQL 兼容模式） |
| 模板引擎 | Mustache 0.9.11（Prompt 构建） |
| 前端框架 | React 18 + TypeScript 5.4 |
| 构建工具 | Vite 5.3 |
| 样式方案 | Tailwind CSS 3.4 |
| 状态管理 | Zustand 4.5 |
| 实时通信 | SSE（Server-Sent Events） |
| **向量数据库** | Chroma (Docker) |
| **Embedding 模型** | BAAI/bge-small-zh-v1.5 (Python sidecar) |
| **RAG 框架** | LangChain4j RAG API + ChromaEmbeddingStore |

## 架构概览

```
用户浏览器 (React)
      │  SSE / REST
      ▼
ChatController ── SessionController
      │
      ▼
  AgentEngine (ReAct 循环)
   │      │       │       │
   ▼      ▼       ▼       ▼
LlmAdapter  PromptEngine  MemoryManager  ToolRegistry
(LangChain4j) (Mustache)   (H2)        (3 Tools)
```

### Agent 执行流程（ReAct 模式）

```
User Input → Prompt 构建 → LLM 调用 → 响应解析
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
                THOUGHT              TOOL_CALL               FINAL
               记录思考过程          执行工具获取数据         返回最终答案
                    │                     │
                    └─────────────────────┘
                              循环迭代（max 10 次）
```

### Agent 执行流程（Plan-Execute 模式）

```
User Input → PLANNING (LLM 生成执行计划 JSON)
                  │
                  ▼  [step_start / step SSE 事件]
            EXECUTION (逐步执行: 工具调用 + 推理)
                  │
                  ▼  [token / done SSE 事件]
            SYNTHESIS (流式汇总生成最终答案)
```

前端提供模式切换下拉框（ReAct / Plan-Execute），后端通过 `mode` 参数路由。
详细 SSE 事件：`plan_start` → `plan` → `step_start` → `step` → `synthesis` → `token` → `done`

### 记忆管理

- **窗口模式** (`window`, 默认)：简单滑动窗口，最近 20 条消息
- **摘要模式** (`summarizing`)：超过阈值自动 LLM 摘要旧消息，保留最近 10 条 + 摘要

通过 `application.yml` 切换：
```yaml
shopai.agent.memory.mode: summarizing  # window | summarizing
```

## 项目结构

```
my-agent-one/
├── README.md
├── .gitignore
├── docs/
│   └── superpowers/
│       ├── specs/2026-06-11-shopai-agent-platform-design.md
│       └── plans/2026-06-11-shopai-agent-phase1.md
│
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/shopai/agent/
│       │   ├── ShopAiApplication.java          # 启动入口
│       │   ├── llm/
│       │   │   └── LangChain4jAdapter.java      # LLM 适配器（同步 + 流式）
│       │   ├── memory/
│       │   │   ├── H2MemoryManager.java         # H2 持久化记忆（ChatMemoryStore）
│       │   │   └── SummarizingMemoryProvider.java  # 摘要记忆（超窗口自动摘要）
│       │   ├── tool/
│       │   │   ├── OrderQueryTool.java          # 订单查询工具 @Tool
│       │   │   ├── ProductSearchTool.java       # 产品搜索工具 @Tool
│       │   │   ├── CalculatorTool.java          # 计算器工具 @Tool
│       │   │   └── PolicyQueryTool.java         # RAG 知识库检索 @Tool
│       │   ├── engine/
│       │   │   ├── ShopAiAgent.java             # AiServices Agent 接口（ReAct）
│       │   │   ├── ReActAgentEngine.java        # ReAct 流式引擎
│       │   │   ├── ToolRegistry.java            # 反射工具注册表（Plan-Execute）
│       │   │   ├── PlanExecuteEngine.java       # Plan-Execute 三阶段引擎
│       │   │   ├── ExecutionPlan.java           # 执行计划 record
│       │   │   ├── PlanStep.java                # 计划步骤 record
│       │   │   ├── StepResult.java              # 步骤执行结果 record
│       │   │   └── PlanExecuteEvent.java        # SSE 事件 sealed interface
│       │   ├── rag/                             # RAG 管道
│       │   │   ├── PolicyRagService.java        # RAG 检索编排
│       │   │   ├── DocumentIndexService.java    # 文档索引构建
│       │   │   ├── ParentChildChunker.java      # 父子分块
│       │   │   ├── ParentChunkStore.java        # 父块存储
│       │   │   └── Text2VecEmbeddingModel.java   # Embedding 模型适配
│       │   ├── web/
│       │   │   ├── ChatController.java          # 对话 API（REST + SSE，双模式路由）
│       │   │   ├── SessionController.java       # 会话 CRUD API
│       │   │   └── PolicyController.java        # 知识库管理 API
│       │   ├── tracing/                         # 可观测性
│       │   │   ├── OpenTelemetryConfig.java     # OTel SDK 配置
│       │   │   └── OtelChatModelListener.java   # LLM 调用追踪
│       │   └── config/
│       │       ├── AgentConfig.java             # Spring Bean 装配
│       │       └── DataInitializer.java         # 启动时注入模拟数据
│       ├── main/resources/
│       │   ├── application.yml                   # 应用配置
│       │   ├── schema.sql                        # H2 DDL（5 张表）
│       │   └── data/
│       │       ├── products.json                  # 10 条模拟产品
│       │       └── orders.json                    # 5 条模拟订单
│       └── test/java/com/shopai/agent/
│
└── frontend/
    ├── package.json
    ├── vite.config.ts
    ├── tailwind.config.js
    ├── tsconfig.json
    ├── index.html
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── index.css
        ├── types/index.ts                       # TypeScript 类型定义
        ├── api/chat.ts                          # REST API 客户端
        ├── store/chatStore.ts                   # Zustand 状态管理
        ├── hooks/useSSE.ts                      # SSE 流式接收 Hook
        └── components/
            ├── Sidebar.tsx                      # 侧边栏（会话列表）
            ├── ChatArea.tsx                     # 对话区域容器
            ├── MessageList.tsx                  # 消息列表（自动滚动）
            ├── MessageBubble.tsx                # 消息气泡（用户/Agent）
            ├── AgentSteps.tsx                   # Agent 执行步骤展示
            ├── InputBar.tsx                     # 输入栏（自适应高度）
            └── LoadingDots.tsx                  # 等待动画
```

## 快速开始

### 环境要求

- **JDK 21+**
- **Maven 3.8+**
- **Node.js 18+**
- **OpenAI API Key**（或其他兼容 API，如 DeepSeek、通义千问）

### 前置依赖 (RAG 模块)

1. **Docker Desktop** — 启动 Chroma 向量数据库:
   ```bash
   docker pull chromadb/chroma
   docker run -d -p 8000:8000 chromadb/chroma
   ```

2. **Python 3.9+** — 安装 Embedding sidecar 依赖:
   ```bash
   cd backend
   pip install -r requirements-sidecar.txt
   ```

3. **模型文件** — `BAAI/bge-small-zh-v1.5` 存放于 `D:\models\huggingface`

4. **启动 Embedding Sidecar** (先于后端启动):
   ```bash
   cd backend
   set MODEL_PATH=D:\models\huggingface
   set MODEL_NAME=BAAI/bge-small-zh-v1.5
   set PORT=9876
   python embedding_sidecar.py
   ```
   确认就绪: `curl http://localhost:9876/health` → `{"status":"ok"}`

### 1. 配置 LLM

编辑 `backend/src/main/resources/application.yml`：

```yaml
shopai:
  llm:
    provider: openai
    api-key: ${OPENAI_API_KEY:sk-your-api-key-here}  # 或设置环境变量
    model: gpt-4o-mini    # 可选: gpt-3.5-turbo, deepseek-chat 等
    base-url: https://api.openai.com/v1  # 可替换为兼容 API 地址
```

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

启动成功标志：
```
Embedding sidecar configured at http://127.0.0.1:9876
Loaded 10/10 products
Loaded 5/5 orders
Started ShopAiApplication in 1.975 seconds
```

后端运行在 **http://localhost:8080**，H2 控制台在 **http://localhost:8080/h2-console**。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 **http://localhost:3000**，API 请求自动代理到后端 8080 端口。

### 4. 运行测试

```bash
cd backend
mvn test
# 预期: 8 tests, 0 failures
```

## API 文档

### 会话管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/sessions` | 获取所有会话 |
| `POST` | `/api/sessions` | 创建新会话 → `{"sessionId":"uuid"}` |
| `GET` | `/api/sessions/{id}/messages` | 获取会话历史消息 |
| `DELETE` | `/api/sessions/{id}` | 删除会话及消息 |

### 对话

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/chat/send` | 发送消息 → `{"messageId":"uuid","streamUrl":"..."}` |
| `GET` | `/api/chat/stream/{messageId}` | SSE 流式接收 Agent 执行步骤 |

### SSE 事件

```javascript
// step 事件 — Agent 每一步执行结果
event: step
data: {"iteration":1,"type":"TOOL_CALL","toolName":"ProductSearchTool","arguments":{"keyword":"iPhone"}}

// final 事件 — 最终回复
event: final
data: {"messageId":"xxx","content":"您好，iPhone 15...","steps":[...],"tokenUsage":{...},"latencyMs":1234}
```

### 知识库管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/knowledge/documents` | 获取已上传的文档列表 |
| `POST` | `/api/knowledge/upload` | 上传政策文档（.md / .txt） |
| `DELETE` | `/api/knowledge/documents/{id}` | 删除指定文档 |
| `POST` | `/api/knowledge/rebuild` | 重建向量索引 |
| `GET` | `/api/knowledge/chunks/{docId}` | 预览文档分块（待增强） |

## 配置文件

```yaml
# backend/src/main/resources/application.yml
server.port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/shopai;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
  sql.init.mode: always
  sql.init.schema-locations: classpath:schema.sql

shopai:
  llm:
    provider: openai
    api-key: ${OPENAI_API_KEY}       # 支持环境变量
    model: gpt-4o-mini               # 可替换为任意 OpenAI 兼容模型
    base-url: https://api.openai.com/v1
    timeout: 30s
  agent:
    max-iterations: 10               # ReAct 循环最大迭代次数
    max-history-messages: 20         # 上下文窗口最大消息数
```

## 可用工具

Agent 拥有 3 个工具，会根据用户意图自动选择和调用：

| 工具 | 功能 | 示例 |
|------|------|------|
| `ProductSearchTool` | 搜索产品（关键词/分类/价格） | "有没有 5000 以下的手机？" |
| `OrderQueryTool` | 查询订单（订单号/客户名） | "查一下我的订单 20240611001" |
| `CalculatorTool` | 数学计算 | "iPhone 15 打 9 折多少钱？" |
| `PolicyQueryTool` | RAG 知识库检索（退换货/保修/物流政策） | "退货政策是什么？" |

## H2 数据库

5 张核心表：

| 表名 | 说明 |
|------|------|
| `conversation` | 会话（session_id, title） |
| `message` | 消息（role, content, metadata JSON） |
| `product` | 产品（name, category, price, stock, specs JSON） |
| `customer_order` | 订单（order_no, status, items JSON, logistics） |

H2 控制台：启动后访问 `http://localhost:8080/h2-console`  
JDBC URL: `jdbc:h2:file:./data/shopai`  
用户名: `sa`，密码: 空

## 开发路线

### Phase 1: 核心闭环 ✅ 已完成
- [x] 领域模型（15 个 record/enum）
- [x] LLM 适配器（LangChain4j）
- [x] Prompt 引擎（Mustache）
- [x] 记忆管理器（H2）
- [x] 工具注册表 + 3 个工具
- [x] ReAct Agent 引擎
- [x] REST + SSE Web 层
- [x] React 对话 UI
- [x] 单元测试 + 集成测试（8 tests）

### Phase 2: RAG + 工具增强 ✅ 已完成
- [x] Chroma 向量数据库 + BAAI/bge-small-zh-v1.5 Embedding
- [x] 知识库检索工具（PolicyQueryTool + PolicyRagService）
- [x] 文档索引管理（DocumentIndexService + EmbeddingStoreIngestor）
- [x] 知识库管理页面（上传/删除/重建索引）
- [x] 父子分块检索 + PDF/Word 文档支持
- [x] **Plan-and-Execute 双模式引擎**（Planning → Execution → Synthesis）
- [x] **多轮对话上下文优化**（SummarizingMemoryProvider — 超窗口自动摘要）
- [ ] 工具数量扩展（退款、售后、物流追踪）

### Phase 3: 生产化 （计划中）
- [ ] Docker 容器化
- [ ] 配置外部化（Nacos/Consul）
- [ ] 监控与指标（Prometheus + Grafana）
- [ ] 限流与熔断
- [ ] 用户认证

## JD 技能覆盖

本项目实践覆盖了 AI Agent 岗位的核心要求：

| JD 要求 | 对应实践 |
|---------|---------|
| Agent 核心模块开发 | ReActAgentEngine + **PlanExecuteEngine** — ReAct / Plan-Execute 双模式 |
| AI 能力集成 | LangChain4jAdapter — 统一 LLM 调用接口，支持模型切换 |
| RAG 与知识库 | Chroma + BAAI/bge-small-zh-v1.5 + PolicyRagService + 父子分块 |
| 工具生态建设 | ToolRegistry + 4 个工具（LangChain4j @Tool + 反射注册双模式）|
| **对话记忆管理** | **SummarizingMemoryProvider — 超窗口自动摘要 + 长期记忆** |
| 可观测性 | OpenTelemetry + Langfuse 链路追踪 + Token 统计 |
| 流式对话 | SSE Token 级流式输出 |
| 工程能力 | Spring Boot + H2 + SSE + React + TypeScript |
