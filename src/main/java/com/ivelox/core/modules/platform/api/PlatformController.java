package com.ivelox.core.modules.platform.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ivelox.core.modules.platform.application.port.in.GetFeatureFlagsUseCase;

@RestController
@RequestMapping("/api/v1")
public class PlatformController {

    private final GetFeatureFlagsUseCase getFeatureFlagsUseCase;

    public PlatformController(GetFeatureFlagsUseCase getFeatureFlagsUseCase) {
        this.getFeatureFlagsUseCase = getFeatureFlagsUseCase;
    }

    @GetMapping("/health")
    public Map<String, String> live() {
        return Map.of("status", "ok");
    }

    @GetMapping("/features")
    public FeaturesResponse features() {
        return FeaturesResponse.from(getFeatureFlagsUseCase.getFeatureFlags());
    }
}
