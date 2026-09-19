package com.ivelox.core.modules.auth.application.port.out;

public interface TokenIssuerPort {

    String issueOwnerToken();

    long ttlSeconds();
}
