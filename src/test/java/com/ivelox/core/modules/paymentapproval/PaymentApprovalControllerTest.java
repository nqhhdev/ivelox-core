package com.ivelox.core.modules.paymentapproval;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class PaymentApprovalControllerTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    class Enabled {
        @Autowired
        private WebApplicationContext context;
        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        }

        @Test
        void unauthenticatedPaymentsAreAllowedForDemo() throws Exception {
            mockMvc.perform(get("/api/v1/payment-approval/payments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isArray());
        }

        @Test
        void malformedMonthAndIdReturnStableErrors() throws Exception {
            mockMvc.perform(get("/api/v1/payment-approval/summary")
                            .param("month", "2026-13"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_month"));

            mockMvc.perform(get("/api/v1/payment-approval/payments/not-a-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_id"));
        }
    }

    @Nested
    @SpringBootTest(properties = "ivelox.payment-approval-enabled=false")
    @ActiveProfiles("test")
    class Disabled {
        @Autowired
        private WebApplicationContext context;
        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        }

        @Test
        void paymentRoutesReturnFeatureDisabled() throws Exception {
            mockMvc.perform(get("/api/v1/payment-approval/payments"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("payment_feature_disabled"));
        }
    }
}
