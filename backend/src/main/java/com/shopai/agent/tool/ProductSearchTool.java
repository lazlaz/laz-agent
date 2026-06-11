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
