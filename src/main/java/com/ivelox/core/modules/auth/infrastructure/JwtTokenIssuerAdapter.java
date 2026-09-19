package com.ivelox.core.modules.auth.infrastructure;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.ivelox.core.config.IveloxProperties;
import com.ivelox.core.modules.auth.application.port.out.TokenIssuerPort;
import com.ivelox.core.modules.auth.application.port.out.TokenVerifierPort;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenIssuerAdapter implements TokenIssuerPort, TokenVerifierPort {

    private final IveloxProperties props;
    private final SecretKey key;

    public JwtTokenIssuerAdapter(IveloxProperties props) {
        this.props = props;
        byte[] secret = props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            // pad for HS256 minimum key length in local/dev
            byte[] padded = new byte[32];
            System.arraycopy(secret, 0, padded, 0, Math.min(secret.length, 32));
            secret = padded;
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    @Override
    public String issueOwnerToken() {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.jwtTtlSeconds());
        return Jwts.builder()
                .subject("owner")
                .claim("role", "owner")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    @Override
    public long ttlSeconds() {
        return props.jwtTtlSeconds();
    }

    @Override
    public Optional<VerifiedToken> verify(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = String.valueOf(claims.get("role", String.class));
            return Optional.of(new VerifiedToken(claims.getSubject(), role));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
