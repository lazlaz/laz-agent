package com.shopai.agent.tool;

import com.shopai.agent.rag.PolicyRagService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PolicyQueryTool {

    private static final Logger log = LoggerFactory.getLogger(PolicyQueryTool.class);

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
        log.info("[PolicyQuery] 查询: {}", question);
        String result = ragService.query(question);
        log.info("[PolicyQuery] 结果: {} chars", result != null ? result.length() : 0);
        return result;
    }
}
