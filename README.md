# ShopAI Agent Platform

基于 **Java + Spring Boot + LangChain4j** 自建的轻量级 AI Agent 平台，以**电商智能客服**为业务场景，实践 AI Agent 开发核心技术栈。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.3.0 + Java 17 |
| LLM 适配 | LangChain4j 0.31.0（OpenAI 兼容协议） |
| 数据库 | H2（Embedded，PostgreSQL 兼容模式） |
| 模板引擎 | Mustache 0.9.11（Prompt 构建） |
| 前端框架 | React 18 + TypeScript 5.4 |
| 构建工具 | Vite 5.3 |
| 样式方案 | Tailwind CSS 3.4 |
| 状态管理 | Zustand 4.5 |
| 实时通信 | SSE（Server-Sent Events） |

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
│       │   ├── domain/                          # 领域模型（15 个 record/enum）
│       │   │   ├── Message.java, Role.java, AgentRequest.java, AgentResponse.java
│       │   │   ├── ToolDefinition.java, ToolCall.java, ToolResult.java
│       │   │   ├── LlmResponse.java, DecisionType.java, TokenUsage.java
│       │   │   └── StepType.java, StepRecord.java, ChatRequest.java
│       │   ├── llm/
│       │   │   ├── LlmAdapter.java              # LLM 适配器接口
│       │   │   └── LangChain4jAdapter.java      # LangChain4j 实现
│       │   ├── prompt/
│       │   │   ├── PromptEngine.java            # Prompt 引擎接口
│       │   │   └── MustachePromptEngine.java    # Mustache 实现
│       │   ├── memory/
│       │   │   ├── MemoryManager.java           # 记忆管理器接口
│       │   │   └── H2MemoryManager.java         # H2 实现
│       │   ├── tool/
│       │   │   ├── ToolRegistry.java            # 工具注册表接口
│       │   │   ├── DefaultToolRegistry.java     # 基于 ConcurrentHashMap 实现
│       │   │   ├── OrderQueryTool.java          # 订单查询工具
│       │   │   ├── ProductSearchTool.java       # 产品搜索工具
│       │   │   └── CalculatorTool.java          # 计算器工具
│       │   ├── engine/
│       │   │   ├── AgentEngine.java             # Agent 引擎接口
│       │   │   ├── ReActAgentEngine.java        # ReAct 循环实现
│       │   │   └── LlmResponseParser.java       # LLM 响应解析器
│       │   ├── web/
│       │   │   ├── ChatController.java          # 对话 API（REST + SSE）
│       │   │   └── SessionController.java       # 会话 CRUD API
│       │   └── config/
│       │       ├── AgentConfig.java             # Spring Bean 装配
│       │       └── DataInitializer.java         # 启动时注入模拟数据
│       ├── main/resources/
│       │   ├── application.yml                   # 应用配置
│       │   ├── schema.sql                        # H2 DDL（5 张表）
│       │   ├── data/
│       │   │   ├── products.json                  # 10 条模拟产品
│       │   │   └── orders.json                    # 5 条模拟订单
│       │   └── prompts/
│       │       ├── react-system.mustache          # ReAct 系统 Prompt 模板
│       │       └── tool-result.mustache           # 工具结果注入模板
│       └── test/java/com/shopai/agent/
│           ├── tool/ToolRegistryTest.java
│           ├── engine/ReActAgentEngineTest.java
│           └── web/ChatControllerTest.java
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

- **JDK 17+**（推荐 JDK 17）
- **Maven 3.8+**
- **Node.js 18+**
- **OpenAI API Key**（或其他兼容 API，如 DeepSeek、通义千问）

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

### Phase 2: RAG + 工具增强 （计划中）
- [ ] 向量数据库（document_chunk 表 + embedding）
- [ ] 知识库检索工具
- [ ] 工具数量扩展（退款、售后、物流追踪）
- [ ] 多轮对话上下文优化

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
| Agent 核心模块开发 | ReActAgentEngine — THOUGHT/TOOL_CALL/FINAL 循环 |
| AI 能力集成 | LangChain4jAdapter — 统一 LLM 调用接口，支持模型切换 |
| RAG 与知识库 | Prompt Engine + Memory Manager（Phase 2 扩展向量检索）|
| 工具生态建设 | ToolRegistry + 3 个工具（JSON Schema 参数 + Function Handler）|
| 性能与稳定性 | Token 统计、延迟追踪、错误处理 |
| 工程能力 | Spring Boot + H2 + SSE + React + TypeScript + 测试体系 |
