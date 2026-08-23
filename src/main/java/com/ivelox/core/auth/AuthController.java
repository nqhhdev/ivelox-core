package com.ivelox.core.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final OtpService otpService;
    private final JwtService jwtService;

    public AuthController(OtpService otpService, JwtService jwtService) {
        this.otpService = otpService;
        this.jwtService = jwtService;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp() {
        otpService.requestOtp();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/verify")
    public Map<String, Object> verify(@Valid @RequestBody VerifyRequest body) {
        otpService.verifyOtp(body.code());
        String token = jwtService.issueOwnerToken();
        return Map.of(
                "access_token", token,
                "token_type", "Bearer",
                "expires_in", jwtService.ttlSeconds()
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        return Map.of(
                "subject", authentication.getName(),
                "role", "owner"
        );
    }

    public record VerifyRequest(@NotBlank String code) {
    }
}
