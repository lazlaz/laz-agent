package com.shopai.agent.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeds known test data into in-memory H2 for deterministic integration tests.
 * <p>
 * Mirrors the production {@code DataInitializer} but uses plain INSERT instead of MERGE
 * so tests always start from a clean, known state.
 */
public class TestDataFactory {

    private static final Logger log = LoggerFactory.getLogger(TestDataFactory.class);

    private TestDataFactory() {}

    /**
     * Seeds the standard 10 products + 5 orders into the in-memory H2.
     * Call in {@code @BeforeEach} of integration tests that need business data.
     */
    public static void seedAll(JdbcTemplate jdbc) {
        seedProducts(jdbc);
        seedOrders(jdbc);
        log.info("Test data seeded: 10 products, 5 orders");
    }

    public static void seedProducts(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM product");
        Object[][] products = {
            {"p-001", "iPhone 15 Pro Max", "phone", 9999.00, 120,
                "{\"screen\":\"6.7 inch\",\"chip\":\"A17 Pro\",\"camera\":\"48MP\"}",
                "Apple flagship phone, great for photography"},
            {"p-002", "iPhone 15", "phone", 6999.00, 200,
                "{\"screen\":\"6.1 inch\",\"chip\":\"A16\",\"camera\":\"48MP\"}",
                "Apple mainstream phone"},
            {"p-003", "华为Mate 60 Pro", "phone", 6999.00, 80,
                "{\"screen\":\"6.82 inch\",\"chip\":\"Kirin 9000S\",\"camera\":\"50MP\"}",
                "Huawei flagship, satellite communication"},
            {"p-004", "小米14 Pro", "phone", 4999.00, 150,
                "{\"screen\":\"6.73 inch\",\"chip\":\"Snapdragon 8 Gen 3\",\"camera\":\"50MP Leica\"}",
                "Xiaomi flagship, Leica optics"},
            {"p-005", "MacBook Pro 14", "computer", 14999.00, 50,
                "{\"screen\":\"14.2 inch\",\"chip\":\"M3 Pro\",\"ram\":\"18GB\"}",
                "Apple laptop for professionals"},
            {"p-006", "MacBook Air 15", "computer", 10499.00, 60,
                "{\"screen\":\"15.3 inch\",\"chip\":\"M3\",\"ram\":\"8GB\"}",
                "Thin and light Apple laptop"},
            {"p-007", "ThinkPad X1 Carbon", "computer", 9999.00, 40,
                "{\"screen\":\"14 inch\",\"chip\":\"Intel i7-1365U\",\"ram\":\"16GB\"}",
                "Lenovo business laptop"},
            {"p-008", "华为MateBook X Pro", "computer", 8999.00, 35,
                "{\"screen\":\"14.2 inch\",\"chip\":\"Intel i7-1360P\",\"ram\":\"16GB\"}",
                "Huawei premium laptop"},
            {"p-009", "AirPods Pro 2", "accessory", 1899.00, 300,
                "{\"type\":\"earbuds\",\"anc\":true}",
                "Apple noise-cancelling earbuds"},
            {"p-010", "Apple Watch Ultra 2", "accessory", 6499.00, 70,
                "{\"type\":\"watch\",\"screen\":\"49mm\",\"waterproof\":true}",
                "Apple premium sports watch"}
        };
        for (Object[] row : products) {
            jdbc.update(
                "INSERT INTO product (id, name, category, price, stock, specs, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                row[0], row[1], row[2], row[3], row[4], row[5], row[6]);
        }
    }

    public static void seedOrders(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM customer_order");
        Object[][] orders = {
            {"o-001", "20240611001", "张三", "shipped", 9999.00,
                "[{\"name\":\"iPhone 15 Pro Max\",\"qty\":1,\"price\":9999}]",
                "顺丰 SF1234567890"},
            {"o-002", "20240611002", "李四", "delivered", 6999.00,
                "[{\"name\":\"华为Mate 60 Pro\",\"qty\":1,\"price\":6999}]",
                "京东 JD9876543210"},
            {"o-003", "20240611003", "王五", "returning", 10499.00,
                "[{\"name\":\"MacBook Air 15\",\"qty\":1,\"price\":10499}]",
                "顺丰 SF1122334455"},
            {"o-004", "20240611004", "张三", "paid", 2398.00,
                "[{\"name\":\"AirPods Pro 2\",\"qty\":1,\"price\":1899},{\"name\":\"手机壳\",\"qty\":1,\"price\":499}]",
                ""},
            {"o-005", "20240611005", "赵六", "delivered", 14999.00,
                "[{\"name\":\"MacBook Pro 14\",\"qty\":1,\"price\":14999}]",
                "京东 JD5566778899"}
        };
        for (Object[] row : orders) {
            jdbc.update(
                "INSERT INTO customer_order (id, order_no, customer_name, status, total_amount, items, logistics) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                row[0], row[1], row[2], row[3], row[4], row[5], row[6]);
        }
    }

    /**
     * Clears all tables. Call in {@code @AfterEach} to keep tests isolated.
     */
    public static void clearAll(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM agent_message");
        jdbc.update("DELETE FROM conversation");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM customer_order");
    }
}
