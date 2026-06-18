# LangChain4j AiServices Tool Refactor — Design Spec

Date: 2026-06-16 | Status: approved

## Problem

Current tool system is custom-built, bypassing LangChain4j's native capabilities:
- Manual text parsing (`LlmResponseParser`) to interpret LLM output — fragile (FINAL parsing bug confirmed)
- Custom `ToolDefinition`/`ToolRegistry` hierarchy — replaces LangChain4j's `@Tool` annotation
- Plain `model.generate(prompt)` calls — no structured tool calling
- Manual ReAct while-loop — reinvents `AiServices` built-in loop

## Target

Fully adopt LangChain4j AiServices (high-level API) for tool calling. Use `@Tool` annotations for tool definitions, `StreamingChatLanguageModel` for streaming, and `ChatMemoryStore` backed by H2 for persistence.

## Architecture Changes

### Tool Layer

```java
// Old: ToolDefinition + ToolRegistry + ToolParameters
// New: @Tool annotation only
@Component
public class ProductSearchTool {
    @Tool("搜索电子产品，支持关键词、分类、价格范围筛选。参数均为可选，至少提供一个")
    public String searchProducts(
        @P("搜索关键词") String keyword,
        @P("分类: phone/computer/accessory") String category,
        @P("最低价格") Double minPrice,
        @P("最高价格") Double maxPrice) { ... }
}
```

- **Delete**: `ToolRegistry`, `DefaultToolRegistry`, `ToolDefinition`, `ToolParameters`, `ParamSchema`, `ToolCall`, `ToolResult`
- Each tool class remains a `@Component`, but methods use `@Tool` + `@P` annotations
- LangChain4j auto-generates `ToolSpecification` (JSON Schema) from method signatures

### Agent Interface

```java
// Defines the contract LangChain4j implements
public interface ShopAiAgent {
    TokenStream chat(@UserMessage String userMessage);
}
```

- `TokenStream` supports streaming callbacks: `onPartialResponse`, `onToolExecuted`, `onCompleteResponse`, `onError`
- No manual loop — LangChain4j handles the ReAct cycle internally
- `@UserMessage` injects the user's message into the chat template

### LLM Adapter Upgrade

```java
// Old: ChatLanguageModel model.generate(prompt)
// New: StreamingChatLanguageModel with token-level streaming
StreamingChatLanguageModel model = OpenAiStreamingChatModel.builder()
    .apiKey(apiKey)
    .modelName("deepseek-chat")
    .baseUrl("https://api.deepseek.com/v1")
    .timeout(Duration.ofSeconds(30))
    .build();
```

- `OpenAiStreamingChatModel` uses DeepSeek's OpenAI-compatible streaming + function calling API
- Must verify DeepSeek's function calling support (OpenAI-compatible, should work)

### Memory — H2 Persistence via ChatMemoryStore

```java
// H2MemoryManager implements ChatMemoryStore (LangChain4j SPI)
@Component
public class H2MemoryManager implements ChatMemoryStore {
    @Override public List<ChatMessage> getMessages(Object memoryId) { ... }
    @Override public void updateMessages(Object memoryId, List<ChatMessage> messages) { ... }
    @Override public void deleteMessages(Object memoryId) { ... }
}
```

- `ChatMemoryStore` is LangChain4j's SPI for custom memory backends
- Persists to existing H2 `session_messages` table
- `MessageWindowChatMemory` wraps it to enforce max-message window

### System Prompt

```java
@SystemMessage("""
    You are a customer service AI for ShopAI, an electronics e-commerce platform.
    Use Chinese to communicate with users. Greet warmly and provide helpful answers.
    When you have enough information to answer the user, provide the final answer directly.
    """)
```

- `@SystemMessage` on the agent interface replaces Mustache template rendering
- **Delete**: `MustachePromptEngine`, `PromptEngine`, `react-system.mustache`, `tool-result.mustache`

### SSE Step Visualization

| TokenStream callback | SSE event | Frontend effect |
|---|---|---|
| `onToolExecuted(tool)` | `step: tool_call` → `step: tool_result` | Step cards in chat |
| `onPartialResponse(token)` | `token: xxx` | Character-by-character streaming |
| `onCompleteResponse(resp)` | `done: {tokenUsage, latencyMs}` | Complete signal + metadata |

- Frontend unchanged — SSE event format preserved

## File Change Manifest

| Action | File | Reason |
|--------|------|--------|
| **NEW** | `agent/ShopAiAgent.java` | Agent interface for AiServices |
| **REWRITE** | `tool/ProductSearchTool.java` | Add `@Tool` annotation |
| **REWRITE** | `tool/OrderQueryTool.java` | Add `@Tool` annotation |
| **REWRITE** | `tool/CalculatorTool.java` | Add `@Tool` annotation |
| **REWRITE** | `llm/LangChain4jAdapter.java` | Switch to `StreamingChatLanguageModel` |
| **REWRITE** | `engine/ReActAgentEngine.java` | Wrap AiServices, expose `TokenStream` |
| **REWRITE** | `web/ChatController.java` | Use `TokenStream` callbacks for SSE |
| **REWRITE** | `config/AgentConfig.java` | New bean wiring |
| **REWRITE** | `memory/H2MemoryManager.java` | Implement `ChatMemoryStore` |
| **DELETE** | `engine/LlmResponseParser.java` | No more text parsing |
| **DELETE** | `tool/ToolRegistry.java`, `tool/DefaultToolRegistry.java` | Replaced by AiServices |
| **DELETE** | `domain/ToolDefinition.java`, `domain/ToolCall.java`, `domain/ToolResult.java`, `domain/ToolParameters.java`, `domain/ParamSchema.java` | Replaced by LangChain4j types |
| **DELETE** | `domain/DecisionType.java`, `domain/LlmResponse.java` | No more manual decision parsing |
| **DELETE** | `prompt/MustachePromptEngine.java`, `prompt/PromptEngine.java` | Replaced by `@SystemMessage` |
| **DELETE** | `memory/MemoryManager.java` | Replaced by `ChatMemoryStore` |
| **DELETE** | `resources/prompts/react-system.mustache`, `resources/prompts/tool-result.mustache` | Replaced by annotations |
| **UPDATE** | `pom.xml` | Potentially bump langchain4j version |

## Key Risk: DeepSeek Function Calling

DeepSeek's API is OpenAI-compatible but `deepseek-chat` model's function calling capability needs verification. If function calling is not supported:

**Fallback**: Use Low-Level API (Plan B) — generate `ToolSpecification` from `@Tool` methods, inject into `ChatLanguageModel.generate(messages, toolSpecifications)`, manually execute tool calls, loop. This still eliminates text parsing but retains manual loop control.
