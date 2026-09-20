package com.ivelox.core.modules.paymentapproval;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = "ivelox.payment-approval-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentApprovalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unauthenticatedPaymentsAreAllowedForDemo() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/payment-approval/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currency").value("AED"))
                .andReturn();

        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(put("/api/v1/payment-approval/requests/" + id + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"8888\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.currency").value("AED"));

        // Shared H2 may already contain decided rows from other tests — don't assume index 0.
        mockMvc.perform(get("/api/v1/payment-approval/payments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", hasItem(id)))
                .andExpect(jsonPath("$.data.items[*].currency", hasItem("AED")));
    }

    @Test
    void malformedMonthAndIdReturnStableErrors() throws Exception {
        mockMvc.perform(get("/api/v1/payment-approval/summary")
                        .param("month", "2026-13")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_month"));

        mockMvc.perform(get("/api/v1/payment-approval/payments/not-a-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_id"));
    }

    @Test
    void deleteOneAndDeleteManyRemovePayments() throws Exception {
        String id1 = createPendingId();
        String id2 = createPendingId();
        String id3 = createPendingId();

        mockMvc.perform(delete("/api/v1/payment-approval/payments/" + id1)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/payment-approval/requests")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id", not(hasItem(id1))));

        mockMvc.perform(delete("/api/v1/payment-approval/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + id2 + "\",\"" + id3 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(2));

        mockMvc.perform(delete("/api/v1/payment-approval/payments/" + id1)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("payment_not_found"));
    }

    private String createPendingId() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/payment-approval/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
    }
}

@SpringBootTest(properties = "ivelox.payment-approval-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentApprovalControllerDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void paymentRoutesReturnFeatureDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/payment-approval/payments")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("payment_feature_disabled"));
    }
}
