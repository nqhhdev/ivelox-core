package com.ivelox.core.modules.auth.api;

import jakarta.validation.constraints.NotBlank;

public record VerifyOtpRequest(@NotBlank String code) {
}
