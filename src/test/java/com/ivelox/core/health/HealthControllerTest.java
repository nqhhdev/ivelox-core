package com.ivelox.core.health;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

@SpringBootTest
@ActiveProfiles("test")
class HealthControllerTest {

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
                "owner",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
        );
    }

    @Test
    void mealsCrudAndTodaySummary() throws Exception {
        String body = """
                {
                  "raw_input": "pho",
                  "quantity": 1,
                  "unit": "serving",
                  "kcal": 450,
                  "protein_g": 20,
                  "carb_g": 50,
                  "fat_g": 10,
                  "meal_type": "lunch",
                  "logged_at": "2026-08-23T01:00:00Z"
                }
                """;

        String created = mockMvc.perform(post("/api/v1/health/meals")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kcal").value(450))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = created.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        // 2026-08-23T01:00Z = 2026-08-23 08:00 ICT → civil day 2026-08-23
        mockMvc.perform(get("/api/v1/health/meals")
                        .param("date", "2026-08-23")
                        .with(authentication(ownerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(get("/api/v1/health/check/today")
                        .param("date", "2026-08-23")
                        .with(authentication(ownerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eaten_kcal").value(450))
                .andExpect(jsonPath("$.meal_count").value(1));

        mockMvc.perform(delete("/api/v1/health/meals/" + id)
                        .with(authentication(ownerAuth())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/health/check/today")
                        .param("date", "2026-08-23")
                        .with(authentication(ownerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meal_count").value(0));
    }

    @Test
    void resolveWithoutGeminiReturns503() throws Exception {
        mockMvc.perform(post("/api/v1/health/foods/resolve")
                        .with(authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"pho bo\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void unauthenticatedMealsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/health/meals").param("date", "2026-08-23"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void livenessStillPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
