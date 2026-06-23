package com.shopai.agent.tool;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrderQueryTool {

    private static final Logger log = LoggerFactory.getLogger(OrderQueryTool.class);

    private final JdbcTemplate jdbc;

    public OrderQueryTool(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Tool("查询用户的订单状态和物流信息。通过订单号或客户姓名查询，至少提供一个参数")
    public String queryOrders(
        @P("订单号，如 20240611001，可选") String orderNo,
        @P("客户姓名，可选") String customerName) {

        log.info("[OrderQuery] 参数: orderNo={}, customerName={}", orderNo, customerName);

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
            return "请提供订单号或客户姓名";
        }

        if (orders.isEmpty()) {
            log.info("[OrderQuery] 结果: 无匹配订单");
            return "未找到相关订单";
        }

        log.info("[OrderQuery] 结果: {} 条订单", orders.size());
        StringBuilder sb = new StringBuilder();
        for (var o : orders) {
            sb.append(String.format(
                "订单号: %s | 客户: %s | 状态: %s | 金额: ¥%.2f | 物流: %s | 时间: %s\n",
                o.get("ORDER_NO"), o.get("CUSTOMER_NAME"), o.get("STATUS"),
                o.get("TOTAL_AMOUNT"), o.get("LOGISTICS"), o.get("CREATED_AT")
            ));
        }
        return sb.toString().trim();
    }
}
