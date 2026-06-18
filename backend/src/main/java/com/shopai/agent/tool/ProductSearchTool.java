package com.shopai.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
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

    @Tool("搜索电子产品，支持关键词、分类、价格范围筛选。参数均为可选，至少提供一个即可")
    public String searchProducts(
        @P("搜索关键词，匹配产品名和描述") String keyword,
        @P("分类: phone/computer/accessory，可选") String category,
        @P("最低价格，可选") Double minPrice,
        @P("最高价格，可选") Double maxPrice) {

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
            params.add(minPrice);
        }
        if (maxPrice != null) {
            sql.append(" AND price <= ?");
            params.add(maxPrice);
        }
        sql.append(" ORDER BY price ASC LIMIT 10");

        List<Map<String, Object>> results = jdbc.queryForList(
            sql.toString(), params.toArray()
        );

        if (results.isEmpty()) {
            return "未找到匹配的产品";
        }

        StringBuilder sb = new StringBuilder();
        for (var r : results) {
            sb.append(String.format(
                "%s | 分类: %s | 价格: ¥%.2f | 库存: %d | %s\n",
                r.get("NAME"), r.get("CATEGORY"), r.get("PRICE"),
                r.get("STOCK"), r.get("DESCRIPTION")
            ));
        }
        return sb.toString().trim();
    }
}
