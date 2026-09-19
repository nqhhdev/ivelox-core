package com.ivelox.core.modules.auth.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ivelox.core.modules.auth.application.port.in.RequestOtpUseCase;
import com.ivelox.core.modules.auth.application.port.in.VerifyOtpAndIssueSessionUseCase;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RequestOtpUseCase requestOtpUseCase;
    private final VerifyOtpAndIssueSessionUseCase verifyOtpAndIssueSessionUseCase;

    public AuthController(
            RequestOtpUseCase requestOtpUseCase,
            VerifyOtpAndIssueSessionUseCase verifyOtpAndIssueSessionUseCase
    ) {
        this.requestOtpUseCase = requestOtpUseCase;
        this.verifyOtpAndIssueSessionUseCase = verifyOtpAndIssueSessionUseCase;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp() {
        requestOtpUseCase.requestOtp();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/otp/verify")
    public SessionResponse verify(@Valid @RequestBody VerifyOtpRequest body) {
        var session = verifyOtpAndIssueSessionUseCase.verifyOtpAndIssueSession(body.code());
        return SessionResponse.from(session);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return new MeResponse(authentication.getName(), "owner");
    }
}
