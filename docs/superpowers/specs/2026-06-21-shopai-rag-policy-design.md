# ShopAI Agent — RAG 售后政策知识库 设计文档

**日期**：2026-06-21  
**状态**：设计完成  
**父项目**：ShopAI Agent Platform Phase 2a

---

## 1. 目标

为 ShopAI Agent 平台新增 RAG（Retrieval-Augmented Generation）能力，以**售后政策知识库**为 MVP 场景，让 Agent 能根据用户问题自动检索相关政策条款并引用原文回答。

## 2. 业务场景

- 用户问："我用了三天不喜欢，能退吗？"
- Agent 调用 `PolicyQueryTool`，检索到"七日无理由退货"政策原文
- Agent 基于原文回答，引用具体条款出处

## 3. 技术选型

| 决策项 | 选型 | 理由 |
|--------|------|------|
| 向量数据库 | Chroma (Docker) | 轻量、REST API 直接交互、面试主流 |
| Embedding 模型 | BAAI/bge-small-zh-v1.5 | 本地 CPU 可跑、免费、中文效果好 |
| 模型路径 | `D:\models\huggingface` | 用户指定目录 |
| Java 调用方式 | Python HTTP 侧服务, Spring Boot 启动时拉起 | encoding sidecar 模式,架构清晰 |
| 文档格式 | Markdown (`.md`) | 结构好解析、便于维护 |
| Chunking 策略 | 按 `##` 分节 + 节内按段落切 | 保留章节路径,语义完整 |
| Agent 集成方式 | 作为 Tool (`@Tool("queryPolicy")`) | 与现有 3 个 Tool 模式一致 |
| Chroma 交互 | LangChain4j `ChromaEmbeddingStore` | 官方集成，与现有 LangChain4j 体系一致 |
| 文档切分 | LangChain4j `DocumentByParagraphSplitter` | 内置 API，保持与 TextSegment 生态一致 |
| 文档加载 | LangChain4j `FileSystemDocumentLoader` | 统一 Document 抽象 |

## 4. 架构总览

```
┌─────────────────────────────────────────────────────┐
│  前端 (React + TS)                                    │
│  ┌──────────────┐  ┌──────────────────────────┐     │
│  │  聊天界面(已有) │  │  知识库管理页 (新增)        │     │
│  │              │  │  /admin/knowledge          │     │
│  │              │  │  上传/列表/删除/预览/重建    │     │
│  └──────┬───────┘  └──────────┬───────────────┘     │
└─────────┼────────────────────┼──────────────────────┘
          │ SSE                │ REST
          ▼                    ▼
┌─────────────────────────────────────────────────────┐
│  后端 (Spring Boot 3.3 + Java 21)                     │
│                                                      │
│  ┌────────────┐  ┌───────────────┐                   │
│  │ChatController│ │PolicyController│ (新增)           │
│  │  (已有)     │  │ 上传/管理文档   │                 │
│  └─────┬──────┘  └──────┬────────┘                  │
│        │                │                            │
│  ┌─────▼──────┐  ┌──────▼──────────────┐                  │
│  │ReActAgent  │  │PolicyRagService(新增) │                  │
│  │Engine(已有)│  │ - 检索编排            │                  │
│  └─────┬──────┘  │ - ResultFormatter    │                  │
│        │         └──────┬───────────────┘                  │
│        │                │                                   │
│        │         ┌──────▼───────────────┐                   │
│        │         │EmbeddingModel(新增)   │                   │
│        │         │ - 实现 LC4j 接口      │                   │
│        │         │ - 管理Python子进程     │                   │
│        │         │ - encode→float[512]  │                   │
│        │         └──────┬───────────────┘                   │
│        │                │                                   │
│  ┌─────▼────────┐  ┌───▼────────────────┐                   │
│  │ 已有3个Tool   │  │ ChromaEmbeddingStore│                  │
│  │+PolicyQuery  │  │ (LC4j 官方集成)     │                   │
│  │ Tool(新增)   │  │ → Chroma :8000     │                   │
│  └──────────────┘  └────────────────────┘                   │
│                                                      │
│  ┌────────────────────────────┐                      │
│  │ Embedding Sidecar (Python) │                      │
│  │ BAAI/bge-small-zh-v1.5     │                      │
│  │ localhost:9876             │                      │
│  └────────────────────────────┘                      │
└─────────────────────────────────────────────────────┘
```

## 5. 新增模块详细设计

### 5.1 文档加载与切分 — 使用 LangChain4j 内置 API

**使用 LangChain4j 标准文档处理链**，无需自定义 Parser：

```java
// 文档加载
Document document = FileSystemDocumentLoader.loadDocument(
    Paths.get("policies/return-policy.md"),
    new TextDocumentParser()
);

// 按段落切分（maxSegmentSize=1000字符, maxOverlap=0）
DocumentSplitter splitter = new DocumentByParagraphSplitter(1000, 0,
    new DocumentByParagraphSplitter.ParagraphChecker() {
        @Override
        public boolean isParagraphBoundary(String text, int index) {
            return text.charAt(index) == '\n' && index > 0 && text.charAt(index - 1) == '\n';
        }
    }
);

List<TextSegment> segments = splitter.split(document);
```

**元数据挂载**：通过 `TextSegmentTransformer` 为每个 segment 添加章节路径：

```java
.textSegmentTransformer(segment -> {
    String fileName = segment.metadata().getString(DOCUMENT_FILE_NAME);
    return TextSegment.from(
        "[" + fileName + "] " + segment.text(),
        segment.metadata()
    );
})
```

**关键点**：
- `DocumentByParagraphSplitter` 以空行为界切分，符合政策文档结构
- `FileSystemDocumentLoader` 支持单文件 + 批量加载
- `TextSegment` 自带 `metadata()` Map，无需自定义 record
- MVP 阶段不实现标题路径累积（Markdown `##` 解析），通过文件名区分来源即可

### 5.2 EmbeddingService — 实现 LangChain4j EmbeddingModel 接口

**职责**：管理 Python embedding sidecar，实现 `dev.langchain4j.model.embedding.EmbeddingModel` 接口

**包路径**：`com.shopai.agent.rag`

```java
@Component
public class Text2VecEmbeddingModel implements EmbeddingModel, DisposableBean {

    private Process pythonProcess;
    private String sidecarUrl;

    @Value("${shopai.rag.embedding.model-path}")
    private String modelPath;

    @Value("${shopai.rag.embedding.model-name}")
    private String modelName;

    @Value("${shopai.rag.embedding.sidecar-port:9876}")
    private int sidecarPort;

    @PostConstruct
    public void startSidecar() {
        // ProcessBuilder 启动 Python Flask 侧服务
        // 轮询 GET /health 直至就绪，超时 30s
    }

    @Override
    public Embedding embed(String text) {
        // POST /encode {"texts": [text]}
        // 返回 Embedding.from(floatArray)
        return embedAll(List.of(text)).content().getFirst();
    }

    @Override
    public Response<Embedding> embedAll(List<TextSegment> segments) {
        List<String> texts = segments.stream().map(TextSegment::text).toList();
        float[][] vectors = encode(texts);
        List<Embedding> embeddings = new ArrayList<>();
        for (float[] v : vectors) {
            embeddings.add(Embedding.from(v));
        }
        return Response.from(embeddings);
    }

    // 内部 HTTP 调用方法
    private float[][] encode(List<String> texts) {
        // RestTemplate POST → Python sidecar
    }

    @Override
    public void destroy() {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            pythonProcess.destroyForcibly();
        }
    }
}
```

**为什么实现 `EmbeddingModel`**：
- `EmbeddingStoreIngestor` 需要 `EmbeddingModel` 参数
- `EmbeddingStoreContentRetriever` 也需要 `EmbeddingModel`
- 实现标准接口后，可无缝接入 LangChain4j RAG 全家桶

### 5.3 ChromaEmbeddingStore — 使用 LangChain4j 官方集成

**无需自定义 HTTP Client**，直接使用 LangChain4j 官方 `ChromaEmbeddingStore`：

**Maven 依赖**（新增）：
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-chroma</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

**Bean 配置**：
```java
@Bean
public ChromaEmbeddingStore chromaEmbeddingStore(
    @Value("${shopai.rag.chroma.host}") String host,
    @Value("${shopai.rag.chroma.port}") int port,
    @Value("${shopai.rag.chroma.collection}") String collection) {

    return ChromaEmbeddingStore.builder()
        .baseUrl("http://" + host + ":" + port)
        .collectionName(collection)
        .build();
}
```

**核心类型说明**：
- `EmbeddingStore<TextSegment>` — 泛型接口，ChromaEmbeddingStore 实现
- `TextSegment` — LangChain4j 标准文本段（替代自定义 Chunk record）
- `EmbeddingMatch<TextSegment>` — 检索结果（替代自定义 SearchResult record）
- `Embedding` — 向量封装（替代 float[]）

### 5.4 PolicyRagService — 检索编排

**职责**：编排查询流程（embedding → chroma 检索 → 结果格式化）

**包路径**：`com.shopai.agent.rag`

```java
@Service
public class PolicyRagService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int topK;      // 从配置读取，默认 3
    private final double minScore; // 从配置读取，默认 0.5

    public String query(String question) {
        // 1. 向量化问题
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        // 2. 检索 (使用 LangChain4j 标准 API)
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(topK)
            .minScore(minScore)
            .build();
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        // 3. 格式化结果
        return ResultFormatter.format(result.matches());
    }
}
```

**ResultFormatter** — 将 `List<EmbeddingMatch<TextSegment>>` 格式化为 LLM 可读文本：

```
【相关政策条款】

1. [退换货政策 > 无理由退货]
   消费者自收到商品之日起七日内可申请无理由退货，
   商品须保持完好，不影响二次销售...

2. [退换货政策 > 退货流程 > 申请步骤]
   用户可在"我的订单"页面点击"申请退货"，
   填写退货原因并上传商品照片...

请根据以上政策条款回答用户的问题，并在回答中引用具体条款。
```

### 5.5 PolicyQueryTool — Agent Tool 集成

**职责**：`@Tool` 注解方法，让 Agent 决定何时查询政策知识库

**包路径**：`com.shopai.agent.tool`

```java
@Component
public class PolicyQueryTool {

    private final PolicyRagService ragService;

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

**集成点**：
- 在 `AgentConfig.agent()` 方法中通过 `AiServices.builder().tools()` 注册（和现有 3 个 Tool 同列表）
- 添加 `PolicyQueryTool` 后无需修改 Agent 接口和 Engine

### 5.6 PolicyController — 文档管理 API

**包路径**：`com.shopai.agent.web`

**端点**：

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/knowledge/upload` | 上传政策文档 (multipart/form-data, file 字段) |
| `GET` | `/api/knowledge/documents` | 列出所有已上传文档（文件名、chunk 数、上传时间） |
| `DELETE` | `/api/knowledge/documents/{id}` | 删除文档，并移除对应 Chroma 数据 |
| `POST` | `/api/knowledge/rebuild` | 清空 Chroma collection，从 `policies/` 目录全量重建索引 |
| `GET` | `/api/knowledge/chunks/{docId}` | 预览某文档的切片列表（管理员查看切分效果） |

**文档存储**：
- 原始文件存于 `backend/src/main/resources/policies/` 目录
- 启动时若 Chroma 为空，自动从该目录构建索引
- 上传文档写入该目录，然后触发增量索引

### 5.7 DocumentIndexService — 使用 EmbeddingStoreIngestor 构建索引

**职责**：组合 LC4j DocumentLoader + Splitter + EmbeddingModel + EmbeddingStore，完成索引构建

**包路径**：`com.shopai.agent.rag`

```java
@Service
public class DocumentIndexService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final String policiesDir; // 从配置读取

    public int buildIndex() {
        // 1. 批量加载 policies/ 目录下所有 .md 文件
        List<Document> documents = FileSystemDocumentLoader.loadDocuments(
            Paths.get(policiesDir), new TextDocumentParser()
        );

        // 2. 清空已有索引
        embeddingStore.removeAll();

        // 3. 使用 EmbeddingStoreIngestor 一站式处理
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
            .documentSplitter(new DocumentByParagraphSplitter(1000, 0))
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build();

        ingestor.ingest(documents);

        // 4. 返回总 segment 数（通过再次搜索估算或直接返回文档数）
        return documents.size();
    }
}
```

### 5.8 前端：知识库管理页

**路由**：`/admin/knowledge`（与聊天界面 `/` 并列）

**组件**：`KnowledgeManager.tsx`

**功能区域**：
1. **上传区**：文件选择器 + 上传按钮（支持 .md / .txt）
2. **文档列表**：表格展示（文档名、切片数、上传时间、操作按钮）
3. **操作**：预览切片（展开面板）、删除文档、重建索引按钮

**UI 线框**：
```
┌─────────────────────────────────────────────────┐
│  知识库管理                                       │
│                                                 │
│  📎 上传文档          [选择文件] [上传]           │
│  支持 .md / .txt                                │
│                                                 │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
│                                                 │
│  已索引文档 (5 篇)                  [🔃 重建索引]  │
│  ┌──────────┬────────┬────────────┬──────────┐  │
│  │ 文档名    │ 切片数  │ 上传时间    │ 操作     │  │
│  ├──────────┼────────┼────────────┼──────────┤  │
│  │ 退换货政策 │   8    │ 2026-06-21 │ 🗑 👁    │  │
│  │ 保修条款   │   5    │ 2026-06-21 │ 🗑 👁    │  │
│  │ 物流说明   │   3    │ 2026-06-21 │ 🗑 👁    │  │
│  └──────────┴────────┴────────────┴──────────┘  │
└─────────────────────────────────────────────────┘
```

**API 对接**：
- 新增 `frontend/src/api/knowledge.ts`（API 调用函数）
- Zustand store 可用独立 store（`knowledgeStore.ts`）或扩展现有 store（推荐独立 store）

## 6. 配置项

新增 `application.yml` 配置项：

```yaml
shopai:
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
    policies-dir: policies/   # 相对于 resources 目录
```

## 7. 启动依赖与顺序

```
1. Docker: chromadb/chroma          (后台常驻)
2. Python sidecar (Spring 拉起)     (应用启动时自动启动)
3. Spring Boot                      (等待 sidecar 就绪)
4. Chroma collection 初始化          (首次启动时构建索引)
```

**启动检查清单**：
- `application-local.yml` 中无需新增密钥（bge-small 本地运行、Chroma 无认证）
- Docker 需预先安装并拉取 `chromadb/chroma` 镜像
- Python 环境需安装 `sentence-transformers` 包
- 模型文件需在 `D:\models\huggingface` 目录下已下载

## 8. 文档更新清单

实施时需同步更新以下文档：

| 文档 | 更新内容 |
|------|---------|
| `README.md` | 技术栈增加 Chroma + bge-small；API 增加知识库管理端点；快速启动增加 Docker/Python 依赖 |
| `docs/ONBOARDING.md` | 新增 RAG 模块架构说明；新增 `rag/` 包介绍；新增启动步骤 |
| `backend/pom.xml` | 新增 `langchain4j-chroma` 依赖；移除 `mustache` 依赖（未使用） |

## 9. 质量目标

| 指标 | 目标值 | 验证方式 |
|------|--------|---------|
| 检索召回率 | top-3 命中率 > 80% | 10 个测试问题人工验证 |
| Embedding 延迟 | 单次 < 200ms | 日志计时 |
| 端到端延迟 | 问题到检索结果 < 500ms | SSE trace 查看 |
| Chunk 质量 | 无句子中间切断 | 管理页预览功能检查 |
| LLM 回答引用率 | 回答中引用政策原文 > 90% | 人工抽查 |

## 10. 风险与边界

**MVP 不做**：
- 增量索引（上传文档后自动增量，而非全量重建）
- Embedding sidecar 意外退出自动重启
- 混合检索（语义 + BM25 关键词）
- 检索效果量化评估
- 用户上传文件类型校验（先信任 .md/.txt）

**待 Phase 2b 迭代**：
- 混合检索 (Hybrid Search)
- 商品资料库扩展
- 检索质量看板
