package com.shopai.agent.web;

import com.shopai.agent.domain.*;
import com.shopai.agent.engine.AgentEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired private MockMvc mvc;

    @MockBean private AgentEngine engine;

    @Test
    void shouldAcceptChatMessage() throws Exception {
        when(engine.execute(any())).thenReturn(
            new AgentResponse("Hello!", List.of(), TokenUsage.ZERO, 100)
        );

        mvc.perform(post("/api/chat/send")
                .contentType("application/json")
                .content("{\"message\":\"hi\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messageId").exists())
            .andExpect(jsonPath("$.streamUrl").exists());
    }
}
