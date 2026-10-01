package com.libera.backend.security;

import com.libera.backend.domain.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Emite los JWT de sesión. El "sub" es el ID del usuario: es lo que los controllers leen con Principal.getName().
 */
@Service
public class JwtTokenService {

    public static final String ISSUER = "libera-backend";
    public static final String ROLES_CLAIM = "roles";

    private final JwtEncoder jwtEncoder;
    private final Duration expiration;

    public JwtTokenService(JwtEncoder jwtEncoder, @Value("${libera.security.jwt.expiration:24h}") Duration expiration) {
        this.jwtEncoder = jwtEncoder;
        this.expiration = expiration;
    }

    public String issueToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim(ROLES_CLAIM, List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getExpirationSeconds() {
        return expiration.toSeconds();
    }
}
