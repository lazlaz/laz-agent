package com.shopai.agent.verify;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TestOpenAiError {

    // 替换成您的 API Key 和端点
    private static final String API_KEY = System.getenv("DEEPSEEK_API_KEY");
    private static final String API_URL = "https://api.deepseek.com/chat/completions"; // 或您的代理地址

    public static void main(String[] args) throws Exception {
        // 错误的消息 JSON（与之前构造的一致）
        String errorJson = """
                {
                  "model": "deepseek-chat",
                  "messages": [
                    {
                      "role": "system",
                      "content": "You are a customer service AI for ShopAI, an electronics e-commerce platform.\\nYour name is ShopAI Assistant.\\n\\nGuidelines:\\n- Always respond in Chinese\\n- Use available tools to look up product information and order status before answering\\n- When a user asks about products, search for them using the product search tool\\n- When a user asks about orders, query using their order number or name\\n- For calculations, use the calculator tool\\n- Provide complete, helpful answers — never leave the user hanging\\n- If you don't have enough information, ask clarifying questions\\n- Be friendly and professional\\n"
                    },
                    {
                      "role": "user",
                      "content": "张三订单"
                    },
                    {
                      "role": "assistant",
                      "content": "让我查一下张三的订单信息。"
                    },
                    {
                      "role": "tool",
                      "tool_call_id": "call_00_hjQJsSFqdgtfOhuwmIee1306",
                      "content": "订单号: 20240611001 | 客户: 张三 | 状态: shipped | 金额: ¥9999.00 | 物流: 顺丰 SF1234567890 | 时间: 2024-06-11 10:30:00.0\\n订单号: 20240610002 | 客户: 张三 | 状态: delivered | 金额: ¥1899.00 | 物流: 圆通 YT9876543210 | 时间: 2024-06-10 14:20:00.0"
                    }
                  ],
                  "stream": true,
                  "stream_options": {
                    "include_usage": true
                  },
                  "tools": [
                    {
                      "type": "function",
                      "function": {
                        "name": "searchProducts",
                        "description": "搜索电子产品，支持关键词、分类、价格范围筛选。参数均为可选，至少提供一个即可",
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "keyword": { "type": "string", "description": "搜索关键词，匹配产品名和描述" },
                            "category": { "type": "string", "description": "分类: phone/computer/accessory，可选" },
                            "minPrice": { "type": "number", "description": "最低价格，可选" },
                            "maxPrice": { "type": "number", "description": "最高价格，可选" }
                          },
                          "required": ["keyword", "category", "minPrice", "maxPrice"]
                        }
                      }
                    },
                    {
                      "type": "function",
                      "function": {
                        "name": "queryOrders",
                        "description": "查询用户的订单状态和物流信息。通过订单号或客户姓名查询，至少提供一个参数",
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "orderNo": { "type": "string", "description": "订单号，如 20240611001，可选" },
                            "customerName": { "type": "string", "description": "客户姓名，可选" }
                          },
                          "required": ["orderNo", "customerName"]
                        }
                      }
                    },
                    {
                      "type": "function",
                      "function": {
                        "name": "calculate",
                        "description": "执行数学计算，支持加减乘除和括号。例如: '2+3*4' 或 '100*0.9'",
                        "parameters": {
                          "type": "object",
                          "properties": {
                            "expression": { "type": "string", "description": "数学表达式，支持 + - * / 和括号" }
                          },
                          "required": ["expression"]
                        }
                      }
                    }
                  ]
                }
                """;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(errorJson))
                .build();

        // 因为 stream=true，这里使用 BodyHandlers.ofString() 会一次性接收所有流式数据（实际 SSE 应该逐行解析，但为了演示错误，直接获取响应体也够用）
        // 注意：部分服务端在流式模式下可能返回 SSE 格式，但错误时通常返回 JSON 错误，可直接读取。
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("状态码: " + response.statusCode());
        System.out.println("响应体: " + response.body());
    }
}
