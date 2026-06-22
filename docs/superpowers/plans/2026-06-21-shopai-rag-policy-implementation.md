# RAG 售后政策知识库 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ShopAI Agent 新增 RAG 售后政策知识库 — Agent 自动检索政策条款并引用原文回答

**Architecture:** Python Embedding Sidecar (BAAI/bge-small-zh-v1.5) + Chroma Docker + LangChain4j 标准 RAG API (EmbeddingModel / ChromaEmbeddingStore / EmbeddingStoreIngestor) + PolicyQueryTool 集成到现有 ReAct Agent

**Tech Stack:** LangChain4j 1.16.2 (chroma / document-splitter), Chroma Docker, BAAI/bge-small-zh-v1.5 + Flask Sidecar, Spring Boot 3.3, Java 21, React 18 + TypeScript + Zustand

## Global Constraints

- `langchain4j.version`: 1.16.2
- 模型路径: `D:\models\huggingface`
- 模型名: `BAAI/bge-small-zh-v1.5`
- Chroma 端口: `localhost:8000`
- Sidecar 端口: `localhost:9876`
- Top-K 默认: 3
- Chunk 大小: 1000 字符 (DocumentByParagraphSplitter)
- MVP 不做增量索引、不做 sidecar 自动重启、不做混合检索

---

## 文件结构

```
新增文件:
  backend/embedding_sidecar.py                     # Python sidecar 服务
  backend/src/main/resources/policies/
    return-policy.md                               # 退换货政策 (示例文档)
    warranty.md                                    # 保修政策 (示例文档)
    shipping.md                                    # 物流说明 (示例文档)
  backend/src/main/java/com/shopai/agent/rag/
    Text2VecEmbeddingModel.java                    # EmbeddingModel 实现 + sidecar 管理
    PolicyRagService.java                          # 检索编排
    ResultFormatter.java                           # 结果格式化
    DocumentIndexService.java                      # 索引构建 (EmbeddingStoreIngestor)
  backend/src/main/java/com/shopai/agent/tool/
    PolicyQueryTool.java                           # @Tool 供 Agent 调用
  backend/src/main/java/com/shopai/agent/web/
    PolicyController.java                          # 知识库管理 REST API
  frontend/src/api/knowledge.ts                    # API 调用函数
  frontend/src/store/knowledgeStore.ts             # Zustand store
  frontend/src/components/KnowledgeManager.tsx     # 管理页面组件

修改文件:
  backend/pom.xml                                  # 新增 langchain4j-chroma 依赖
  backend/src/main/resources/application.yml       # 新增 shopai.rag 配置段
  backend/src/main/java/com/shopai/agent/config/AgentConfig.java  # 注册新 Tool + Chroma Bean
  frontend/src/App.tsx                             # 加入 /admin/knowledge 路由
  README.md                                        # 更新技术栈 + 快速启动
  docs/ONBOARDING.md                               # 新增 RAG 模块说明
```

---

### Task 1: Python Embedding Sidecar

**Files:**
- Create: `backend/embedding_sidecar.py`
- Create: `backend/requirements-sidecar.txt`

**Interfaces:**
- Consumes: (none — standalone)
- Produces: HTTP `POST /encode` `{"texts": [...]}` → `{"embeddings": [[...], ...]}`, `GET /health` → `{"status":"ok"}`

- [ ] **Step 1: 编写 Python sidecar 脚本**

创建 `backend/embedding_sidecar.py`:

```python
"""Text2Vec Embedding Sidecar — HTTP wrapper for BAAI/bge-small-zh-v1.5."""
import sys
import os
from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer

MODEL_PATH = os.environ.get("MODEL_PATH", r"D:\models\huggingface")
MODEL_NAME = os.environ.get("MODEL_NAME", "BAAI/bge-small-zh-v1.5")
PORT = int(os.environ.get("PORT", "9876"))

app = Flask(__name__)

print(f"Loading model {MODEL_NAME} from {MODEL_PATH}...")
model = SentenceTransformer(MODEL_NAME, cache_folder=MODEL_PATH)
print("Model loaded.")


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "model": MODEL_NAME})


@app.route("/encode", methods=["POST"])
def encode():
    data = request.get_json()
    texts = data.get("texts", [])
    if not texts:
        return jsonify({"error": "texts is required"}), 400
    embeddings = model.encode(texts, normalize_embeddings=True)
    return jsonify({"embeddings": [e.tolist() for e in embeddings]})


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=PORT)
```

- [ ] **Step 2: 编写 Python 依赖文件**

创建 `backend/requirements-sidecar.txt`:

```
flask>=3.0
sentence-transformers>=2.7
torch>=2.0
```

- [ ] **Step 3: 本地启动测试 sidecar**

```bash
cd backend
pip install -r requirements-sidecar.txt
# 确保 MODEL_PATH 目录下有 BAAI/bge-small-zh-v1.5 模型
set MODEL_PATH=D:\models\huggingface
set MODEL_NAME=BAAI/bge-small-zh-v1.5
set PORT=9876
python embedding_sidecar.py
```

在另一个终端测试:

```bash
curl http://localhost:9876/health
# 预期: {"model":"BAAI/bge-small-zh-v1.5","status":"ok"}

curl -X POST http://localhost:9876/encode -H "Content-Type: application/json" -d "{\"texts\":[\"你好\"]}"
# 预期: {"embeddings":[[... 512维浮点数组 ...]]}
```

- [ ] **Step 4: Commit**

```bash
git add backend/embedding_sidecar.py backend/requirements-sidecar.txt
git commit -m "feat: 添加 BAAI/bge-small-zh-v1.5 Embedding sidecar (Flask)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Maven 依赖 + 配置

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:**
- Consumes: (none — infra setup)
- Produces: `langchain4j-chroma` on classpath; `shopai.rag.*` config properties available via `@Value`

- [ ] **Step 1: 添加 langchain4j-chroma 依赖到 pom.xml**

```xml
<!-- 在 langchain4j-http-client-jdk 之下添加 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-chroma</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

- [ ] **Step 2: 添加 RAG 配置段到 application.yml**

在 `shopai.agent:` 段之后添加:

```yaml
  rag:
    embedding:
      model-path: D:/models/huggingface
      model-name: BAAI/bge-small-zh-v1.5
      sidecar-port: 9876
      sidecar-startup-timeout-seconds: 30
    chroma:
      host: localhost
      port: 8000
      collection: shopai_policies
    retrieval:
      top-k: 3
      min-score: 0.5
    policies-dir: policies/
```

- [ ] **Step 3: 验证配置正确加载**

```bash
cd backend
mvn validate -q
# 预期: BUILD SUCCESS, YAML 语法正确
```

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml
git commit -m "feat: 添加 langchain4j-chroma 依赖和 RAG 配置

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Text2VecEmbeddingModel — LC4j EmbeddingModel 实现

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/rag/Text2VecEmbeddingModel.java`

**Interfaces:**
- Consumes: `shopai.rag.embedding.*` config properties, Python sidecar on localhost
- Produces: `EmbeddingModel embed(String)` / `embedAll(List<TextSegment>)` → `dev.langchain4j.data.embedding.Embedding`

- [ ] **Step 1: 创建 Text2VecEmbeddingModel**

```java
package com.shopai.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class Text2VecEmbeddingModel implements EmbeddingModel, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(Text2VecEmbeddingModel.class);

    @Value("${shopai.rag.embedding.model-path}")
    private String modelPath;

    @Value("${shopai.rag.embedding.model-name}")
    private String modelName;

    @Value("${shopai.rag.embedding.sidecar-port:9876}")
    private int sidecarPort;

    @Value("${shopai.rag.embedding.sidecar-startup-timeout-seconds:30}")
    private int startupTimeoutSeconds;

    private Process pythonProcess;
    private final RestTemplate restTemplate = new RestTemplate();
    private String sidecarUrl;

    @PostConstruct
    public void startSidecar() {
        sidecarUrl = "http://127.0.0.1:" + sidecarPort;
        try {
            log.info("Starting embedding sidecar: model={}, port={}", modelName, sidecarPort);
            ProcessBuilder pb = new ProcessBuilder(
                "python", "embedding_sidecar.py"
            );
            pb.environment().put("MODEL_PATH", modelPath);
            pb.environment().put("MODEL_NAME", modelName);
            pb.environment().put("PORT", String.valueOf(sidecarPort));
            pb.redirectErrorStream(true);
            pythonProcess = pb.start();

            // 等待 health 就绪
            long deadline = System.currentTimeMillis() + startupTimeoutSeconds * 1000L;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Map<?, ?> health = restTemplate.getForObject(sidecarUrl + "/health", Map.class);
                    if (health != null && "ok".equals(health.get("status"))) {
                        log.info("Embedding sidecar ready: {}", health);
                        return;
                    }
                } catch (RestClientException ignored) {
                    // sidecar 尚未就绪
                }
                Thread.sleep(1000);
            }
            throw new IllegalStateException("Embedding sidecar failed to start within " + startupTimeoutSeconds + "s");
        } catch (Exception e) {
            log.error("Failed to start embedding sidecar", e);
            throw new RuntimeException("Failed to start embedding sidecar", e);
        }
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        List<String> texts = segments.stream().map(TextSegment::text).toList();
        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                sidecarUrl + "/encode",
                Map.of("texts", texts),
                Map.class
            );

            if (response == null || !response.containsKey("embeddings")) {
                throw new RuntimeException("Empty or invalid response from embedding sidecar");
            }

            @SuppressWarnings("unchecked")
            List<List<Double>> rawEmbeddings = (List<List<Double>>) response.get("embeddings");
            List<Embedding> embeddings = new ArrayList<>();
            for (List<Double> raw : rawEmbeddings) {
                float[] vector = new float[raw.size()];
                for (int i = 0; i < raw.size(); i++) {
                    vector[i] = raw.get(i).floatValue();
                }
                embeddings.add(Embedding.from(vector));
            }
            return Response.from(embeddings);
        } catch (RestClientException e) {
            log.error("Embedding sidecar call failed", e);
            throw new RuntimeException("Embedding service unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            log.info("Shutting down embedding sidecar");
            pythonProcess.destroyForcibly();
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
# 预期: BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/rag/Text2VecEmbeddingModel.java
git commit -m "feat: 实现 Text2VecEmbeddingModel — LC4j EmbeddingModel 接口 + Python sidecar 管理

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: ChromaEmbeddingStore Bean

**Files:**
- Modify: `backend/src/main/java/com/shopai/agent/config/AgentConfig.java`

**Interfaces:**
- Consumes: `shopai.rag.chroma.*` config properties
- Produces: `ChromaEmbeddingStore` bean (type `EmbeddingStore<TextSegment>`)

- [ ] **Step 1: 在 AgentConfig 中添加 ChromaEmbeddingStore Bean**

在 `AgentConfig.java` 中，类注释之后、`@Value` 字段之后添加：

```java
@Value("${shopai.rag.chroma.host}")
private String chromaHost;

@Value("${shopai.rag.chroma.port}")
private int chromaPort;

@Value("${shopai.rag.chroma.collection}")
private String chromaCollection;
```

在 `chatMemoryStore` bean 之后添加：

```java
@Bean
public EmbeddingStore<TextSegment> embeddingStore() {
    return ChromaEmbeddingStore.builder()
        .baseUrl("http://" + chromaHost + ":" + chromaPort)
        .collectionName(chromaCollection)
        .build();
}
```

需要添加 import:

```java
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
# 预期: BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/config/AgentConfig.java
git commit -m "feat: 注册 ChromaEmbeddingStore Bean

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: PolicyRagService + ResultFormatter

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/rag/ResultFormatter.java`
- Create: `backend/src/main/java/com/shopai/agent/rag/PolicyRagService.java`

**Interfaces:**
- Consumes: `EmbeddingModel` bean, `EmbeddingStore<TextSegment>` bean, `shopai.rag.retrieval.*` config
- Produces: `PolicyRagService.query(String question)` → 格式化的检索结果字符串

- [ ] **Step 1: 创建 ResultFormatter**

```java
package com.shopai.agent.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.List;

final class ResultFormatter {

    private ResultFormatter() {}

    static String format(List<EmbeddingMatch<TextSegment>> matches) {
        if (matches == null || matches.isEmpty()) {
            return "【相关政策条款】\n未找到相关的政策条款。请根据您已有的知识回答用户，并建议用户联系人工客服获取最新政策信息。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【相关政策条款】\n\n");

        for (int i = 0; i < matches.size(); i++) {
            EmbeddingMatch<TextSegment> match = matches.get(i);
            TextSegment seg = match.embedded();
            double score = match.score();
            String docName = seg.metadata().getString("file_name");
            if (docName == null) docName = seg.metadata().getString("absolute_directory_path");

            sb.append(String.format("%d. [来源: %s] (相关度: %.0f%%)\n", i + 1, docName, score * 100));
            sb.append("   ").append(seg.text()).append("\n\n");
        }

        sb.append("请根据以上政策条款回答用户的问题，并在回答中明确引用具体条款。");
        return sb.toString();
    }
}
```

- [ ] **Step 2: 创建 PolicyRagService**

```java
package com.shopai.agent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PolicyRagService {

    private static final Logger log = LoggerFactory.getLogger(PolicyRagService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int topK;
    private final double minScore;

    public PolicyRagService(
        EmbeddingModel embeddingModel,
        EmbeddingStore<TextSegment> embeddingStore,
        @Value("${shopai.rag.retrieval.top-k:3}") int topK,
        @Value("${shopai.rag.retrieval.min-score:0.5}") double minScore
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.topK = topK;
        this.minScore = minScore;
    }

    public String query(String question) {
        long start = System.currentTimeMillis();

        Embedding queryEmbedding = embeddingModel.embed(question).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(topK)
            .minScore(minScore)
            .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        long elapsed = System.currentTimeMillis() - start;
        log.info("RAG query: '{}' → {} results in {}ms", question, matches.size(), elapsed);

        return ResultFormatter.format(matches);
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile -q
# 预期: BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/rag/ResultFormatter.java backend/src/main/java/com/shopai/agent/rag/PolicyRagService.java
git commit -m "feat: PolicyRagService + ResultFormatter — LC4j EmbeddingSearchRequest 检索编排

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: DocumentIndexService — 索引构建

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/rag/DocumentIndexService.java`

**Interfaces:**
- Consumes: `EmbeddingModel` bean, `EmbeddingStore<TextSegment>` bean, `shopai.rag.policies-dir` config
- Produces: `DocumentIndexService.buildIndex()` → 加载 policies/ 目录 → LC4j `EmbeddingStoreIngestor` 一站式索引

- [ ] **Step 1: 创建 DocumentIndexService**

```java
package com.shopai.agent.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class DocumentIndexService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final Path policiesPath;

    public DocumentIndexService(
        EmbeddingModel embeddingModel,
        EmbeddingStore<TextSegment> embeddingStore,
        @Value("${shopai.rag.policies-dir:policies/}") String policiesDir
    ) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;

        // 解析相对于 resources 的路径
        String resourcePath = this.getClass().getClassLoader().getResource("").getPath();
        this.policiesPath = Paths.get(resourcePath, policiesDir).normalize();
    }

    public int buildIndex() {
        log.info("Building RAG index from: {}", policiesPath);

        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
            policiesPath, new TextDocumentParser()
        );

        if (documents.isEmpty()) {
            log.warn("No policy documents found in {}", policiesPath);
            return 0;
        }

        log.info("Loaded {} documents, clearing existing index...", documents.size());
        embeddingStore.removeAll();

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
            .documentSplitter(new DocumentByParagraphSplitter(1000, 0))
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build();

        ingestor.ingest(documents);

        log.info("Index rebuild complete: {} documents ingested", documents.size());
        return documents.size();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
# 预期: BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/rag/DocumentIndexService.java
git commit -m "feat: DocumentIndexService — LC4j EmbeddingStoreIngestor 索引构建

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: PolicyQueryTool + AgentConfig 集成

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/tool/PolicyQueryTool.java`
- Modify: `backend/src/main/java/com/shopai/agent/config/AgentConfig.java`

**Interfaces:**
- Consumes: `PolicyRagService`
- Produces: `PolicyQueryTool` bean (unused by other modules directly — registered in `AiServices.tools()`)

- [ ] **Step 1: 创建 PolicyQueryTool**

```java
package com.shopai.agent.tool;

import com.shopai.agent.rag.PolicyRagService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class PolicyQueryTool {

    private final PolicyRagService ragService;

    public PolicyQueryTool(PolicyRagService ragService) {
        this.ragService = ragService;
    }

    @Tool("当用户咨询退换货规则、保修政策、物流说明、售后流程等政策类问题时，" +
          "调用此工具查询公司售后政策知识库，获取最相关的政策条款原文。" +
          "根据返回的政策原文回答用户问题，并引用具体条款。")
    public String queryPolicy(
        @P("用户完整的问题描述，保留所有细节以便精准检索") String question
    ) {
        return ragService.query(question);
    }
}
```

- [ ] **Step 2: 修改 AgentConfig.shopAiAgent() 注册 PolicyQueryTool**

修改 `AgentConfig.java` 的 `shopAiAgent` 方法签名，注入 `PolicyQueryTool`:

```java
@Bean
public ShopAiAgent shopAiAgent(
    StreamingChatModel streamingModel,
    ChatMemoryProvider memoryProvider,
    ProductSearchTool productSearch,
    OrderQueryTool orderQuery,
    CalculatorTool calculator,
    PolicyQueryTool policyQuery) {
    return AiServices.builder(ShopAiAgent.class)
        .streamingChatModel(streamingModel)
        .chatMemoryProvider(memoryProvider)
        .tools(productSearch, orderQuery, calculator, policyQuery)
        .build();
}
```

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile -q
# 预期: BUILD SUCCESS
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/tool/PolicyQueryTool.java backend/src/main/java/com/shopai/agent/config/AgentConfig.java
git commit -m "feat: PolicyQueryTool — @Tool 供 Agent 查询售后政策知识库

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: PolicyController — 知识库管理 REST API

**Files:**
- Create: `backend/src/main/java/com/shopai/agent/web/PolicyController.java`

**Interfaces:**
- Consumes: `DocumentIndexService`
- Produces: REST endpoints (upload / list / delete / rebuild / preview chunks)

- [ ] **Step 1: 创建 PolicyController**

```java
package com.shopai.agent.web;

import com.shopai.agent.rag.DocumentIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/knowledge")
public class PolicyController {

    private static final Logger log = LoggerFactory.getLogger(PolicyController.class);

    private final DocumentIndexService indexService;
    private final Path policiesPath;

    public PolicyController(DocumentIndexService indexService) {
        this.indexService = indexService;
        String resourcePath = getClass().getClassLoader().getResource("").getPath();
        this.policiesPath = Paths.get(resourcePath, "policies").normalize();
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".md") && !filename.endsWith(".txt"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "仅支持 .md / .txt 文件"));
            }

            Files.createDirectories(policiesPath);
            Path dest = policiesPath.resolve(filename);
            file.transferTo(dest.toFile());

            log.info("Document uploaded: {}", filename);
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "filename", filename,
                "message", "上传成功，请重建索引以生效"
            ));
        } catch (IOException e) {
            log.error("Upload failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments() {
        List<Map<String, Object>> docs = new ArrayList<>();
        File[] files = policiesPath.toFile().listFiles((dir, name) -> name.endsWith(".md") || name.endsWith(".txt"));
        if (files != null) {
            for (File f : files) {
                docs.add(Map.of(
                    "id", f.getName(),
                    "name", f.getName(),
                    "size", f.length(),
                    "updatedAt", new Date(f.lastModified()).toString()
                ));
            }
        }
        return ResponseEntity.ok(docs);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String id) {
        try {
            Path file = policiesPath.resolve(id).normalize();
            if (!file.startsWith(policiesPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "非法路径"));
            }
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Document deleted: {}", id);
                return ResponseEntity.ok(Map.of("status", "ok", "message", "删除成功，请重建索引以生效"));
            }
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildIndex() {
        int count = indexService.buildIndex();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "documentCount", count,
            "message", "索引重建完成，共处理 " + count + " 篇文档"
        ));
    }

    @GetMapping("/chunks/{docId}")
    public ResponseEntity<Map<String, Object>> previewChunks(@PathVariable String docId) {
        // MVP 阶段返回简单的文件信息（Chroma 查询需要额外设计），后续可增强
        Path file = policiesPath.resolve(docId).normalize();
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "docId", docId,
            "message", "Chunk 预览功能将通过 Chroma 查询实现 (待增强)"
        ));
    }
}
```

注意：需要添加 `import java.io.File;` 到 import 区域。

- [ ] **Step 2: 编译验证**

```bash
cd backend && mvn compile -q
# 预期: BUILD SUCCESS
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/shopai/agent/web/PolicyController.java
git commit -m "feat: PolicyController — 知识库管理 REST API (upload/list/delete/rebuild)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: 示例政策文档

**Files:**
- Create: `backend/src/main/resources/policies/return-policy.md`
- Create: `backend/src/main/resources/policies/warranty.md`
- Create: `backend/src/main/resources/policies/shipping.md`

**Interfaces:**
- Consumes: (none)
- Produces: 3 篇 Markdown 政策文档，供 DocumentIndexService 启动时索引

- [ ] **Step 1: 创建退换货政策**

`backend/src/main/resources/policies/return-policy.md`:

```markdown
# ShopAI 退换货政策

## 无理由退货

消费者自收到商品之日起七日内可申请无理由退货，商品须保持完好、配件齐全、不影响二次销售。退回运费由买家承担，ShopAI 收到退货并核验后 3 个工作日内退款至原支付账户。

以下商品不支持无理由退货：已激活的手机、已拆封的软件、个人定制商品。

## 有理由退货

商品存在非人为质量问题，自签收之日起 15 日内可申请退货或换货。ShopAI 承担全部运费。质量问题包括但不限于：无法开机、屏幕显示异常、电池严重损耗、外观明显缺陷。

超过 15 日但仍在保修期内，仅支持维修，不支持退货换货。

## 退货流程

用户在"我的订单"页面选择对应订单，点击"申请退货"按钮，选择退货原因并上传商品照片。客服将在 24 小时内审核申请。审核通过后，用户需在 72 小时内寄出商品并填写物流单号。

## 退款时效

签收退货后 3 个工作日内发起退款。信用卡/借记卡退款到账时间为 3-15 个工作日，具体以发卡行处理时间为准。
```

- [ ] **Step 2: 创建保修政策**

`backend/src/main/resources/policies/warranty.md`:

```markdown
# ShopAI 商品保修政策

## 保修范围

所有 ShopAI 自营电子产品均享有一年免费保修服务，自签收之日起计算。保修涵盖因制造缺陷导致的硬件故障，包括但不限于：主板故障、电池非正常衰减、屏幕显示异常、按键失灵。

## 不保修范围

以下情况不在保修范围内：人为损坏（跌落、进水、挤压）、自行拆机或改装、使用非原装配件、自然灾害导致损坏、已过保修期。

## 保修流程

用户联系客服提交保修申请 → 客服在线诊断 → 寄修或到店维修 → 维修完成后寄回。保修期内维修免费，往返运费由 ShopAI 承担。

## 延长保修

用户可在购买后 30 日内购买延保服务（+1 年或 +2 年），延保服务不涵盖电池正常衰减。
```

- [ ] **Step 3: 创建物流说明**

`backend/src/main/resources/policies/shipping.md`:

```markdown
# ShopAI 物流与配送说明

## 配送时效

省会城市通常 1-2 个工作日送达，地级市 2-3 个工作日，县级及以下地区 3-5 个工作日。以上为工作日估算，节假日顺延。

## 运费政策

订单满 99 元包邮，不满 99 元收取 8 元运费。特殊大件商品（如 65 寸以上电视）可能收取额外运费，下单时页面将明确提示。

## 签收注意事项

签收前请检查包裹外观是否完好。如发现外包装明显破损、变形、进水痕迹，请拒收并联系客服。签收后如发现内在质量问题，按退换货政策处理。

## 物流跟踪

用户可通过"我的订单"页面查看实时物流信息。如物流信息超过 48 小时未更新，请联系客服查询。
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/policies/
git commit -m "feat: 添加 3 篇示例售后政策文档 (退换货/保修/物流)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: 前端 — API 客户端 + Zustand Store

**Files:**
- Create: `frontend/src/api/knowledge.ts`
- Create: `frontend/src/store/knowledgeStore.ts`

- [ ] **Step 1: 创建 knowledge.ts API 客户端**

```typescript
const BASE = '/api/knowledge';

export interface KnowledgeDocument {
  id: string;
  name: string;
  size: number;
  updatedAt: string;
}

export async function uploadDocument(file: File): Promise<{ status: string; filename: string; message: string }> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${BASE}/upload`, { method: 'POST', body: formData });
  if (!res.ok) throw new Error(`Upload failed: ${res.status}`);
  return res.json();
}

export async function fetchDocuments(): Promise<KnowledgeDocument[]> {
  const res = await fetch(`${BASE}/documents`);
  if (!res.ok) throw new Error(`Fetch documents failed: ${res.status}`);
  return res.json();
}

export async function deleteDocument(id: string): Promise<{ status: string; message: string }> {
  const res = await fetch(`${BASE}/documents/${encodeURIComponent(id)}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete failed: ${res.status}`);
  return res.json();
}

export async function rebuildIndex(): Promise<{ status: string; documentCount: number; message: string }> {
  const res = await fetch(`${BASE}/rebuild`, { method: 'POST' });
  if (!res.ok) throw new Error(`Rebuild failed: ${res.status}`);
  return res.json();
}
```

- [ ] **Step 2: 创建 knowledgeStore.ts**

```typescript
import { create } from 'zustand';
import type { KnowledgeDocument } from '../api/knowledge';
import { fetchDocuments, uploadDocument, deleteDocument, rebuildIndex } from '../api/knowledge';

interface KnowledgeState {
  documents: KnowledgeDocument[];
  loading: boolean;
  message: string | null;

  loadDocuments: () => Promise<void>;
  upload: (file: File) => Promise<void>;
  remove: (id: string) => Promise<void>;
  rebuild: () => Promise<void>;
  clearMessage: () => void;
}

export const useKnowledgeStore = create<KnowledgeState>((set) => ({
  documents: [],
  loading: false,
  message: null,

  loadDocuments: async () => {
    set({ loading: true });
    try {
      const docs = await fetchDocuments();
      set({ documents: docs, loading: false });
    } catch {
      set({ loading: false, message: '加载文档列表失败' });
    }
  },

  upload: async (file: File) => {
    set({ loading: true, message: null });
    try {
      const result = await uploadDocument(file);
      set({ message: result.message, loading: false });
    } catch {
      set({ loading: false, message: '上传失败' });
    }
  },

  remove: async (id: string) => {
    set({ loading: true, message: null });
    try {
      const result = await deleteDocument(id);
      set({ message: result.message, loading: false });
    } catch {
      set({ loading: false, message: '删除失败' });
    }
  },

  rebuild: async () => {
    set({ loading: true, message: null });
    try {
      const result = await rebuildIndex();
      set({ message: result.message, loading: false });
    } catch {
      set({ loading: false, message: '索引重建失败' });
    }
  },

  clearMessage: () => set({ message: null }),
}));
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/knowledge.ts frontend/src/store/knowledgeStore.ts
git commit -m "feat: 前端知识库 API 客户端 + Zustand store

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: 前端 — KnowledgeManager 页面 + 路由

**Files:**
- Create: `frontend/src/components/KnowledgeManager.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: 创建 KnowledgeManager 组件**

`frontend/src/components/KnowledgeManager.tsx`:

```tsx
import { useEffect, useRef, useState } from 'react';
import { useKnowledgeStore } from '../store/knowledgeStore';

export default function KnowledgeManager() {
  const { documents, loading, message, loadDocuments, upload, remove, rebuild, clearMessage } = useKnowledgeStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  useEffect(() => { loadDocuments(); }, [loadDocuments]);

  useEffect(() => {
    if (message) {
      const timer = setTimeout(clearMessage, 5000);
      return () => clearTimeout(timer);
    }
  }, [message, clearMessage]);

  const handleUpload = async () => {
    const file = fileInputRef.current?.files?.[0];
    if (!file) return;
    setUploading(true);
    await upload(file);
    setUploading(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
    await loadDocuments();
  };

  const handleDelete = async (id: string) => {
    if (!confirm(`确定删除 "${id}"?`)) return;
    await remove(id);
    await loadDocuments();
  };

  const handleRebuild = async () => {
    if (!confirm('确定重建索引? 这将清空所有现有索引并重新构建。')) return;
    await rebuild();
    await loadDocuments();
  };

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* 顶部栏 */}
      <div className="bg-white border-b px-6 py-4">
        <h1 className="text-xl font-bold text-gray-800">知识库管理</h1>
        <p className="text-sm text-gray-500 mt-1">
          管理售后政策文档，上传后需重建索引才能生效
        </p>
      </div>

      {/* 消息提示 */}
      {message && (
        <div className="mx-6 mt-4 px-4 py-3 bg-blue-50 border border-blue-200 rounded-lg text-blue-700 text-sm">
          {message}
        </div>
      )}

      {/* 上传区 */}
      <div className="mx-6 mt-4 p-4 bg-white rounded-lg border">
        <div className="flex items-center gap-3">
          <span className="text-gray-600 text-sm font-medium">📎 上传文档</span>
          <input
            ref={fileInputRef}
            type="file"
            accept=".md,.txt"
            className="text-sm text-gray-500 file:mr-3 file:py-1.5 file:px-3 file:border-0 file:text-sm file:font-medium file:bg-gray-100 file:text-gray-700 hover:file:bg-gray-200 file:rounded"
          />
          <button
            onClick={handleUpload}
            disabled={uploading}
            className="px-4 py-1.5 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {uploading ? '上传中...' : '上传'}
          </button>
        </div>
        <p className="text-xs text-gray-400 mt-2">支持 .md / .txt 格式</p>
      </div>

      {/* 文档列表 */}
      <div className="mx-6 mt-4 p-4 bg-white rounded-lg border flex-1 overflow-auto">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-semibold text-gray-700">
            已上传文档 ({documents.length} 篇)
          </h2>
          <button
            onClick={handleRebuild}
            disabled={loading}
            className="px-3 py-1.5 text-sm bg-amber-500 text-white rounded hover:bg-amber-600 disabled:opacity-50"
          >
            🔃 {loading ? '处理中...' : '重建索引'}
          </button>
        </div>

        {documents.length === 0 ? (
          <p className="text-gray-400 text-sm text-center py-8">暂无文档，请上传 .md 政策文件</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="pb-2 font-medium">文档名</th>
                <th className="pb-2 font-medium">大小</th>
                <th className="pb-2 font-medium">更新时间</th>
                <th className="pb-2 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => (
                <tr key={doc.id} className="border-b last:border-0 hover:bg-gray-50">
                  <td className="py-2.5 text-gray-800 font-medium">{doc.name}</td>
                  <td className="py-2.5 text-gray-500">{(doc.size / 1024).toFixed(1)} KB</td>
                  <td className="py-2.5 text-gray-500">{doc.updatedAt}</td>
                  <td className="py-2.5">
                    <button
                      onClick={() => handleDelete(doc.id)}
                      className="text-red-500 hover:text-red-700 text-sm"
                    >
                      🗑 删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 修改 App.tsx 加入路由**

修改 `frontend/src/App.tsx`:

```tsx
import { useState } from 'react';
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';
import KnowledgeManager from './components/KnowledgeManager';

type Page = 'chat' | 'knowledge';

export default function App() {
  const [page, setPage] = useState<Page>('chat');

  return (
    <div className="h-screen flex">
      {page === 'chat' ? (
        <>
          <Sidebar onNavigateKnowledge={() => setPage('knowledge')} />
          <ChatArea />
        </>
      ) : (
        <>
          <div className="w-16 bg-gray-900 flex flex-col items-center pt-4 gap-2">
            <button
              onClick={() => setPage('chat')}
              className="text-white text-xs p-2 hover:bg-gray-700 rounded w-12 text-center"
              title="返回聊天"
            >
              💬
            </button>
          </div>
          <KnowledgeManager />
        </>
      )}
    </div>
  );
}
```

注意：需要同步修改 `Sidebar.tsx`，添加 `onNavigateKnowledge` prop（或在 Sidebar 中直接使用 state 提升方案）。MVP 简化方案：在 App.tsx 中用一个简单的顶部导航切换。

- [ ] **Step 3: 简化方案 — 用条件渲染替代路由**

不引入 react-router，仅用 React state 管理页面切换。在 Sidebar 底部添加知识库入口按钮。这需要修改 Sidebar 接收 `onNavigateKnowledge` prop。

修改 `frontend/src/components/Sidebar.tsx`，在 props 添加:

```tsx
interface Props {
  onNavigateKnowledge?: () => void;
}

export default function Sidebar({ onNavigateKnowledge }: Props) {
```

在 Sidebar 底部（会话列表之后）添加一个导航按钮:

```tsx
{onNavigateKnowledge && (
  <button
    onClick={onNavigateKnowledge}
    className="w-full mt-auto px-3 py-2 text-sm text-gray-300 hover:text-white hover:bg-gray-700 rounded transition-colors"
  >
    📚 知识库管理
  </button>
)}
```

- [ ] **Step 4: 编译前端验证**

```bash
cd frontend && npm run build
# 预期: 无 TS 错误, 构建成功
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/KnowledgeManager.tsx frontend/src/App.tsx frontend/src/components/Sidebar.tsx
git commit -m "feat: KnowledgeManager 页面 + 路由 — 知识库管理前端

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: 文档更新 + 端到端验证

**Files:**
- Modify: `README.md`
- Modify: `docs/ONBOARDING.md`

- [ ] **Step 1: 更新 README.md**

在 "技术栈" 表格增加：

```markdown
| **向量数据库** | Chroma (Docker) |
| **Embedding 模型** | BAAI/bge-small-zh-v1.5 (Python sidecar) |
| **RAG 框架** | LangChain4j RAG API + ChromaEmbeddingStore |
```

在 "API 端点" 增加知识库管理 API 表格。在 "快速启动" 增加 Docker + Python 依赖步骤：

```markdown
### 前置依赖 (新增)

1. Docker Desktop — 拉取 Chroma: `docker pull chromadb/chroma`
2. Python 3.9+ — `pip install -r requirements-sidecar.txt`
3. 模型文件 `BAAI/bge-small-zh-v1.5` 存放于 `D:\models\huggingface`
```

- [ ] **Step 2: 更新 ONBOARDING.md**

在架构说明部分新增 `rag/` 包描述：

```markdown
### RAG 模块 (`com.shopai.agent.rag`)

| 类 | 职责 |
|----|------|
| `Text2VecEmbeddingModel` | 实现 LC4j `EmbeddingModel`，管理 Python sidecar |
| `PolicyRagService` | 检索编排: 向量化 → Chroma 查询 → 结果格式化 |
| `DocumentIndexService` | 使用 `EmbeddingStoreIngestor` 构建索引 |
| `ResultFormatter` | 将 `EmbeddingMatch` 格式化为 LLM 可读文本 |
```

- [ ] **Step 3: 端到端验证**

启动顺序：

```bash
# 1. 启动 Chroma
docker run -d -p 8000:8000 chromadb/chroma

# 2. 启动后端 (自动拉起 Python sidecar + 构建索引)
cd backend && mvn spring-boot:run

# 3. 启动前端
cd frontend && npm run dev
```

验证清单：
- [ ] `GET http://localhost:9876/health` → sidecar 正常
- [ ] `GET http://localhost:8000/api/v1/heartbeat` → Chroma 正常
- [ ] `POST http://localhost:8080/api/knowledge/rebuild` → 返回 `documentCount: 3`
- [ ] 前端聊天界面输入 "退货政策是什么？" → Agent 调用 `PolicyQueryTool`，返回引用原文的回答
- [ ] 前端 `/admin/knowledge` 页面 → 显示 3 篇文档，可上传/删除/重建索引

- [ ] **Step 4: Commit**

```bash
git add README.md docs/ONBOARDING.md
git commit -m "docs: 更新 README + ONBOARDING — RAG 模块说明 + 启动步骤

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 任务依赖图

```
Task 1 (Sidecar) ─────┐
                       ├──▶ Task 3 (EmbeddingModel) ──┐
Task 2 (Config) ──────┤                               ├──▶ Task 5 (RagService) ──▶ Task 7 (Tool) ──┐
                       ├──▶ Task 4 (Chroma Bean) ─────┘                               │              │
                       │                                                               │              │
                       └──▶ Task 6 (IndexService) ─────────────────────────────────────┤              │
                                                                                        │              │
                       Task 9 (Policy Docs) ────────────────────────────────────────────┘              │
                                                                                                       │
                       Task 8 (PolicyController) ──────────────────────────────────────────────────────┤
                                                                                                       │
                       Task 10 (Frontend API+Store) ──▶ Task 11 (Frontend UI) ─────────────────────────┤
                                                                                                       │
                       Task 12 (Docs + E2E) ───────────────────────────────────────────────────────────┘
```

并行可做：Task 1 + Task 2 + Task 9 同时起步；Task 3 + Task 4 并行；Task 10 + Task 8 并行。
