package com.smartpay.dto.response;

/**
 * Successful authentication response.
 */
public record TokenResponse(
        String token,
        long expiresIn,
        UserResponse user) {
}
