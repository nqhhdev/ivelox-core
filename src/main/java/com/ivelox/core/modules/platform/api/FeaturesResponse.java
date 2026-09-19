package com.ivelox.core.modules.platform.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.ivelox.core.modules.platform.domain.model.FeatureFlags;

public record FeaturesResponse(FeatureFlag health, FeatureFlag finance,
                               @JsonProperty("payment_approval") FeatureFlag paymentApproval) {

    public record FeatureFlag(boolean enabled, @JsonProperty("auth_required") boolean authRequired) {
    }

    public static FeaturesResponse from(FeatureFlags flags) {
        return new FeaturesResponse(
                new FeatureFlag(flags.healthEnabled(), true),
                new FeatureFlag(flags.financeEnabled(), true),
                new FeatureFlag(flags.paymentApprovalEnabled(), true)
        );
    }
}
