package com.smartpay.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Minimal, dependency-free HS256 JWT implementation (JDK Mac + Base64 URL).
 * Sufficient for the MVP; swap for a library in production hardening.
 */
@Component
public class JwtService {

    private final byte[] secret;
    private final long expirationSeconds;
    private final ObjectMapper objectMapper;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-seconds:86400}") long expirationSeconds,
                      ObjectMapper objectMapper) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "security.jwt.secret must be at least 32 bytes — set JWT_SECRET");
        }
        this.secret = bytes;
        this.expirationSeconds = expirationSeconds;
        this.objectMapper = objectMapper;
    }

    public record Claims(String email, Long userId, String role) {
    }

    public String createToken(Long userId, String email, String role) {
        long now = Instant.now().getEpochSecond();
        String header = b64(write(Map.of("alg", "HS256", "typ", "JWT")));
        String payload = b64(write(Map.of(
                "sub", email, "uid", userId, "role", role,
                "iat", now, "exp", now + expirationSeconds)));
        String signingInput = header + "." + payload;
        return signingInput + "." + b64(hmac(signingInput));
    }

    /**
     * Verifies the signature and expiry, returning the claims.
     *
     * @throws IllegalArgumentException if the token is malformed, has a bad
     *                                  signature or is expired
     */
    public Claims parse(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed token");
        }
        byte[] expected = hmac(parts[0] + "." + parts[1]);
        byte[] provided;
        try {
            provided = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed token signature");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new IllegalArgumentException("Invalid token signature");
        }
        Map<?, ?> payload = read(parts[1]);
        long exp = ((Number) payload.get("exp")).longValue();
        if (Instant.now().getEpochSecond() >= exp) {
            throw new IllegalArgumentException("Token expired");
        }
        return new Claims(
                (String) payload.get("sub"),
                ((Number) payload.get("uid")).longValue(),
                (String) payload.get("role"));
    }

    // ------------------------------------------------------------------ helpers

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] write(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialization failure", e);
        }
    }

    private Map<?, ?> read(String base64) {
        try {
            byte[] json = Base64.getUrlDecoder().decode(base64);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed token payload");
        }
    }
}
