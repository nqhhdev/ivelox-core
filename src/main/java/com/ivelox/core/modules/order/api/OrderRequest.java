package com.ivelox.core.modules.order.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrderRequest(
        @NotBlank String customerName,
        @Positive BigDecimal totalAmount
) {
}
