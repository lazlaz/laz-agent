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
