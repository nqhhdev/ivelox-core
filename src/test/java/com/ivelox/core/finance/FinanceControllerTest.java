package com.ivelox.core.finance;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

class FinanceControllerTest {

    private static UsernamePasswordAuthenticationToken ownerAuth() {
        return new UsernamePasswordAuthenticationToken(
                "owner", null, List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @Import(FinanceTestSupport.class)
    @Transactional
    class Enabled {

        @Autowired
        private WebApplicationContext context;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }

        @Test
        void contributingWrongCurrencyToSavingGoalReturns400() throws Exception {
            String createGoal = """
                    {"name":"Emergency fund","target_amount":50000000,"currency":"VND","day_of_month":5}
                    """;
            String created = mockMvc.perform(post("/api/v1/finance/savings")
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createGoal))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String goalId = created.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

            String contribute = """
                    {"kind":"saving","amount":10,"currency":"USD","saving_goal_id":"%s"}
                    """.formatted(goalId);

            mockMvc.perform(post("/api/v1/finance/transactions")
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(contribute))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("currency_mismatch"));
        }

        @Test
        void unauthenticatedDashboardRejected() throws Exception {
            mockMvc.perform(get("/api/v1/finance/dashboard"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void dashboardDefaultsToHomeCurrencyVnd() throws Exception {
            mockMvc.perform(get("/api/v1/finance/dashboard")
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.currency").value("VND"));
        }

        @Test
        void dailyLogWithInvalidCategoryReturns400() throws Exception {
            String body = """
                    {"kind":"daily","amount":45000,"currency":"VND","category":"not-a-real-category"}
                    """;
            mockMvc.perform(post("/api/v1/finance/transactions")
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_category"));
        }

        @Test
        void loanPaymentExceedingRemainingPrincipalReturns400() throws Exception {
            String createLoan = """
                    {"name":"Bike","principal":100,"monthly_payment":10,"currency":"USD","day_of_month":5}
                    """;
            String created = mockMvc.perform(post("/api/v1/finance/loans")
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createLoan))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String loanId = created.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

            String overpay = """
                    {"kind":"loan_payment","amount":1000,"currency":"USD","loan_id":"%s"}
                    """.formatted(loanId);

            mockMvc.perform(post("/api/v1/finance/transactions")
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(overpay))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("amount_exceeds_remaining"));
        }

        @Test
        void malformedTransactionCursorReturns400NotServerError() throws Exception {
            mockMvc.perform(get("/api/v1/finance/transactions")
                            .param("cursor", "not-valid-base64!!")
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid cursor"));
        }

        @Test
        void patchIncomeCannotChangeRecurrence() throws Exception {
            String createIncome = """
                    {"name":"Salary","amount":1000,"currency":"USD","day_of_month":1,"recurrence":"monthly"}
                    """;
            String created = mockMvc.perform(post("/api/v1/finance/incomes")
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createIncome))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String incomeId = created.replaceAll("(?s).*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

            String patch = """
                    {"recurrence":"none"}
                    """;
            mockMvc.perform(patch("/api/v1/finance/incomes/" + incomeId)
                            .with(authentication(ownerAuth()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(patch))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recurrence").value("monthly"));
        }
    }

    @Nested
    @SpringBootTest(properties = "ivelox.finance-enabled=false")
    @ActiveProfiles("test")
    @Import(FinanceTestSupport.class)
    class Disabled {

        @Autowired
        private WebApplicationContext context;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(SecurityMockMvcConfigurers.springSecurity())
                    .build();
        }

        @Test
        void dashboardReturns404WhenFlagDisabled() throws Exception {
            mockMvc.perform(get("/api/v1/finance/dashboard")
                            .with(authentication(ownerAuth())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("finance feature disabled"));
        }
    }
}
