# ShopAI Agent Platform — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 1 core loop — Agent Engine with ReAct pattern, Tool Registry with 3 tools, H2-backed Memory, Prompt Engine, LangChain4j LLM Adapter, REST+SSE web layer, and React chat UI.

**Architecture:** Bottom-up build. Shared domain models first, then each component independently (LLM → Prompt → Memory → Tool → Engine), then web layer wires them together, finally React frontend consumes the SSE API. Each component is interface-defined and Spring-managed.

**Tech Stack:** Java 17, Spring Boot 3.x, LangChain4j, H2, JdbcTemplate, Maven; React 18, TypeScript, Vite, Tailwind CSS, Zustand, EventSource (SSE).

---

## File Structure (Phase 1)

### Backend (`backend/`)

```
backend/
├── pom.xml
└── src/
    ├── main/java/com/shopai/agent/
    │   ├── ShopAiApplication.java              ← @SpringBootApplication
    │   ├── domain/                              ← Shared domain types
    │   │   ├── Message.java
    │   │   ├── Role.java
    │   │   ├── ToolDefinition.java
    │   │   ├── ToolCall.java
    │   │   ├── ToolResult.java
    │   │   ├── ChatRequest.java
    │   │   ├── LlmResponse.java
    │   │   ├── DecisionType.java
    │   │   ├── StepType.java
    │   │   ├── StepRecord.java
    │   │   ├── AgentRequest.java
    │   │   ├── AgentResponse.java
    │   │   └── TokenUsage.java
    │   ├── llm/                                 ← LLM adapter layer
    │   │   ├── LlmAdapter.java                  (interface)
    │   │   └── LangChain4jAdapter.java          (impl)
    │   ├── prompt/                              ← Prompt template engine
    │   │   ├── PromptEngine.java                (interface)
    │   │   └── MustachePromptEngine.java        (impl)
    │   ├── memory/                              ← Conversation memory
    │   │   ├── MemoryManager.java               (interface)
    │   │   └── H2MemoryManager.java             (impl)
    │   ├── tool/                                ← Tool registry + tools
    │   │   ├── ToolRegistry.java                (interface)
    │   │   ├── DefaultToolRegistry.java         (impl)
    │   │   ├── OrderQueryTool.java
    │   │   ├── ProductSearchTool.java
    │   │   └── CalculatorTool.java
    │   ├── engine/                              ← Agent execution engine
    │   │   ├── AgentEngine.java                 (interface)
    │   │   ├── ReActAgentEngine.java            (impl)
    │   │   └── LlmResponseParser.java           (parse THOUGHT/ACTION/FINAL)
    │   ├── web/                                 ← REST + SSE
    │   │   ├── ChatController.java
    │   │   └── SessionController.java
    │   └── config/                              ← Spring configuration
    │       ├── AgentConfig.java                 (bean wiring)
    │       └── DataInitializer.java             (mock data seed)
    └── main/resources/
        ├── application.yml
        ├── schema.sql
        ├── data/
        │   ├── products.json
        │   └── orders.json
        └── prompts/
            └── react-system.mustache

backend/src/test/java/com/shopai/agent/
    ├── tool/
    │   └── ToolRegistryTest.java
    ├── engine/
    │   └── ReActAgentEngineTest.java
    └── web/
        └── ChatControllerTest.java
```

### Frontend (`frontend/`)

```
frontend/
├── package.json
├── vite.config.ts
├── tailwind.config.js
├── tsconfig.json
├── index.html
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── index.css
    ├── types/
    │   └── index.ts
    ├── api/
    │   └── chat.ts
    ├── store/
    │   └── chatStore.ts
    ├── hooks/
    │   └── useSSE.ts
    └── components/
        ├── Sidebar.tsx
        ├── ChatArea.tsx
        ├── MessageList.tsx
        ├── MessageBubble.tsx
        ├── AgentSteps.tsx
        ├── InputBar.tsx
        └── LoadingDots.tsx
```

---

## Task 1: Backend Project Scaffolding

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/shopai/agent/ShopAiApplication.java`
- Create: `backend/src/main/resources/application.yml`

- [ ] **Step 1: Generate Spring Boot project with Maven**

Run: `cd backend` (create the directory first)

Create `backend/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
        <relativePath/>
    </parent>
    <groupId>com.shopai</groupId>
    <artifactId>shopai-agent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>ShopAI Agent Platform</name>
    <description>Mini AI Agent Platform — E-commerce Customer Service</description>

    <properties>
        <java.version>17</java.version>
        <langchain4j.version>0.31.0</langchain4j.version>
    </properties>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>

        <!-- H2 Database -->
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- LangChain4j -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Mustache (for prompt templates) -->
        <dependency>
            <groupId>com.github.spullara.mustache.java</groupId>
            <artifactId>compiler</artifactId>
            <version>0.9.11</version>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create Spring Boot application entry point**

Create `backend/src/main/java/com/shopai/agent/ShopAiApplication.java`:

```java
package com.shopai.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShopAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopAiApplication.class, args);
    }
}
```

- [ ] **Step 3: Create application configuration**

Create `backend/src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/shopai;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

shopai:
  llm:
    provider: openai
    api-key: ${OPENAI_API_KEY:sk-placeholder}
    model: gpt-4o-mini
    base-url: https://api.openai.com/v1
    timeout: 30s
  agent:
    max-iterations: 10
    max-history-messages: 20
```

- [ ] **Step 4: Build and verify project compiles**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/pom.xml backend/src/
git commit -m "feat: scaffold Spring Boot backend project"
```

---

## Task 2: Frontend Project Scaffolding

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tailwind.config.js`
- Create: `frontend/tsconfig.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/index.css`

- [ ] **Step 1: Create package.json**

Run: `mkdir -p frontend/src`

Create `frontend/package.json`:

```json
{
  "name": "shopai-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "zustand": "^4.5.2"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@vitejs/plugin-react": "^4.3.0",
    "autoprefixer": "^10.4.19",
    "postcss": "^8.4.38",
    "tailwindcss": "^3.4.4",
    "typescript": "^5.4.5",
    "vite": "^5.3.1"
  }
}
```

- [ ] **Step 2: Install dependencies**

Run: `cd frontend && npm install`
Expected: packages installed without errors

- [ ] **Step 3: Create Vite config with proxy**

Create `frontend/vite.config.ts`:

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
});
```

- [ ] **Step 4: Create Tailwind config**

Create `frontend/tailwind.config.js`:

```javascript
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {},
  },
  plugins: [],
};
```

- [ ] **Step 5: Create PostCSS config**

Create `frontend/postcss.config.js`:

```javascript
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
```

- [ ] **Step 6: Create tsconfig.json**

Create `frontend/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"]
}
```

- [ ] **Step 7: Create index.html**

Create `frontend/index.html`:

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>ShopAI - 智能客服</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 8: Create entry files**

Create `frontend/src/main.tsx`:

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

Create `frontend/src/App.tsx`:

```tsx
export default function App() {
  return (
    <div className="h-screen flex items-center justify-center bg-gray-100">
      <h1 className="text-3xl font-bold text-gray-700">ShopAI Agent Platform</h1>
    </div>
  );
}
```

Create `frontend/src/index.css`:

```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

- [ ] **Step 9: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 10: Verify dev server starts**

Run: `cd frontend && npx vite --host 2>&1 | head -5` (Ctrl+C after confirming it starts)
Expected: `Local: http://localhost:3000/`

- [ ] **Step 11: Commit**

```bash
git add frontend/
git commit -m "feat: scaffold React + Vite + Tailwind frontend project"
```

---

## Task 3: Domain Models

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/domain/Role.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/Message.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/ToolDefinition.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/ToolCall.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/ToolResult.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/ChatRequest.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/LlmResponse.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/DecisionType.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/StepType.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/StepRecord.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/AgentRequest.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/AgentResponse.java`
- Create: `backend/src/main/java/com/shopai/agent/domain/TokenUsage.java`

- [ ] **Step 1: Create Role enum**

Create `backend/src/main/java/com/shopai/agent/domain/Role.java`:

```java
package com.shopai.agent.domain;

public enum Role {
    USER, ASSISTANT, SYSTEM, TOOL
}
```

- [ ] **Step 2: Create Message record**

Create `backend/src/main/java/com/shopai/agent/domain/Message.java`:

```java
package com.shopai.agent.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Message(
    String id,
    Role role,
    String content,
    Map<String, Object> metadata,
    Instant timestamp
) {
    public static Message of(Role role, String content) {
        return new Message(
            UUID.randomUUID().toString(),
            role,
            content,
            Map.of(),
            Instant.now()
        );
    }

    public static Message of(Role role, String content, Map<String, Object> metadata) {
        return new Message(
            UUID.randomUUID().toString(),
            role,
            content,
            metadata,
            Instant.now()
        );
    }
}
```

- [ ] **Step 3: Create ToolDefinition, ToolCall, ToolResult records**

Create `backend/src/main/java/com/shopai/agent/domain/ToolDefinition.java`:

```java
package com.shopai.agent.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record ToolDefinition(
    String name,
    String description,
    ToolParameters parameters,
    Function<Map<String, Object>, ToolResult> handler
) {}

// In same file
record ParamSchema(String type, String description) {}

public record ToolParameters(
    String type,
    Map<String, ParamSchema> properties,
    List<String> required
) {
    public ToolParameters {
        type = (type == null) ? "object" : type;
    }
}
```

Wait — Java records in the same file need to be properly structured. Let me split these into proper files.

Create `backend/src/main/java/com/shopai/agent/domain/ToolDefinition.java`:

```java
package com.shopai.agent.domain;

import java.util.Map;
import java.util.function.Function;

public record ToolDefinition(
    String name,
    String description,
    ToolParameters parameters,
    Function<Map<String, Object>, ToolResult> handler
) {}
```

Create `backend/src/main/java/com/shopai/agent/domain/ToolParameters.java`:

```java
package com.shopai.agent.domain;

import java.util.List;
import java.util.Map;

public record ToolParameters(
    String type,
    Map<String, ParamSchema> properties,
    List<String> required
) {
    public ToolParameters {
        type = (type == null) ? "object" : type;
    }
}
```

Create `backend/src/main/java/com/shopai/agent/domain/ParamSchema.java`:

```java
package com.shopai.agent.domain;

public record ParamSchema(String type, String description) {}
```

Create `backend/src/main/java/com/shopai/agent/domain/ToolCall.java`:

```java
package com.shopai.agent.domain;

import java.util.Map;

public record ToolCall(String name, Map<String, Object> arguments) {}
```

Create `backend/src/main/java/com/shopai/agent/domain/ToolResult.java`:

```java
package com.shopai.agent.domain;

import java.util.Map;

public record ToolResult(boolean success, String content, Map<String, Object> metadata) {
    public static ToolResult ok(String content) {
        return new ToolResult(true, content, Map.of());
    }

    public static ToolResult ok(String content, Map<String, Object> metadata) {
        return new ToolResult(true, content, metadata);
    }

    public static ToolResult fail(String content) {
        return new ToolResult(false, content, Map.of());
    }
}
```

- [ ] **Step 4: Create ChatRequest record**

Create `backend/src/main/java/com/shopai/agent/domain/ChatRequest.java`:

```java
package com.shopai.agent.domain;

import java.util.List;

public record ChatRequest(
    String systemPrompt,
    List<Message> messages,
    List<ToolDefinition> toolsAvailable
) {}
```

- [ ] **Step 5: Create DecisionType enum**

Create `backend/src/main/java/com/shopai/agent/domain/DecisionType.java`:

```java
package com.shopai.agent.domain;

public enum DecisionType {
    THOUGHT, TOOL_CALL, FINAL
}
```

- [ ] **Step 6: Create TokenUsage record**

Create `backend/src/main/java/com/shopai/agent/domain/TokenUsage.java`:

```java
package com.shopai.agent.domain;

public record TokenUsage(int inputTokens, int outputTokens) {
    public static final TokenUsage ZERO = new TokenUsage(0, 0);

    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(
            this.inputTokens + other.inputTokens,
            this.outputTokens + other.outputTokens
        );
    }

    public int total() {
        return inputTokens + outputTokens;
    }
}
```

- [ ] **Step 7: Create LlmResponse record**

Create `backend/src/main/java/com/shopai/agent/domain/LlmResponse.java`:

```java
package com.shopai.agent.domain;

public record LlmResponse(
    String content,
    DecisionType decision,
    ToolCall toolCall,
    TokenUsage usage
) {}
```

- [ ] **Step 8: Create StepType enum and StepRecord**

Create `backend/src/main/java/com/shopai/agent/domain/StepType.java`:

```java
package com.shopai.agent.domain;

public enum StepType {
    THOUGHT, TOOL_CALL, TOOL_RESULT, FINAL
}
```

Create `backend/src/main/java/com/shopai/agent/domain/StepRecord.java`:

```java
package com.shopai.agent.domain;

public record StepRecord(
    int iteration,
    StepType type,
    String thought,
    ToolCall toolCall,
    ToolResult toolResult
) {
    public static StepRecord thought(int iteration, String thought) {
        return new StepRecord(iteration, StepType.THOUGHT, thought, null, null);
    }

    public static StepRecord toolCall(int iteration, ToolCall call) {
        return new StepRecord(iteration, StepType.TOOL_CALL, null, call, null);
    }

    public static StepRecord toolResult(int iteration, ToolResult result) {
        return new StepRecord(iteration, StepType.TOOL_RESULT, null, null, result);
    }

    public static StepRecord finalAnswer(int iteration, String answer) {
        return new StepRecord(iteration, StepType.FINAL, answer, null, null);
    }
}
```

- [ ] **Step 9: Create AgentRequest and AgentResponse**

Create `backend/src/main/java/com/shopai/agent/domain/AgentRequest.java`:

```java
package com.shopai.agent.domain;

import java.util.Map;

public record AgentRequest(
    String sessionId,
    String userInput,
    Map<String, Object> context
) {
    public AgentRequest(String sessionId, String userInput) {
        this(sessionId, userInput, Map.of());
    }
}
```

Create `backend/src/main/java/com/shopai/agent/domain/AgentResponse.java`:

```java
package com.shopai.agent.domain;

import java.util.List;

public record AgentResponse(
    String content,
    List<StepRecord> steps,
    TokenUsage totalUsage,
    long latencyMs
) {}
```

- [ ] **Step 10: Compile and verify**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/domain/
git commit -m "feat: add domain models (Message, Tool, Agent, LLM types)"
```

---

## Task 4: Database Schema & Mock Data

**Files:**
- Create: `backend/src/main/resources/schema.sql`
- Create: `backend/src/main/resources/data/products.json`
- Create: `backend/src/main/resources/data/orders.json`
- Create: `backend/src/main/java/com/shopai/agent/config/DataInitializer.java`

- [ ] **Step 1: Create H2 schema**

Create `backend/src/main/resources/schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS conversation (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    title VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS message (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_msg_session ON message(session_id, created_at);

CREATE TABLE IF NOT EXISTS product (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50),
    price DECIMAL(10, 2),
    stock INT DEFAULT 0,
    specs TEXT,
    description TEXT
);

CREATE TABLE IF NOT EXISTS customer_order (
    id VARCHAR(36) PRIMARY KEY,
    order_no VARCHAR(20) UNIQUE NOT NULL,
    customer_name VARCHAR(100),
    status VARCHAR(20),
    total_amount DECIMAL(10, 2),
    items TEXT,
    logistics VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 2: Create mock products JSON**

Create `backend/src/main/resources/data/products.json`:

```json
[
  {"id":"p001","name":"iPhone 15 Pro Max","category":"phone","price":9999.00,"stock":120,"specs":"{\"screen\":\"6.7 inch\",\"chip\":\"A17 Pro\",\"camera\":\"48MP\"}","description":"Apple flagship smartphone with powerful A17 Pro chip and pro camera system"},
  {"id":"p002","name":"iPhone 15","category":"phone","price":5999.00,"stock":200,"specs":"{\"screen\":\"6.1 inch\",\"chip\":\"A16\",\"camera\":\"48MP\"}","description":"Apple iPhone 15 with Dynamic Island and 48MP main camera"},
  {"id":"p003","name":"Samsung Galaxy S24 Ultra","category":"phone","price":8999.00,"stock":85,"specs":"{\"screen\":\"6.8 inch\",\"chip\":\"Snapdragon 8 Gen 3\",\"camera\":\"200MP\"}","description":"Samsung flagship with built-in S Pen and 200MP camera"},
  {"id":"p004","name":"Xiaomi 14 Pro","category":"phone","price":4999.00,"stock":150,"specs":"{\"screen\":\"6.73 inch\",\"chip\":\"Snapdragon 8 Gen 3\",\"camera\":\"50MP Leica\"}","description":"Xiaomi flagship with Leica optics and fast charging"},
  {"id":"p005","name":"MacBook Pro 14","category":"computer","price":14999.00,"stock":40,"specs":"{\"screen\":\"14.2 inch\",\"chip\":\"M3 Pro\",\"ram\":\"18GB\"}","description":"Apple MacBook Pro with M3 Pro chip, ideal for developers"},
  {"id":"p006","name":"MacBook Air 15","category":"computer","price":10499.00,"stock":60,"specs":"{\"screen\":\"15.3 inch\",\"chip\":\"M3\",\"ram\":\"8GB\"}","description":"Thin and light MacBook Air with M3 chip"},
  {"id":"p007","name":"ThinkPad X1 Carbon","category":"computer","price":10999.00,"stock":30,"specs":"{\"screen\":\"14 inch\",\"chip\":\"Intel i7-1365U\",\"ram\":\"16GB\"}","description":"Lenovo business laptop, lightweight and durable"},
  {"id":"p008","name":"AirPods Pro 2","category":"accessory","price":1899.00,"stock":300,"specs":"{\"type\":\"TWS\",\"anc\":true,\"battery\":\"6h\"}","description":"Apple noise-cancelling earbuds with USB-C charging"},
  {"id":"p009","name":"Sony WH-1000XM5","category":"accessory","price":2499.00,"stock":90,"specs":"{\"type\":\"over-ear\",\"anc\":true,\"battery\":\"30h\"}","description":"Sony flagship noise-cancelling headphones"},
  {"id":"p010","name":"iPad Air M2","category":"computer","price":4799.00,"stock":70,"specs":"{\"screen\":\"11 inch\",\"chip\":\"M2\",\"storage\":\"128GB\"}","description":"iPad Air with M2 chip, great for creativity and study"}
]
```

- [ ] **Step 3: Create mock orders JSON**

Create `backend/src/main/resources/data/orders.json`:

```json
[
  {"id":"o001","order_no":"20240611001","customer_name":"张三","status":"shipped","total_amount":9999.00,"items":"[{\"productId\":\"p001\",\"name\":\"iPhone 15 Pro Max\",\"qty\":1,\"price\":9999.00}]","logistics":"顺丰 SF1234567890","created_at":"2024-06-11T10:30:00"},
  {"id":"o002","order_no":"20240610002","customer_name":"张三","status":"delivered","total_amount":1899.00,"items":"[{\"productId\":\"p008\",\"name\":\"AirPods Pro 2\",\"qty\":1,\"price\":1899.00}]","logistics":"圆通 YT9876543210","created_at":"2024-06-10T14:20:00"},
  {"id":"o003","order_no":"20240609003","customer_name":"李四","status":"delivered","total_amount":14999.00,"items":"[{\"productId\":\"p005\",\"name\":\"MacBook Pro 14\",\"qty\":1,\"price\":14999.00}]","logistics":"京东 JD1122334455","created_at":"2024-06-09T09:15:00"},
  {"id":"o004","order_no":"20240608004","customer_name":"王五","status":"returning","total_amount":5999.00,"items":"[{\"productId\":\"p002\",\"name\":\"iPhone 15\",\"qty\":1,\"price\":5999.00}]","logistics":"中通 ZT5566778899","created_at":"2024-06-08T16:45:00"},
  {"id":"o005","order_no":"20240607005","customer_name":"赵六","status":"paid","total_amount":15498.00,"items":"[{\"productId\":\"p008\",\"name\":\"AirPods Pro 2\",\"qty\":1,\"price\":1899.00},{\"productId\":\"p009\",\"name\":\"Sony WH-1000XM5\",\"qty\":1,\"price\":2499.00}]","logistics":null,"created_at":"2024-06-07T11:00:00"}
]
```

- [ ] **Step 4: Create DataInitializer**

Create `backend/src/main/java/com/shopai/agent/config/DataInitializer.java`:

```java
package com.shopai.agent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) throws Exception {
        loadProducts();
        loadOrders();
    }

    private void loadProducts() throws Exception {
        var resource = new ClassPathResource("data/products.json");
        List<Map<String, Object>> products = mapper.readValue(
            resource.getInputStream(),
            new TypeReference<>() {}
        );
        for (var p : products) {
            jdbc.update(
                "MERGE INTO product (id, name, category, price, stock, specs, description) VALUES (?, ?, ?, ?, ?, ?, ?)",
                p.get("id"), p.get("name"), p.get("category"),
                Double.parseDouble(p.get("price").toString()),
                Integer.parseInt(p.get("stock").toString()),
                p.get("specs"), p.get("description")
            );
        }
        System.out.println("Loaded " + products.size() + " products");
    }

    private void loadOrders() throws Exception {
        var resource = new ClassPathResource("data/orders.json");
        List<Map<String, Object>> orders = mapper.readValue(
            resource.getInputStream(),
            new TypeReference<>() {}
        );
        for (var o : orders) {
            jdbc.update(
                "MERGE INTO customer_order (id, order_no, customer_name, status, total_amount, items, logistics, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                o.get("id"), o.get("order_no"), o.get("customer_name"),
                o.get("status"),
                Double.parseDouble(o.get("total_amount").toString()),
                o.get("items"), o.get("logistics"),
                o.get("created_at") != null ? java.sql.Timestamp.valueOf(o.get("created_at").toString().replace("T", " ")) : null
            );
        }
        System.out.println("Loaded " + orders.size() + " orders");
    }
}
```

- [ ] **Step 5: Verify app starts and data loads**

Run: `cd backend && mvn spring-boot:run`
Expected: Log shows "Loaded 10 products" and "Loaded 5 orders", app starts on port 8080. Kill with Ctrl+C.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/schema.sql backend/src/main/resources/data/ backend/src/main/java/com/shopai/agent/config/DataInitializer.java
git commit -m "feat: add H2 schema and mock data initializer"
```

---

## Task 5: LLM Adapter

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/llm/LlmAdapter.java`
- Create: `backend/src/main/java/com/shopai/agent/llm/LangChain4jAdapter.java`

- [ ] **Step 1: Create LlmAdapter interface**

Create `backend/src/main/java/com/shopai/agent/llm/LlmAdapter.java`:

```java
package com.shopai.agent.llm;

import com.shopai.agent.domain.ChatRequest;
import com.shopai.agent.domain.LlmResponse;

public interface LlmAdapter {
    LlmResponse chat(ChatRequest request);
}
```

- [ ] **Step 2: Create LangChain4jAdapter implementation**

Create `backend/src/main/java/com/shopai/agent/llm/LangChain4jAdapter.java`:

```java
package com.shopai.agent.llm;

import com.shopai.agent.domain.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.stream.Collectors;

public class LangChain4jAdapter implements LlmAdapter {

    private final ChatLanguageModel model;

    public LangChain4jAdapter(String apiKey, String modelName, String baseUrl, Duration timeout) {
        this.model = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .timeout(timeout)
            .build();
    }

    @Override
    public LlmResponse chat(ChatRequest request) {
        String prompt = buildFullPrompt(request);
        String rawResponse = model.generate(prompt);

        // Parse the LLM response into DecisionType + optional ToolCall
        return parseResponse(rawResponse);
    }

    private String buildFullPrompt(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.systemPrompt()).append("\n\n");

        // Append conversation history
        if (request.messages() != null) {
            for (Message msg : request.messages()) {
                sb.append(msg.role()).append(": ").append(msg.content()).append("\n");
            }
        }

        return sb.toString();
    }

    LlmResponse parseResponse(String raw) {
        String content = raw.trim();

        // Extract THOUGHT
        String thought = extractSection(content, "THOUGHT:");
        String action = extractSection(content, "ACTION:");

        if (action != null && !action.trim().equalsIgnoreCase("FINAL")) {
            // Parse tool call from ACTION
            ToolCall toolCall = parseToolCall(action.trim());
            return new LlmResponse(content, DecisionType.TOOL_CALL, toolCall, estimateTokens(content));
        } else if (action != null && action.trim().equalsIgnoreCase("FINAL")) {
            // Extract the actual answer after FINAL marker
            String answer = extractAfterFinal(content);
            return new LlmResponse(answer, DecisionType.FINAL, null, estimateTokens(content));
        } else {
            // No ACTION found — treat as THOUGHT
            return new LlmResponse(content, DecisionType.THOUGHT, null, estimateTokens(content));
        }
    }

    private String extractSection(String text, String marker) {
        int start = text.indexOf(marker);
        if (start == -1) return null;
        int contentStart = start + marker.length();
        // Find next section marker
        int nextThought = text.indexOf("THOUGHT:", contentStart);
        int nextAction = text.indexOf("ACTION:", contentStart);
        int end = text.length();
        if (nextThought > contentStart) end = Math.min(end, nextThought);
        if (nextAction > contentStart) end = Math.min(end, nextAction);
        return text.substring(contentStart, end).trim();
    }

    private ToolCall parseToolCall(String actionText) {
        // Format: tool_name({"param": "value"})
        int parenIdx = actionText.indexOf('(');
        if (parenIdx == -1) return new ToolCall(actionText.trim(), java.util.Map.of());

        String toolName = actionText.substring(0, parenIdx).trim();
        String argsJson = actionText.substring(parenIdx + 1, actionText.lastIndexOf(')')).trim();

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            var args = mapper.readValue(argsJson, java.util.Map.class);
            return new ToolCall(toolName, args);
        } catch (Exception e) {
            return new ToolCall(toolName, java.util.Map.of());
        }
    }

    private String extractAfterFinal(String text) {
        int finalIdx = text.lastIndexOf("FINAL");
        if (finalIdx == -1) return text;
        // Skip past "FINAL" and any following newline
        String after = text.substring(finalIdx + 5).trim();
        // Also skip any text containing THOUGHT/ACTION before the user-facing answer
        int lastAction = after.lastIndexOf("ACTION:");
        if (lastAction != -1) {
            after = after.substring(lastAction + 7).trim();
            if (after.equalsIgnoreCase("FINAL") || after.isBlank()) {
                // The answer is everything after the FINAL marker that isn't structured
                after = text.substring(text.lastIndexOf("FINAL") + 5).trim();
            }
        }
        return after;
    }

    private TokenUsage estimateTokens(String text) {
        // Rough estimation: ~4 chars per token
        int estimated = Math.max(1, text.length() / 4);
        return new TokenUsage(estimated, estimated);
    }
}
```

- [ ] **Step 3: Create a config bean for LlmAdapter**

Create `backend/src/main/java/com/shopai/agent/config/AgentConfig.java`:

```java
package com.shopai.agent.config;

import com.shopai.agent.llm.LangChain4jAdapter;
import com.shopai.agent.llm.LlmAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AgentConfig {

    @Value("${shopai.llm.api-key}")
    private String apiKey;

    @Value("${shopai.llm.model}")
    private String model;

    @Value("${shopai.llm.base-url}")
    private String baseUrl;

    @Value("${shopai.llm.timeout}")
    private String timeout;

    @Bean
    public LlmAdapter llmAdapter() {
        return new LangChain4jAdapter(
            apiKey,
            model,
            baseUrl,
            Duration.parse("PT" + timeout.replace("s", "S").replace("m", "M"))
        );
    }
}
```

- [ ] **Step 4: Compile and verify**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/llm/ backend/src/main/java/com/shopai/agent/config/AgentConfig.java
git commit -m "feat: add LLM adapter with LangChain4j integration"
```

---

## Task 6: Prompt Engine

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/prompt/PromptEngine.java`
- Create: `backend/src/main/java/com/shopai/agent/prompt/MustachePromptEngine.java`
- Create: `backend/src/main/resources/prompts/react-system.mustache`

- [ ] **Step 1: Create PromptEngine interface**

Create `backend/src/main/java/com/shopai/agent/prompt/PromptEngine.java`:

```java
package com.shopai.agent.prompt;

import com.shopai.agent.domain.ChatRequest;
import com.shopai.agent.domain.Message;
import com.shopai.agent.domain.ToolDefinition;

import java.util.List;
import java.util.Map;

public interface PromptEngine {
    ChatRequest build(String templateName, Map<String, Object> vars);

    record BuildVars(
        List<ToolDefinition> tools,
        List<Message> history,
        String userInput
    ) {
        public Map<String, Object> toMap() {
            return Map.of(
                "tools", tools,
                "history", history,
                "userInput", userInput
            );
        }
    }
}
```

- [ ] **Step 2: Create MustachePromptEngine**

Create `backend/src/main/java/com/shopai/agent/prompt/MustachePromptEngine.java`:

```java
package com.shopai.agent.prompt;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.shopai.agent.domain.ChatRequest;
import com.shopai.agent.domain.Message;
import com.shopai.agent.domain.ToolDefinition;
import org.springframework.core.io.ClassPathResource;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MustachePromptEngine implements PromptEngine {

    private final MustacheFactory mf = new DefaultMustacheFactory();
    private final Map<String, String> templates = new ConcurrentHashMap<>();

    public MustachePromptEngine() {
        // Load built-in templates from classpath
        loadTemplate("react-system", "prompts/react-system.mustache");
        loadTemplate("tool-result", "prompts/tool-result.mustache");
    }

    private void loadTemplate(String name, String classpath) {
        try {
            var resource = new ClassPathResource(classpath);
            String content = Files.readString(Path.of(resource.getURI()));
            templates.put(name, content);
        } catch (Exception e) {
            System.err.println("Failed to load template: " + classpath + " — " + e.getMessage());
        }
    }

    @Override
    public ChatRequest build(String templateName, Map<String, Object> vars) {
        String templateContent = templates.get(templateName);
        if (templateContent == null) {
            throw new IllegalArgumentException("Template not found: " + templateName);
        }

        Mustache mustache = mf.compile(new StringReader(templateContent), templateName);
        StringWriter writer = new StringWriter();
        mustache.execute(writer, vars);
        String rendered = writer.toString();

        @SuppressWarnings("unchecked")
        List<ToolDefinition> tools = (List<ToolDefinition>) vars.get("tools");
        @SuppressWarnings("unchecked")
        List<Message> messages = (List<Message>) vars.get("history");

        return new ChatRequest(rendered, messages, tools);
    }

    public void registerTemplate(String name, String content) {
        templates.put(name, content);
    }
}
```

- [ ] **Step 3: Create ReAct system prompt template**

Create `backend/src/main/resources/prompts/react-system.mustache`:

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

Respond strictly using this format:

THOUGHT: [your reasoning about what to do next]
ACTION: tool_name({"param": "value"})   // or ACTION: FINAL if ready to answer

Rules:
- Call only one tool per ACTION
- Use FINAL only when you have all information needed to fully answer the user
- When using FINAL, provide a complete, helpful answer in Chinese
- Break down complex requests into steps if needed
```

- [ ] **Step 4: Create tool-result template**

Create `backend/src/main/resources/prompts/tool-result.mustache`:

```
Tool {{toolName}} returned:
{{result}}

Based on this result, continue. Reply with THOUGHT then ACTION (or FINAL if done).
```

- [ ] **Step 5: Add PromptEngine bean to AgentConfig**

Edit `backend/src/main/java/com/shopai/agent/config/AgentConfig.java` — add:

```java
@Bean
public PromptEngine promptEngine() {
    return new MustachePromptEngine();
}
```

(Import `com.shopai.agent.prompt.MustachePromptEngine` and `com.shopai.agent.prompt.PromptEngine`)

- [ ] **Step 6: Compile and verify**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/prompt/ backend/src/main/resources/prompts/
git add backend/src/main/java/com/shopai/agent/config/AgentConfig.java
git commit -m "feat: add prompt template engine with Mustache"
```

---

## Task 7: Memory Manager

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/memory/MemoryManager.java`
- Create: `backend/src/main/java/com/shopai/agent/memory/H2MemoryManager.java`

- [ ] **Step 1: Create MemoryManager interface**

Create `backend/src/main/java/com/shopai/agent/memory/MemoryManager.java`:

```java
package com.shopai.agent.memory;

import com.shopai.agent.domain.Message;

import java.util.List;

public interface MemoryManager {
    List<Message> loadHistory(String sessionId, int maxMessages);
    void append(String sessionId, Message msg);
    void clear(String sessionId);
}
```

- [ ] **Step 2: Create H2MemoryManager implementation**

Create `backend/src/main/java/com/shopai/agent/memory/H2MemoryManager.java`:

```java
package com.shopai.agent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopai.agent.domain.Message;
import com.shopai.agent.domain.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class H2MemoryManager implements MemoryManager {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public H2MemoryManager(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Message> loadHistory(String sessionId, int maxMessages) {
        String sql = """
            SELECT id, session_id, role, content, metadata, created_at
            FROM message
            WHERE session_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """;
        List<Message> messages = jdbc.query(sql, this::mapMessage, sessionId, maxMessages);
        // Reverse to chronological order
        return messages.reversed();
    }

    @Override
    public void append(String sessionId, Message msg) {
        String sql = """
            INSERT INTO message (id, session_id, role, content, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        String metadataJson;
        try {
            metadataJson = mapper.writeValueAsString(msg.metadata());
        } catch (JsonProcessingException e) {
            metadataJson = "{}";
        }
        jdbc.update(sql,
            msg.id() != null ? msg.id() : UUID.randomUUID().toString(),
            sessionId,
            msg.role().name(),
            msg.content(),
            metadataJson,
            Timestamp.from(msg.timestamp() != null ? msg.timestamp() : Instant.now())
        );

        // Ensure conversation record exists
        jdbc.update(
            "MERGE INTO conversation (id, session_id, title) VALUES (?, ?, ?)",
            UUID.randomUUID().toString(), sessionId, "会话 " + sessionId.substring(0, 8)
        );
    }

    @Override
    public void clear(String sessionId) {
        jdbc.update("DELETE FROM message WHERE session_id = ?", sessionId);
    }

    private Message mapMessage(ResultSet rs, int rowNum) throws java.sql.SQLException {
        String id = rs.getString("id");
        Role role = Role.valueOf(rs.getString("role"));
        String content = rs.getString("content");
        String metadataStr = rs.getString("metadata");
        Map<String, Object> metadata;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(metadataStr, Map.class);
            metadata = parsed;
        } catch (Exception e) {
            metadata = Map.of();
        }
        Instant ts = rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toInstant()
            : Instant.now();
        return new Message(id, role, content, metadata, ts);
    }
}
```

- [ ] **Step 3: Add MemoryManager bean to AgentConfig**

Edit `backend/src/main/java/com/shopai/agent/config/AgentConfig.java` — add:

```java
@Bean
public MemoryManager memoryManager(JdbcTemplate jdbc) {
    return new H2MemoryManager(jdbc);
}
```

(Import `org.springframework.jdbc.core.JdbcTemplate`, `com.shopai.agent.memory.H2MemoryManager`, `com.shopai.agent.memory.MemoryManager`)

- [ ] **Step 4: Compile and verify**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/memory/ backend/src/main/java/com/shopai/agent/config/AgentConfig.java
git commit -m "feat: add H2-backed conversation memory manager"
```

---

## Task 8: Tool Registry + Tools

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/tool/ToolRegistry.java`
- Create: `backend/src/main/java/com/shopai/agent/tool/DefaultToolRegistry.java`
- Create: `backend/src/main/java/com/shopai/agent/tool/OrderQueryTool.java`
- Create: `backend/src/main/java/com/shopai/agent/tool/ProductSearchTool.java`
- Create: `backend/src/main/java/com/shopai/agent/tool/CalculatorTool.java`
- Create: `backend/src/test/java/com/shopai/agent/tool/ToolRegistryTest.java`

- [ ] **Step 1: Create ToolRegistry interface**

Create `backend/src/main/java/com/shopai/agent/tool/ToolRegistry.java`:

```java
package com.shopai.agent.tool;

import com.shopai.agent.domain.ToolCall;
import com.shopai.agent.domain.ToolDefinition;
import com.shopai.agent.domain.ToolResult;

import java.util.List;
import java.util.Optional;

public interface ToolRegistry {
    void register(ToolDefinition tool);
    Optional<ToolDefinition> get(String name);
    ToolResult execute(ToolCall call);
    List<ToolDefinition> listAll();
}
```

- [ ] **Step 2: Create DefaultToolRegistry**

Create `backend/src/main/java/com/shopai/agent/tool/DefaultToolRegistry.java`:

```java
package com.shopai.agent.tool;

import com.shopai.agent.domain.ToolCall;
import com.shopai.agent.domain.ToolDefinition;
import com.shopai.agent.domain.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    @Override
    public void register(ToolDefinition tool) {
        tools.put(tool.name(), tool);
    }

    @Override
    public Optional<ToolDefinition> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    @Override
    public ToolResult execute(ToolCall call) {
        ToolDefinition tool = tools.get(call.name());
        if (tool == null) {
            return ToolResult.fail("Tool not found: " + call.name());
        }
        try {
            return tool.handler().apply(call.arguments());
        } catch (Exception e) {
            return ToolResult.fail("Tool execution error: " + e.getMessage());
        }
    }

    @Override
    public List<ToolDefinition> listAll() {
        return List.copyOf(tools.values());
    }
}
```

- [ ] **Step 3: Create OrderQueryTool**

Create `backend/src/main/java/com/shopai/agent/tool/OrderQueryTool.java`:

```java
package com.shopai.agent.tool;

import com.shopai.agent.domain.ParamSchema;
import com.shopai.agent.domain.ToolDefinition;
import com.shopai.agent.domain.ToolParameters;
import com.shopai.agent.domain.ToolResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrderQueryTool {

    private final JdbcTemplate jdbc;

    public OrderQueryTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ToolDefinition definition() {
        return new ToolDefinition(
            "OrderQueryTool",
            "查询用户的订单状态和物流信息。参数: orderNo (订单号) 或 customerName (客户姓名)",
            new ToolParameters(
                "object",
                Map.of(
                    "orderNo", new ParamSchema("string", "订单号，如 20240611001"),
                    "customerName", new ParamSchema("string", "客户姓名")
                ),
                List.of()
            ),
            this::execute
        );
    }

    private ToolResult execute(Map<String, Object> args) {
        String orderNo = (String) args.get("orderNo");
        String customerName = (String) args.get("customerName");

        List<Map<String, Object>> orders;
        if (orderNo != null && !orderNo.isBlank()) {
            orders = jdbc.queryForList(
                "SELECT order_no, customer_name, status, total_amount, items, logistics, created_at FROM customer_order WHERE order_no = ?",
                orderNo
            );
        } else if (customerName != null && !customerName.isBlank()) {
            orders = jdbc.queryForList(
                "SELECT order_no, customer_name, status, total_amount, items, logistics, created_at FROM customer_order WHERE customer_name = ? ORDER BY created_at DESC LIMIT 10",
                customerName
            );
        } else {
            return ToolResult.fail("请提供订单号或客户姓名");
        }

        if (orders.isEmpty()) {
            return ToolResult.ok("未找到相关订单");
        }

        StringBuilder sb = new StringBuilder();
        for (var o : orders) {
            sb.append(String.format(
                "订单号: %s | 客户: %s | 状态: %s | 金额: ¥%.2f | 物流: %s | 时间: %s\n",
                o.get("ORDER_NO"), o.get("CUSTOMER_NAME"), o.get("STATUS"),
                o.get("TOTAL_AMOUNT"), o.get("LOGISTICS"), o.get("CREATED_AT")
            ));
        }
        return ToolResult.ok(sb.toString().trim());
    }
}
```

- [ ] **Step 4: Create ProductSearchTool**

Create `backend/src/main/java/com/shopai/agent/tool/ProductSearchTool.java`:

```java
package com.shopai.agent.tool;

import com.shopai.agent.domain.ParamSchema;
import com.shopai.agent.domain.ToolDefinition;
import com.shopai.agent.domain.ToolParameters;
import com.shopai.agent.domain.ToolResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ProductSearchTool {

    private final JdbcTemplate jdbc;

    public ProductSearchTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ToolDefinition definition() {
        return new ToolDefinition(
            "ProductSearchTool",
            "搜索产品，支持关键词、分类、价格范围筛选。参数: keyword (搜索关键词), category (分类: phone/computer/accessory), minPrice (最低价), maxPrice (最高价)",
            new ToolParameters(
                "object",
                Map.of(
                    "keyword", new ParamSchema("string", "产品名关键词"),
                    "category", new ParamSchema("string", "分类: phone, computer, accessory"),
                    "minPrice", new ParamSchema("number", "最低价格"),
                    "maxPrice", new ParamSchema("number", "最高价格")
                ),
                List.of()
            ),
            this::execute
        );
    }

    private ToolResult execute(Map<String, Object> args) {
        String keyword = (String) args.get("keyword");
        String category = (String) args.get("category");
        Object minPrice = args.get("minPrice");
        Object maxPrice = args.get("maxPrice");

        StringBuilder sql = new StringBuilder(
            "SELECT name, category, price, stock, description FROM product WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (name LIKE ? OR description LIKE ?)");
            String like = "%" + keyword + "%";
            params.add(like);
            params.add(like);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (minPrice != null) {
            sql.append(" AND price >= ?");
            params.add(Double.parseDouble(minPrice.toString()));
        }
        if (maxPrice != null) {
            sql.append(" AND price <= ?");
            params.add(Double.parseDouble(maxPrice.toString()));
        }
        sql.append(" ORDER BY price ASC LIMIT 10");

        List<Map<String, Object>> results = jdbc.queryForList(
            sql.toString(), params.toArray()
        );

        if (results.isEmpty()) {
            return ToolResult.ok("未找到匹配的产品");
        }

        StringBuilder sb = new StringBuilder();
        for (var r : results) {
            sb.append(String.format(
                "%s | 分类: %s | 价格: ¥%.2f | 库存: %d | %s\n",
                r.get("NAME"), r.get("CATEGORY"), r.get("PRICE"),
                r.get("STOCK"), r.get("DESCRIPTION")
            ));
        }
        return ToolResult.ok(sb.toString().trim());
    }
}
```

- [ ] **Step 5: Create CalculatorTool**

Create `backend/src/main/java/com/shopai/agent/tool/CalculatorTool.java`:

```java
package com.shopai.agent.tool;

import com.shopai.agent.domain.ParamSchema;
import com.shopai.agent.domain.ToolDefinition;
import com.shopai.agent.domain.ToolParameters;
import com.shopai.agent.domain.ToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CalculatorTool {

    public ToolDefinition definition() {
        return new ToolDefinition(
            "CalculatorTool",
            "执行数学计算。参数: expression (数学表达式，如 '2+3*4' 或 '100*0.9')",
            new ToolParameters(
                "object",
                Map.of(
                    "expression", new ParamSchema("string", "数学表达式，支持 + - * / 和括号")
                ),
                List.of("expression")
            ),
            this::execute
        );
    }

    private ToolResult execute(Map<String, Object> args) {
        String expression = (String) args.get("expression");
        if (expression == null || expression.isBlank()) {
            return ToolResult.fail("请提供 expression 参数");
        }

        try {
            // Simple evaluation using javax.script (safe for basic math)
            javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
            javax.script.ScriptEngine engine = mgr.getEngineByName("JavaScript");
            Object result = engine.eval(expression);
            return ToolResult.ok(expression + " = " + result);
        } catch (Exception e) {
            return ToolResult.fail("计算错误: " + e.getMessage());
        }
    }
}
```

- [ ] **Step 6: Wire tools into AgentConfig**

Edit `backend/src/main/java/com/shopai/agent/config/AgentConfig.java` — add:

```java
@Bean
public ToolRegistry toolRegistry(OrderQueryTool orderQuery, ProductSearchTool productSearch, CalculatorTool calculator) {
    DefaultToolRegistry registry = new DefaultToolRegistry();
    registry.register(orderQuery.definition());
    registry.register(productSearch.definition());
    registry.register(calculator.definition());
    return registry;
}
```

(Import `com.shopai.agent.tool.DefaultToolRegistry`, `com.shopai.agent.tool.ToolRegistry`, `com.shopai.agent.tool.OrderQueryTool`, `com.shopai.agent.tool.ProductSearchTool`, `com.shopai.agent.tool.CalculatorTool`)

- [ ] **Step 7: Write ToolRegistry unit test**

Create `backend/src/test/java/com/shopai/agent/tool/ToolRegistryTest.java`:

```java
package com.shopai.agent.tool;

import com.shopai.agent.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private DefaultToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DefaultToolRegistry();
        registry.register(new ToolDefinition(
            "test_tool",
            "A test tool",
            new ToolParameters("object", Map.of("input", new ParamSchema("string", "test input")), List.of("input")),
            args -> ToolResult.ok("Got: " + args.get("input"))
        ));
    }

    @Test
    void shouldRegisterAndRetrieveTool() {
        var tool = registry.get("test_tool");
        assertTrue(tool.isPresent());
        assertEquals("test_tool", tool.get().name());
    }

    @Test
    void shouldReturnEmptyForUnknownTool() {
        assertTrue(registry.get("unknown").isEmpty());
    }

    @Test
    void shouldExecuteTool() {
        var result = registry.execute(new ToolCall("test_tool", Map.of("input", "hello")));
        assertTrue(result.success());
        assertEquals("Got: hello", result.content());
    }

    @Test
    void shouldReturnErrorForUnknownTool() {
        var result = registry.execute(new ToolCall("unknown", Map.of()));
        assertFalse(result.success());
        assertTrue(result.content().contains("not found"));
    }

    @Test
    void shouldListAllTools() {
        assertEquals(1, registry.listAll().size());
    }
}
```

- [ ] **Step 8: Run tests**

Run: `cd backend && mvn test -Dtest=ToolRegistryTest`
Expected: 5 tests pass

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/tool/ backend/src/main/java/com/shopai/agent/config/AgentConfig.java backend/src/test/
git commit -m "feat: add tool registry with OrderQuery, ProductSearch, Calculator tools"
```

---

## Task 9: Agent Engine

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/engine/AgentEngine.java`
- Create: `backend/src/main/java/com/shopai/agent/engine/ReActAgentEngine.java`
- Create: `backend/src/main/java/com/shopai/agent/engine/LlmResponseParser.java`
- Create: `backend/src/test/java/com/shopai/agent/engine/ReActAgentEngineTest.java`

- [ ] **Step 1: Create AgentEngine interface**

Create `backend/src/main/java/com/shopai/agent/engine/AgentEngine.java`:

```java
package com.shopai.agent.engine;

import com.shopai.agent.domain.AgentRequest;
import com.shopai.agent.domain.AgentResponse;

public interface AgentEngine {
    AgentResponse execute(AgentRequest request);
}
```

- [ ] **Step 2: Create LlmResponseParser (extract from LlmAdapter)**

Create `backend/src/main/java/com/shopai/agent/engine/LlmResponseParser.java`:

```java
package com.shopai.agent.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopai.agent.domain.*;

import java.util.Map;

public class LlmResponseParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    public LlmResponse parse(String rawResponse) {
        String content = rawResponse.trim();
        String thought = extractSection(content, "THOUGHT:");
        String action = extractSection(content, "ACTION:");

        if (action != null && !action.trim().equalsIgnoreCase("FINAL")) {
            ToolCall toolCall = parseToolCall(action.trim());
            return new LlmResponse(content, DecisionType.TOOL_CALL, toolCall, estimateTokens(content));
        } else if (action != null) {
            String answer = extractFinalAnswer(content);
            return new LlmResponse(answer, DecisionType.FINAL, null, estimateTokens(content));
        } else {
            return new LlmResponse(content, DecisionType.THOUGHT, null, estimateTokens(content));
        }
    }

    private String extractSection(String text, String marker) {
        int start = text.indexOf(marker);
        if (start == -1) return null;
        int contentStart = start + marker.length();
        int nextThought = text.indexOf("THOUGHT:", contentStart);
        int nextAction = text.indexOf("ACTION:", contentStart);
        int end = text.length();
        if (nextThought > contentStart) end = Math.min(end, nextThought);
        if (nextAction > contentStart) end = Math.min(end, nextAction);
        return text.substring(contentStart, end).trim();
    }

    private ToolCall parseToolCall(String actionText) {
        int parenIdx = actionText.indexOf('(');
        if (parenIdx == -1) return new ToolCall(actionText.trim(), Map.of());
        String toolName = actionText.substring(0, parenIdx).trim();
        String argsJson = actionText.substring(parenIdx + 1, actionText.lastIndexOf(')')).trim();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = mapper.readValue(argsJson, Map.class);
            return new ToolCall(toolName, args);
        } catch (Exception e) {
            return new ToolCall(toolName, Map.of("_raw", argsJson));
        }
    }

    private String extractFinalAnswer(String text) {
        int lastFINAL = text.lastIndexOf("FINAL");
        if (lastFINAL == -1) return text;
        String after = text.substring(lastFINAL + 5).trim();
        // Remove any trailing THOUGHT/ACTION lines
        int nextThought = after.indexOf("THOUGHT:");
        int nextAction = after.indexOf("ACTION:");
        int cutoff = after.length();
        if (nextThought >= 0) cutoff = Math.min(cutoff, nextThought);
        if (nextAction >= 0) cutoff = Math.min(cutoff, nextAction);
        return after.substring(0, cutoff).trim();
    }

    private TokenUsage estimateTokens(String text) {
        int estimated = Math.max(1, text.length() / 4);
        return new TokenUsage(estimated, estimated);
    }
}
```

- [ ] **Step 3: Update LangChain4jAdapter to use LlmResponseParser**

Edit `backend/src/main/java/com/shopai/agent/llm/LangChain4jAdapter.java`:

Remove the `parseResponse`, `extractSection`, `parseToolCall`, `extractAfterFinal`, and `estimateTokens` methods. Replace with:

```java
private final LlmResponseParser parser = new LlmResponseParser();

private LlmResponse parseResponse(String raw) {
    return parser.parse(raw);
}
```

(Import `com.shopai.agent.engine.LlmResponseParser`)

- [ ] **Step 4: Create ReActAgentEngine**

Create `backend/src/main/java/com/shopai/agent/engine/ReActAgentEngine.java`:

```java
package com.shopai.agent.engine;

import com.shopai.agent.domain.*;
import com.shopai.agent.llm.LlmAdapter;
import com.shopai.agent.memory.MemoryManager;
import com.shopai.agent.prompt.PromptEngine;
import com.shopai.agent.tool.ToolRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ReActAgentEngine implements AgentEngine {

    private final LlmAdapter llm;
    private final PromptEngine prompt;
    private final MemoryManager memory;
    private final ToolRegistry tools;

    @Value("${shopai.agent.max-iterations:10}")
    private int maxIterations;

    @Value("${shopai.agent.max-history-messages:20}")
    private int maxHistoryMessages;

    public ReActAgentEngine(LlmAdapter llm, PromptEngine prompt, MemoryManager memory, ToolRegistry tools) {
        this.llm = llm;
        this.prompt = prompt;
        this.memory = memory;
        this.tools = tools;
    }

    @Override
    public AgentResponse execute(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        List<StepRecord> steps = new ArrayList<>();
        TokenUsage totalUsage = TokenUsage.ZERO;

        // Save user message
        memory.append(request.sessionId(), Message.of(Role.USER, request.userInput()));

        // Load history
        List<Message> history = memory.loadHistory(request.sessionId(), maxHistoryMessages);

        // Build initial prompt vars
        Map<String, Object> vars = Map.of(
            "tools", tools.listAll(),
            "history", history,
            "userInput", request.userInput()
        );

        int iteration = 0;
        String finalAnswer = null;

        while (iteration < maxIterations) {
            iteration++;

            // Build prompt and call LLM
            ChatRequest chatRequest = prompt.build("react-system", vars);
            LlmResponse response = llm.chat(chatRequest);
            totalUsage = totalUsage.add(response.usage());

            switch (response.decision()) {
                case THOUGHT -> {
                    steps.add(StepRecord.thought(iteration, response.content()));
                    // Save the thought as a message
                    memory.append(request.sessionId(), Message.of(Role.ASSISTANT, response.content()));
                }
                case TOOL_CALL -> {
                    ToolCall call = response.toolCall();
                    steps.add(StepRecord.toolCall(iteration, call));
                    memory.append(request.sessionId(),
                        Message.of(Role.ASSISTANT, "Calling tool: " + call.name(),
                            Map.of("toolCall", call)));

                    // Execute tool
                    ToolResult result = tools.execute(call);
                    steps.add(StepRecord.toolResult(iteration, result));
                    memory.append(request.sessionId(),
                        Message.of(Role.TOOL, result.content(),
                            Map.of("toolName", call.name(), "success", result.success())));

                    // Inject tool result into conversation for next iteration
                    String toolResultMsg = String.format(
                        "Tool %s returned:\n%s",
                        call.name(), result.content()
                    );
                    // Append to history list for next prompt build
                    List<Message> updatedHistory = new ArrayList<>(history);
                    updatedHistory.add(Message.of(Role.TOOL, toolResultMsg));
                    vars = Map.of(
                        "tools", tools.listAll(),
                        "history", updatedHistory,
                        "userInput", request.userInput()
                    );
                }
                case FINAL -> {
                    finalAnswer = response.content();
                    steps.add(StepRecord.finalAnswer(iteration, finalAnswer));
                    memory.append(request.sessionId(), Message.of(Role.ASSISTANT, finalAnswer));
                    break; // exit while
                }
            }

            if (response.decision() == DecisionType.FINAL) {
                break;
            }
        }

        if (finalAnswer == null) {
            // Max iterations reached without FINAL — synthesize answer
            finalAnswer = "抱歉，处理您的请求超时了。请稍后再试或提供更具体的信息。";
            steps.add(StepRecord.finalAnswer(iteration, finalAnswer));
            memory.append(request.sessionId(), Message.of(Role.ASSISTANT, finalAnswer));
        }

        long latency = System.currentTimeMillis() - startTime;
        return new AgentResponse(finalAnswer, steps, totalUsage, latency);
    }
}
```

- [ ] **Step 5: Write AgentEngine test (with mocked LLM)**

Create `backend/src/test/java/com/shopai/agent/engine/ReActAgentEngineTest.java`:

```java
package com.shopai.agent.engine;

import com.shopai.agent.domain.*;
import com.shopai.agent.llm.LlmAdapter;
import com.shopai.agent.memory.MemoryManager;
import com.shopai.agent.prompt.PromptEngine;
import com.shopai.agent.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReActAgentEngineTest {

    @Mock private LlmAdapter llm;
    @Mock private PromptEngine prompt;
    @Mock private MemoryManager memory;
    @Mock private ToolRegistry tools;

    private ReActAgentEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ReActAgentEngine(llm, prompt, memory, tools);
        ReflectionTestUtils.setField(engine, "maxIterations", 3);
        ReflectionTestUtils.setField(engine, "maxHistoryMessages", 20);
    }

    @Test
    void shouldReturnDirectFinalAnswer() {
        // Given: LLM responds with FINAL on first attempt
        var llmResponse = new LlmResponse("您好，有什么可以帮您？", DecisionType.FINAL, null, new TokenUsage(10, 8));
        when(llm.chat(any())).thenReturn(llmResponse);
        when(prompt.build(eq("react-system"), any())).thenReturn(
            new ChatRequest("sys prompt", List.of(), List.of())
        );
        when(memory.loadHistory(anyString(), anyInt())).thenReturn(List.of());

        // When
        AgentResponse response = engine.execute(new AgentRequest("s1", "你好"));

        // Then
        assertEquals("您好，有什么可以帮您？", response.content());
        assertEquals(1, response.steps().size());
        assertEquals(DecisionType.FINAL, llmResponse.decision());
    }

    @Test
    void shouldExecuteToolThenFinish() {
        // Given: first call → TOOL_CALL, second call → FINAL
        var toolDef = new ToolDefinition("test_tool", "desc",
            new ToolParameters("object", Map.of(), List.of()),
            args -> ToolResult.ok("tool result data"));

        var call1 = new LlmResponse("Need tool", DecisionType.TOOL_CALL,
            new ToolCall("test_tool", Map.of("p", "v")), new TokenUsage(5, 3));
        var call2 = new LlmResponse("Done!", DecisionType.FINAL, null, new TokenUsage(8, 5));

        when(llm.chat(any())).thenReturn(call1, call2);
        when(prompt.build(eq("react-system"), any())).thenReturn(
            new ChatRequest("sys", List.of(), List.of())
        );
        when(memory.loadHistory(anyString(), anyInt())).thenReturn(List.of());
        when(tools.execute(any())).thenReturn(ToolResult.ok("tool result data"));

        // When
        AgentResponse response = engine.execute(new AgentRequest("s2", "use tool"));

        // Then
        assertEquals("Done!", response.content());
        assertEquals(3, response.steps().size()); // TOOL_CALL + TOOL_RESULT + FINAL
        verify(tools).execute(any());
    }
}
```

- [ ] **Step 6: Run tests**

Run: `cd backend && mvn test`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/engine/ backend/src/main/java/com/shopai/agent/llm/LangChain4jAdapter.java backend/src/test/
git commit -m "feat: add ReAct agent execution engine with parser"
```

---

## Task 10: Web Layer (REST + SSE)

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/web/ChatController.java`
- Create: `backend/src/main/java/com/shopai/agent/web/SessionController.java`

- [ ] **Step 1: Create ChatController with SSE**

Create `backend/src/main/java/com/shopai/agent/web/ChatController.java`:

```java
package com.shopai.agent.web;

import com.shopai.agent.domain.AgentRequest;
import com.shopai.agent.domain.AgentResponse;
import com.shopai.agent.domain.StepRecord;
import com.shopai.agent.engine.AgentEngine;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AgentEngine engine;
    private final Map<String, AgentResponse> responseStore = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatController(AgentEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/chat/send")
    public Map<String, Object> send(@RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());
        String message = body.get("message");
        String messageId = UUID.randomUUID().toString();

        // Execute in background so we can return immediately
        executor.submit(() -> {
            AgentResponse response = engine.execute(new AgentRequest(sessionId, message));
            responseStore.put(messageId, response);
        });

        return Map.of(
            "messageId", messageId,
            "streamUrl", "/api/chat/stream/" + messageId
        );
    }

    @GetMapping(value = "/chat/stream/{messageId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String messageId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        executor.submit(() -> {
            try {
                // Poll until response is ready (with timeout)
                AgentResponse response = null;
                int polls = 0;
                while (response == null && polls < 600) { // ~60 second timeout
                    response = responseStore.get(messageId);
                    if (response == null) {
                        Thread.sleep(100);
                        polls++;
                    }
                }

                if (response == null) {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(Map.of("message", "Request timed out")));
                    emitter.complete();
                    return;
                }

                // Send each step
                for (StepRecord step : response.steps()) {
                    emitter.send(SseEmitter.event()
                        .name("step")
                        .data(stepToMap(step)));
                    Thread.sleep(50); // Small delay for animation effect
                }

                // Send final
                emitter.send(SseEmitter.event()
                    .name("final")
                    .data(Map.of(
                        "messageId", messageId,
                        "content", response.content(),
                        "steps", response.steps().stream().map(this::stepToMap).toList(),
                        "tokenUsage", Map.of(
                            "inputTokens", response.totalUsage().inputTokens(),
                            "outputTokens", response.totalUsage().outputTokens()
                        ),
                        "latencyMs", response.latencyMs()
                    )));

                emitter.complete();
                responseStore.remove(messageId);

            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private Map<String, Object> stepToMap(StepRecord step) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("iteration", step.iteration());
        map.put("type", step.type().name());
        if (step.thought() != null) map.put("thought", step.thought());
        if (step.toolCall() != null) {
            map.put("toolName", step.toolCall().name());
            map.put("arguments", step.toolCall().arguments());
        }
        if (step.toolResult() != null) {
            map.put("result", Map.of(
                "success", step.toolResult().success(),
                "content", step.toolResult().content()
            ));
        }
        return map;
    }
}
```

- [ ] **Step 2: Create SessionController**

Create `backend/src/main/java/com/shopai/agent/web/SessionController.java`:

```java
package com.shopai.agent.web;

import com.shopai.agent.domain.Message;
import com.shopai.agent.memory.MemoryManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final JdbcTemplate jdbc;
    private final MemoryManager memory;

    public SessionController(JdbcTemplate jdbc, MemoryManager memory) {
        this.jdbc = jdbc;
        this.memory = memory;
    }

    @GetMapping
    public List<Map<String, Object>> listSessions() {
        return jdbc.queryForList(
            "SELECT session_id, title, created_at FROM conversation ORDER BY created_at DESC"
        );
    }

    @GetMapping("/{sessionId}/messages")
    public List<Map<String, Object>> getMessages(@PathVariable String sessionId) {
        return jdbc.queryForList(
            "SELECT id, role, content, metadata, created_at FROM message WHERE session_id = ? ORDER BY created_at ASC",
            sessionId
        );
    }

    @PostMapping
    public Map<String, String> createSession() {
        String sessionId = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO conversation (id, session_id, title) VALUES (?, ?, ?)",
            UUID.randomUUID().toString(), sessionId, "新会话"
        );
        return Map.of("sessionId", sessionId);
    }

    @DeleteMapping("/{sessionId}")
    public Map<String, String> deleteSession(@PathVariable String sessionId) {
        memory.clear(sessionId);
        jdbc.update("DELETE FROM conversation WHERE session_id = ?", sessionId);
        return Map.of("status", "deleted");
    }
}
```

- [ ] **Step 3: Add CORS config to application.yml**

Edit `backend/src/main/resources/application.yml` — add:

```yaml
  # Add under spring: section (no leading spaces issue, just add these lines)
  # Actually, Spring Boot auto-config handles CORS. Add a config class instead:
```

Better approach: Edit `backend/src/main/java/com/shopai/agent/config/AgentConfig.java` — add:

```java
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Inside AgentConfig class, add:
@Bean
public WebMvcConfigurer corsConfigurer() {
    return new WebMvcConfigurer() {
        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS");
        }
    };
}
```

- [ ] **Step 4: Compile and verify**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/web/ backend/src/main/java/com/shopai/agent/config/AgentConfig.java
git commit -m "feat: add REST controllers with SSE streaming endpoint"
```

---

## Task 11: Frontend — Type Definitions, API, Store

**Files:**
- Create: `frontend/src/types/index.ts`
- Create: `frontend/src/api/chat.ts`
- Create: `frontend/src/store/chatStore.ts`
- Create: `frontend/src/hooks/useSSE.ts`

- [ ] **Step 1: Create TypeScript type definitions**

Create `frontend/src/types/index.ts`:

```typescript
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
```

- [ ] **Step 2: Create API layer**

Create `frontend/src/api/chat.ts`:

```typescript
const BASE = '/api';

export async function sendMessage(sessionId: string, message: string): Promise<{ messageId: string; streamUrl: string }> {
  const res = await fetch(`${BASE}/chat/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, message }),
  });
  if (!res.ok) throw new Error(`Send failed: ${res.status}`);
  return res.json();
}

export async function fetchSessions(): Promise<{ SESSION_ID: string; TITLE: string; CREATED_AT: string }[]> {
  const res = await fetch(`${BASE}/sessions`);
  if (!res.ok) throw new Error(`Fetch sessions failed: ${res.status}`);
  return res.json();
}

export async function fetchMessages(sessionId: string): Promise<{ ID: string; ROLE: string; CONTENT: string; METADATA: string; CREATED_AT: string }[]> {
  const res = await fetch(`${BASE}/sessions/${sessionId}/messages`);
  if (!res.ok) throw new Error(`Fetch messages failed: ${res.status}`);
  return res.json();
}

export async function createSession(): Promise<{ sessionId: string }> {
  const res = await fetch(`${BASE}/sessions`, { method: 'POST' });
  if (!res.ok) throw new Error(`Create session failed: ${res.status}`);
  return res.json();
}

export async function deleteSession(sessionId: string): Promise<void> {
  await fetch(`${BASE}/sessions/${sessionId}`, { method: 'DELETE' });
}
```

- [ ] **Step 3: Create Zustand store**

Create `frontend/src/store/chatStore.ts`:

```typescript
import { create } from 'zustand';
import type { Conversation, StepRecord } from '../types';

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

export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  steps?: StepRecord[];
  tokenUsage?: { inputTokens: number; outputTokens: number };
  latencyMs?: number;
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
```

- [ ] **Step 4: Create useSSE hook**

Create `frontend/src/hooks/useSSE.ts`:

```typescript
import { useEffect, useRef } from 'react';
import { useChatStore } from '../store/chatStore';
import type { SSEStepEvent, AgentFinal, StepRecord } from '../types';

export function useSSE() {
  const { addStep, clearSteps, addMessage, updateLastAssistant, setIsStreaming, currentSteps } = useChatStore();
  const eventSourceRef = useRef<EventSource | null>(null);

  const connect = (streamUrl: string) => {
    disconnect();
    clearSteps();

    const es = new EventSource(streamUrl);
    eventSourceRef.current = es;
    setIsStreaming(true);

    es.addEventListener('step', (e: MessageEvent) => {
      const data: SSEStepEvent = JSON.parse(e.data);
      const step: StepRecord = {
        iteration: data.iteration,
        type: data.type,
        thought: data.thought,
        toolName: data.toolName,
        arguments: data.arguments,
        result: data.result,
      };
      addStep(step);
    });

    es.addEventListener('final', (e: MessageEvent) => {
      const data: AgentFinal = JSON.parse(e.data);
      addMessage({
        id: data.messageId,
        role: 'assistant',
        content: data.content,
        steps: data.steps,
        tokenUsage: data.tokenUsage,
        latencyMs: data.latencyMs,
      });
      setIsStreaming(false);
      es.close();
    });

    es.addEventListener('error', () => {
      setIsStreaming(false);
      es.close();
    });

    es.onerror = () => {
      setIsStreaming(false);
      es.close();
    };
  };

  const disconnect = () => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
      eventSourceRef.current = null;
    }
    setIsStreaming(false);
  };

  useEffect(() => {
    return () => disconnect();
  }, []);

  return { connect, disconnect };
}
```

- [ ] **Step 5: Verify frontend compiles**

Run: `cd frontend && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/ frontend/src/api/ frontend/src/store/ frontend/src/hooks/
git commit -m "feat: add frontend types, API layer, store, and SSE hook"
```

---

## Task 12: Frontend — React Components

**Files:**
- Create: `frontend/src/components/LoadingDots.tsx`
- Create: `frontend/src/components/InputBar.tsx`
- Create: `frontend/src/components/AgentSteps.tsx`
- Create: `frontend/src/components/MessageBubble.tsx`
- Create: `frontend/src/components/MessageList.tsx`
- Create: `frontend/src/components/Sidebar.tsx`
- Create: `frontend/src/components/ChatArea.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Create LoadingDots**

Create `frontend/src/components/LoadingDots.tsx`:

```tsx
export default function LoadingDots() {
  return (
    <div className="flex items-center gap-1 px-4 py-2">
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
          style={{ animationDelay: `${i * 0.15}s` }}
        />
      ))}
      <span className="text-xs text-gray-400 ml-1">思考中...</span>
    </div>
  );
}
```

- [ ] **Step 2: Create InputBar**

Create `frontend/src/components/InputBar.tsx`:

```tsx
import { useState, useRef, useEffect } from 'react';

interface Props {
  onSend: (message: string) => void;
  disabled: boolean;
}

export default function InputBar({ onSend, disabled }: Props) {
  const [input, setInput] = useState('');
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!disabled && ref.current) ref.current.focus();
  }, [disabled]);

  const handleSend = () => {
    const trimmed = input.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setInput('');
  };

  return (
    <div className="border-t border-gray-200 bg-white p-4">
      <div className="flex gap-3 max-w-3xl mx-auto">
        <textarea
          ref={ref}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          placeholder="输入消息... (Enter 发送，Shift+Enter 换行)"
          rows={1}
          className="flex-1 resize-none rounded-lg border border-gray-300 px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent"
          disabled={disabled}
        />
        <button
          onClick={handleSend}
          disabled={disabled || !input.trim()}
          className="px-6 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          发送
        </button>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: Create AgentSteps**

Create `frontend/src/components/AgentSteps.tsx`:

```tsx
import { useState } from 'react';
import type { StepRecord } from '../types';

export default function AgentSteps({ steps }: { steps: StepRecord[] }) {
  const [expanded, setExpanded] = useState(false);

  if (!steps || steps.length === 0) return null;

  const stepLabel = (step: StepRecord): string => {
    switch (step.type) {
      case 'THOUGHT': return `💭 ${step.thought?.slice(0, 60)}...`;
      case 'TOOL_CALL': return `🔧 调用工具: ${step.toolName}`;
      case 'TOOL_RESULT': return step.result?.success ? `✅ 工具返回` : `❌ 工具失败`;
      case 'FINAL': return `📝 生成回复`;
      default: return step.type;
    }
  };

  return (
    <div className="mt-2 border-t border-gray-100 pt-2">
      <button
        onClick={() => setExpanded(!expanded)}
        className="text-xs text-indigo-500 hover:text-indigo-700 font-medium"
      >
        {expanded ? '▾ 隐藏推理过程' : `▸ 查看推理过程 (${steps.length} 步)`}
      </button>
      {expanded && (
        <div className="mt-2 space-y-1 text-xs text-gray-600">
          {steps.map((step, i) => (
            <div key={i} className="bg-gray-50 rounded px-3 py-1.5">
              <span className="font-mono text-gray-400">[{step.iteration}]</span>{' '}
              {stepLabel(step)}
              {step.type === 'TOOL_RESULT' && step.result && (
                <div className="mt-1 text-gray-500 pl-4 border-l-2 border-gray-200">
                  {step.result.content.slice(0, 100)}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Create MessageBubble**

Create `frontend/src/components/MessageBubble.tsx`:

```tsx
import AgentSteps from './AgentSteps';
import type { ChatMessage } from '../store/chatStore';

export default function MessageBubble({ msg }: { msg: ChatMessage }) {
  const isUser = msg.role === 'user';

  return (
    <div className={`flex gap-3 ${isUser ? 'justify-end' : 'justify-start'} mb-4`}>
      {/* Avatar */}
      <div className={`w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold shrink-0 ${isUser ? 'order-2 bg-gray-400' : 'order-1 bg-indigo-600'}`}>
        {isUser ? 'U' : 'AI'}
      </div>

      {/* Bubble */}
      <div className={`max-w-[70%] ${isUser ? 'order-1' : 'order-2'}`}>
        <div className={`px-4 py-2.5 rounded-2xl text-sm leading-relaxed ${
          isUser
            ? 'bg-indigo-600 text-white rounded-tr-sm'
            : 'bg-white text-gray-800 rounded-tl-sm shadow-sm border border-gray-100'
        }`}>
          {msg.content}
          {msg.tokenUsage && (
            <div className="mt-2 text-[10px] opacity-50">
              Tokens: {msg.tokenUsage.inputTokens} in / {msg.tokenUsage.outputTokens} out
              {msg.latencyMs && ` · ${msg.latencyMs}ms`}
            </div>
          )}
        </div>
        {msg.steps && msg.steps.length > 0 && <AgentSteps steps={msg.steps} />}
      </div>
    </div>
  );
}
```

- [ ] **Step 5: Create MessageList**

Create `frontend/src/components/MessageList.tsx`:

```tsx
import { useEffect, useRef } from 'react';
import MessageBubble from './MessageBubble';
import LoadingDots from './LoadingDots';
import type { ChatMessage } from '../store/chatStore';

interface Props {
  messages: ChatMessage[];
  isStreaming: boolean;
}

export default function MessageList({ messages, isStreaming }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isStreaming]);

  return (
    <div className="flex-1 overflow-y-auto px-4 py-6">
      <div className="max-w-3xl mx-auto">
        {messages.length === 0 && !isStreaming && (
          <div className="text-center text-gray-400 mt-20">
            <div className="text-5xl mb-4">🤖</div>
            <p className="text-lg font-medium">欢迎使用 ShopAI 智能客服</p>
            <p className="text-sm mt-2">可以问我订单状态、产品信息或退换货政策</p>
            <div className="mt-6 flex flex-wrap gap-2 justify-center">
              {['查订单 20240611001', '推荐5000以内的手机', 'AirPods多少钱'].map((q) => (
                <span key={q} className="text-xs bg-gray-100 text-gray-600 px-3 py-1 rounded-full">{q}</span>
              ))}
            </div>
          </div>
        )}
        {messages.map((msg) => (
          <MessageBubble key={msg.id} msg={msg} />
        ))}
        {isStreaming && <LoadingDots />}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
```

- [ ] **Step 6: Create Sidebar**

Create `frontend/src/components/Sidebar.tsx`:

```tsx
import { useEffect } from 'react';
import { useChatStore } from '../store/chatStore';
import { fetchSessions, createSession, deleteSession } from '../api/chat';

export default function Sidebar() {
  const { sessions, currentSessionId, setSessions, setCurrentSession, clearMessages } = useChatStore();

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    try {
      const data = await fetchSessions();
      setSessions(data.map((s) => ({
        sessionId: s.SESSION_ID,
        title: s.TITLE,
        createdAt: s.CREATED_AT,
      })));
    } catch (e) {
      console.error('Failed to load sessions', e);
    }
  };

  const handleNewSession = async () => {
    try {
      const { sessionId } = await createSession();
      setCurrentSession(sessionId);
      clearMessages();
      await loadSessions();
    } catch (e) {
      console.error('Failed to create session', e);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteSession(id);
      if (currentSessionId === id) {
        setCurrentSession('');
        clearMessages();
      }
      await loadSessions();
    } catch (e) {
      console.error('Failed to delete session', e);
    }
  };

  return (
    <div className="w-64 bg-gray-900 text-white flex flex-col h-full">
      <div className="p-4 border-b border-gray-700">
        <h1 className="text-lg font-bold">💬 ShopAI 客服</h1>
        <p className="text-xs text-gray-400 mt-1">AI Agent Platform</p>
      </div>
      <div className="p-3">
        <button
          onClick={handleNewSession}
          className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 rounded-lg text-sm font-medium transition-colors"
        >
          + 新会话
        </button>
      </div>
      <div className="flex-1 overflow-y-auto px-2">
        {sessions.map((s) => (
          <div
            key={s.sessionId}
            onClick={() => setCurrentSession(s.sessionId)}
            className={`flex items-center justify-between px-3 py-2 rounded-lg cursor-pointer text-sm mb-1 transition-colors ${
              currentSessionId === s.sessionId
                ? 'bg-indigo-700 text-white'
                : 'text-gray-300 hover:bg-gray-800'
            }`}
          >
            <span className="truncate flex-1">{s.title}</span>
            <button
              onClick={(e) => { e.stopPropagation(); handleDelete(s.sessionId); }}
              className="ml-2 text-gray-500 hover:text-red-400 text-xs opacity-0 hover:opacity-100 transition-opacity"
              title="删除"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 7: Create ChatArea**

Create `frontend/src/components/ChatArea.tsx`:

```tsx
import { useChatStore } from '../store/chatStore';
import { useSSE } from '../hooks/useSSE';
import { sendMessage } from '../api/chat';
import MessageList from './MessageList';
import InputBar from './InputBar';

export default function ChatArea() {
  const { messages, addMessage, isStreaming, setIsStreaming, currentSessionId } = useChatStore();
  const { connect } = useSSE();

  const handleSend = async (text: string) => {
    if (!currentSessionId) return;

    const userMsg = { id: crypto.randomUUID(), role: 'user' as const, content: text };
    addMessage(userMsg);
    setIsStreaming(true);

    try {
      const { streamUrl } = await sendMessage(currentSessionId, text);
      connect(streamUrl);
    } catch (e) {
      console.error('Send failed', e);
      addMessage({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '抱歉，发送消息时出现错误，请稍后再试。',
      });
      setIsStreaming(false);
    }
  };

  return (
    <div className="flex-1 flex flex-col bg-gray-50 h-full">
      <MessageList messages={messages} isStreaming={isStreaming} />
      <InputBar onSend={handleSend} disabled={isStreaming} />
    </div>
  );
}
```

- [ ] **Step 8: Update App.tsx**

Edit `frontend/src/App.tsx` — replace with:

```tsx
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';

export default function App() {
  return (
    <div className="h-screen flex">
      <Sidebar />
      <ChatArea />
    </div>
  );
}
```

- [ ] **Step 9: Verify frontend builds**

Run: `cd frontend && npx tsc --noEmit`
Expected: No TypeScript errors

- [ ] **Step 10: Commit**

```bash
git add frontend/src/
git commit -m "feat: add all React chat UI components"
```

---

## Task 13: End-to-End Verification

**Files:** None (manual verification)

- [ ] **Step 1: Start backend**

Run: `cd backend && mvn spring-boot:run`
Expected: App starts on port 8080, logs "Loaded 10 products", "Loaded 5 orders"

- [ ] **Step 2: Start frontend**

Run (in another terminal): `cd frontend && npm run dev`
Expected: Vite dev server on port 3000

- [ ] **Step 3: Test API health**

Run: `curl http://localhost:8080/api/sessions`
Expected: `[]` (empty or with seeded sessions)

- [ ] **Step 4: Test create session + send message**

Run:
```bash
# Create session
curl -X POST http://localhost:8080/api/sessions

# Send a message (use the returned sessionId)
curl -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<sessionId>","message":"查订单 20240611001"}'
```
Expected: Returns `{"messageId":"..." ,"streamUrl":"/api/chat/stream/..."}`

- [ ] **Step 5: Test SSE stream**

Run:
```bash
curl -N http://localhost:8080/api/chat/stream/<messageId>
```
Expected: SSE events stream (step events then final event)

- [ ] **Step 6: Open browser**

Open `http://localhost:3000` in browser.
Expected: Chat UI with sidebar showing "新会话" button and empty chat area

- [ ] **Step 7: End-to-end conversational test**

1. Click "+ 新会话" to create a new chat
2. Type "查订单 20240611001" and press Enter
3. Observe: Loading dots appear → AgentSteps show tool call → Final answer displays
4. Type "这些产品的价格是多少" and press Enter
5. Observe: Another ReAct cycle, possible tool chain

- [ ] **Step 8: Verify Phase 1 acceptance criteria**

- ✅ User can type a message in the React chat window
- ✅ Agent automatically calls OrderQueryTool when asked about an order
- ✅ Agent returns order status in natural language
- ✅ Conversation history persists (reload page, select session)
- ✅ AgentSteps are visible and expandable to show reasoning

- [ ] **Step 9: Commit any final fixes**

```bash
git add -A
git commit -m "chore: end-to-end verification fixes"
```

---

## Plan Self-Review

| Check | Status |
|-------|--------|
| Spec coverage — all Phase 1 modules addressed | ✅ Engine, Tool, Memory, Prompt, LLM, Web, Frontend, Mock Data |
| No placeholders (TBD/TODO) | ✅ Every step has concrete code |
| Type consistency across tasks | ✅ Domain models defined in Task 3, used consistently through Tasks 5-10 |
| File paths exact | ✅ All paths relative to project root |
| Each task independently testable | ✅ Each task ends with compile or run verification |
