package com.ivelox.core.modules.auth.application.port.out;

import java.util.Optional;

public interface TokenVerifierPort {

    /** Parses and verifies a signed session token. Empty if invalid/expired/malformed. */
    Optional<VerifiedToken> verify(String token);

    record VerifiedToken(String subject, String role) {
    }
}
