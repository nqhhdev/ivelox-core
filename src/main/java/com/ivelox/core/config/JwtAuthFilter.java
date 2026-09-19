package com.ivelox.core.config;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ivelox.core.modules.auth.application.port.out.TokenVerifierPort;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final TokenVerifierPort tokenVerifier;

    public JwtAuthFilter(TokenVerifierPort tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            tokenVerifier.verify(token)
                    .filter(verified -> "owner".equals(verified.subject()))
                    .ifPresentOrElse(
                            verified -> {
                                var auth = new UsernamePasswordAuthenticationToken(
                                        verified.subject(),
                                        null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + verified.role().toUpperCase()))
                                );
                                SecurityContextHolder.getContext().setAuthentication(auth);
                            },
                            SecurityContextHolder::clearContext
                    );
        }
        filterChain.doFilter(request, response);
    }
}
