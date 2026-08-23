package com.ivelox.core.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.ivelox.core.config.IveloxProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final IveloxProperties props;
    private final SecretKey key;

    public JwtService(IveloxProperties props) {
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

    public long ttlSeconds() {
        return props.jwtTtlSeconds();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
