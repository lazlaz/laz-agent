package com.shopai.agent.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) throws Exception {
        loadProducts();
        loadOrders();
    }

    private void loadProducts() throws Exception {
        var resource = new ClassPathResource("data/products.json");
        List<Map<String, Object>> products = mapper.readValue(
            resource.getInputStream(),
            new TypeReference<>() {}
        );
        for (var p : products) {
            jdbc.update(
                "MERGE INTO product (id, name, category, price, stock, specs, description) VALUES (?, ?, ?, ?, ?, ?, ?)",
                p.get("id"), p.get("name"), p.get("category"),
                Double.parseDouble(p.get("price").toString()),
                Integer.parseInt(p.get("stock").toString()),
                p.get("specs"), p.get("description")
            );
        }
        System.out.println("Loaded " + products.size() + " products");
    }

    private void loadOrders() throws Exception {
        var resource = new ClassPathResource("data/orders.json");
        List<Map<String, Object>> orders = mapper.readValue(
            resource.getInputStream(),
            new TypeReference<>() {}
        );
        for (var o : orders) {
            jdbc.update(
                "MERGE INTO customer_order (id, order_no, customer_name, status, total_amount, items, logistics, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                o.get("id"), o.get("order_no"), o.get("customer_name"),
                o.get("status"),
                Double.parseDouble(o.get("total_amount").toString()),
                o.get("items"), o.get("logistics"),
                o.get("created_at") != null ? java.sql.Timestamp.valueOf(o.get("created_at").toString().replace("T", " ")) : null
            );
        }
        System.out.println("Loaded " + orders.size() + " orders");
    }
}
