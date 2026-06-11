package com.shopai.agent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Seeds the H2 database with mock product and order data on application startup.
 * <p>
 * Loads data from {@code data/products.json} and {@code data/orders.json} on the
 * classpath. Uses MERGE INTO for idempotent upserts so repeated runs are safe.
 * Runs early ({@code @Order(1)}) so downstream components see populated data.
 */
@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        loadProducts();
        loadOrders();
    }

    private void loadProducts() throws IOException {
        var resource = new ClassPathResource("data/products.json");
        List<ProductRecord> products;
        try (InputStream in = resource.getInputStream()) {
            products = mapper.readValue(in, new TypeReference<>() {});
        }

        int loaded = 0;
        for (var p : products) {
            if (p.id() == null || p.name() == null) {
                log.warn("Skipping product with missing id or name: {}", p);
                continue;
            }
            try {
                jdbc.update(
                    "MERGE INTO product (id, name, category, price, stock, specs, description) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    p.id(), p.name(), p.category(),
                    p.price() != null ? p.price() : BigDecimal.ZERO,
                    p.stock() != null ? p.stock() : 0,
                    p.specs(), p.description()
                );
                loaded++;
            } catch (Exception e) {
                log.warn("Failed to load product {}: {}", p.id(), e.getMessage());
            }
        }
        log.info("Loaded {}/{} products", loaded, products.size());
    }

    private void loadOrders() throws IOException {
        var resource = new ClassPathResource("data/orders.json");
        List<OrderRecord> orders;
        try (InputStream in = resource.getInputStream()) {
            orders = mapper.readValue(in, new TypeReference<>() {});
        }

        int loaded = 0;
        for (var o : orders) {
            if (o.id() == null || o.order_no() == null) {
                log.warn("Skipping order with missing id or order_no: {}", o);
                continue;
            }
            try {
                java.sql.Timestamp createdAt = null;
                if (o.created_at() != null && !o.created_at().isBlank()) {
                    createdAt = java.sql.Timestamp.valueOf(
                        LocalDateTime.parse(o.created_at(), DT_FMT)
                    );
                }
                jdbc.update(
                    "MERGE INTO customer_order (id, order_no, customer_name, status, total_amount, items, logistics, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    o.id(), o.order_no(), o.customer_name(),
                    o.status(),
                    o.total_amount() != null ? o.total_amount() : BigDecimal.ZERO,
                    o.items(), o.logistics(), createdAt
                );
                loaded++;
            } catch (Exception e) {
                log.warn("Failed to load order {}: {}", o.order_no(), e.getMessage());
            }
        }
        log.info("Loaded {}/{} orders", loaded, orders.size());
    }

    // --- JSON record types for type-safe deserialization ---

    private record ProductRecord(
        String id,
        String name,
        String category,
        BigDecimal price,
        Integer stock,
        String specs,
        String description
    ) {}

    private record OrderRecord(
        String id,
        String order_no,
        String customer_name,
        String status,
        BigDecimal total_amount,
        String items,
        String logistics,
        String created_at
    ) {}
}
