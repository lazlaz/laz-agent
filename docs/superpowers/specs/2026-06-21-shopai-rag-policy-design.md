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
| Embedding 模型 | text2vec-large-chinese | 本地 CPU 可跑、免费、中文效果好 |
| 模型路径 | `D:\models\huggingface` | 用户指定目录 |
| Java 调用方式 | Python HTTP 侧服务, Spring Boot 启动时拉起 | encoding sidecar 模式,架构清晰 |
| 文档格式 | Markdown (`.md`) | 结构好解析、便于维护 |
| Chunking 策略 | 按 `##` 分节 + 节内按段落切 | 保留章节路径,语义完整 |
| Agent 集成方式 | 作为 Tool (`@Tool("queryPolicy")`) | 与现有 3 个 Tool 模式一致 |
| Chroma 交互 | 原生 REST API + RestTemplate | 无额外 SDK 依赖 |

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
│  ┌─────▼──────┐  ┌──────▼────────┐                  │
│  │ReActAgent  │  │PolicyRag      │                   │
│  │Engine(已有)│  │Service(新增)   │                   │
│  └─────┬──────┘  └──────┬────────┘                  │
│        │                │                            │
│        │         ┌──────▼────────┐                   │
│        │         │DocumentParser │ (新增)             │
│        │         │ - Markdown解析 │                  │
│        │         │ - 按##分节切分 │                  │
│        │         │ - 元数据挂载   │                  │
│        │         └──────┬────────┘                   │
│        │                │                            │
│        │         ┌──────▼────────┐                   │
│        │         │EmbeddingService│ (新增)            │
│        │         │ - 管理Python子进程                 │
│        │         │ - HTTP调用sidecar                  │
│        │         │ - encode(text) → float[1024]      │
│        │         └──────┬────────┘                   │
│        │                │                            │
│  ┌─────▼────────┐  ┌───▼────────┐                   │
│  │ 已有3个Tool   │  │  Chroma    │                   │
│  │+PolicyQuery  │  │  (Docker)  │                   │
│  │ Tool(新增)   │  │  :8000     │                   │
│  └──────────────┘  └────────────┘                   │
│                                                      │
│  ┌────────────────────────────┐                      │
│  │ Embedding Sidecar (Python) │                      │
│  │ text2vec-large-chinese     │                      │
│  │ localhost:9876             │                      │
│  └────────────────────────────┘                      │
└─────────────────────────────────────────────────────┘
```

## 5. 新增模块详细设计

### 5.1 DocumentParser — 文档解析与切分

**职责**：将 Markdown 政策文档解析为结构化 Chunk 列表

**包路径**：`com.shopai.agent.rag`

**输入**：`String filePath`（Markdown 文件路径）  
**输出**：`List<Chunk>`

**Chunk 数据结构**：
```java
public record Chunk(
    String content,      // chunk 纯文本
    String sectionPath,  // 章节路径，如 "退换货政策 > 无理由退货"
    String docName,      // 源文档文件名
    int chunkIndex       // 在文档内的序号 (从0开始)
) {}
```

**切分规则**：
1. 按 `##` 标题分节，sectionPath 累积父子关系（从文档标题到当前 `##`）
2. 节内以空行为界切段落
3. 超长段落（>1000 字）按中文句号 `。` 二次切分
4. 末尾 chunk 若 < 50 字，合并到上一个 chunk
5. 空白 chunk 丢弃

**伪代码流程**：
```
readMarkdown(file)
  → 提取 # 标题作为文档名 (docName)
  → 按 ## 分割 section
  → 每个 section 内按 \n\n 分割段落
  → 包装为 Chunk(sectionPath=累积的标题路径)
  → 末尾碎片检查 & 合并
  → return List<Chunk>
```

### 5.2 EmbeddingService — 向量化服务

**职责**：管理 Python embedding sidecar，提供文本向量化接口

**包路径**：`com.shopai.agent.rag`

**Sidecar 部署**：
- Python 微服务（Flask），`sentence-transformers` 加载 `text2vec-large-chinese`
- 监听 `localhost:9xxx`（端口通过配置指定，默认 9876）
- 端点：`POST /encode` 接收 `{"texts": [...]}`，返回 `{"embeddings": [[...], ...]}`

**Spring Boot 侧管理**：
```java
@Component
public class EmbeddingService implements DisposableBean {

    private Process pythonProcess;

    @PostConstruct
    public void startSidecar() {
        // ProcessBuilder 启动 Python 脚本
        // 等待 /health 就绪，超时 30s
    }

    public float[] encode(String text) { ... }
    public float[][] encode(List<String> texts) { ... }

    @Override
    public void destroy() {
        // 关闭子进程
    }
}
```

**故障处理**：
- 启动超时 → 抛异常阻止应用启动，日志明确指引
- 运行时调用失败 → 重试 1 次，依然失败则抛 `EmbeddingException`
- Sidecar 意外退出 → 记录错误日志，后续 `encode` 调用直接失败（MVP 不做自动重启）

### 5.3 ChromaHttpClient — 向量存储交互

**职责**：封装 Chroma REST API 调用

**包路径**：`com.shopai.agent.rag`

**Chroma 环境**：
- Docker 部署：`chromadb/chroma`
- 端口：`localhost:8000`
- Collection 名称：`shopai_policies`
- 向量维度：1024（text2vec-large-chinese 输出维度）

**核心方法**：
```java
public class ChromaHttpClient {

    // 批量写入 chunks + embeddings
    public void add(List<ChunkEmbedding> items) { ... }

    // 检索 topK 个最相似 chunk
    public List<SearchResult> query(float[] embedding, int topK) { ... }

    // 删除 collection（重建索引前清空）
    public void deleteCollection() { ... }

    // 获取已索引数量
    public int collectionSize() { ... }
}

record ChunkEmbedding(
    String docName,
    String sectionPath,
    int chunkIndex,
    String content,
    float[] embedding
) {}

record SearchResult(
    String content,
    String docName,
    String sectionPath,
    int chunkIndex,
    double score       // 相似度分数
) {}
```

**Chroma REST API 映射**：
- `POST /api/v1/collections/{name}/add` → 写入
- `POST /api/v1/collections/{name}/query` → 检索
- `DELETE /api/v1/collections/{name}` → 删除
- `GET /api/v1/collections/{name}` → 元信息

### 5.4 PolicyRagService — 检索编排

**职责**：编排查询流程（embedding → chroma 检索 → 结果格式化）

**包路径**：`com.shopai.agent.rag`

```java
@Service
public class PolicyRagService {

    private final EmbeddingService embeddingService;
    private final ChromaHttpClient chromaClient;
    private final int topK;  // 从配置读取，默认 3

    /**
     * 检索售后政策知识库
     * @param question 用户原始问题
     * @return 已格式化的检索结果，包含原文引用和章节路径
     */
    public String query(String question) {
        float[] embedding = embeddingService.encode(question);
        List<SearchResult> results = chromaClient.query(embedding, topK);
        return ResultFormatter.format(results);
    }
}
```

**ResultFormatter 输出格式**（注入给 LLM 的文本）：

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

### 5.7 DocumentIndexService — 索引构建服务

**职责**：组合 Parser + Embedding + Chroma，完成索引构建

**包路径**：`com.shopai.agent.rag`

```java
@Service
public class DocumentIndexService {

    private final DocumentParser parser;
    private final EmbeddingService embeddingService;
    private final ChromaHttpClient chromaClient;

    /**
     * 全量构建索引：清空 Chroma，遍历 policies/ 目录，解析 + 向量化 + 写入
     */
    public int buildIndex() {
        chromaClient.deleteCollection();
        List<File> files = scanPolicyDir();
        int totalChunks = 0;
        for (File file : files) {
            List<Chunk> chunks = parser.parse(file);
            List<String> texts = chunks.stream().map(Chunk::content).toList();
            float[][] embeddings = embeddingService.encode(texts);
            // 组装 ChunkEmbedding，批量写入 Chroma
            chromaClient.add(assemble(chunks, embeddings));
            totalChunks += chunks.size();
        }
        return totalChunks;
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
      model-name: text2vec-large-chinese
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
- `application-local.yml` 中无需新增密钥（text2vec 本地运行、Chroma 无认证）
- Docker 需预先安装并拉取 `chromadb/chroma` 镜像
- Python 环境需安装 `sentence-transformers` 包
- 模型文件需在 `D:\models\huggingface` 目录下已下载

## 8. 文档更新清单

实施时需同步更新以下文档：

| 文档 | 更新内容 |
|------|---------|
| `README.md` | 技术栈增加 Chroma + text2vec；API 增加知识库管理端点；快速启动增加 Docker/Python 依赖 |
| `docs/ONBOARDING.md` | 新增 RAG 模块架构说明；新增 `rag/` 包介绍；新增启动步骤 |
| `backend/pom.xml` | 无需新增依赖（使用 RestTemplate + ProcessBuilder 均为 JDK 内置） |

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
