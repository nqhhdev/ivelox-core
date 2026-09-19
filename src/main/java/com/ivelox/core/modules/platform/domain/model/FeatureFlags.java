package com.ivelox.core.modules.platform.domain.model;

/** Pure domain model — no Spring, no JPA. */
public record FeatureFlags(boolean healthEnabled, boolean financeEnabled, boolean paymentApprovalEnabled) {
}
