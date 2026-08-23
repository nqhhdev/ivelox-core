package com.ivelox.core.platform;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ivelox.core.config.IveloxProperties;

@RestController
@RequestMapping("/api/v1")
public class PlatformController {

    private final IveloxProperties props;

    public PlatformController(IveloxProperties props) {
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, String> live() {
        return Map.of("status", "ok");
    }

    @GetMapping("/features")
    public Map<String, Object> features() {
        return Map.of(
                "health", Map.of(
                        "enabled", props.healthEnabled(),
                        "auth_required", true
                )
        );
    }
}
