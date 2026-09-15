package com.ivelox.core.modules.order;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private static UsernamePasswordAuthenticationToken ownerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    }

    @Test
    void createAndGetOrder() throws Exception {
        String body = """
                {"customerName":"Alice","totalAmount":99.90}
                """;

        String created = mockMvc.perform(post("/api/v1/orders")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customerName").value("Alice"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andReturn().getResponse().getContentAsString();

        String id = created.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/orders/" + id)
                        .with(authentication(ownerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.customerName").value("Alice"));
    }

    @Test
    void getUnknownOrderReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + java.util.UUID.randomUUID())
                        .with(authentication(ownerAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("order not found"));
    }

    @Test
    void createOrderWithInvalidAmountReturns400() throws Exception {
        String body = """
                {"customerName":"Bob","totalAmount":-5}
                """;
        mockMvc.perform(post("/api/v1/orders")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
