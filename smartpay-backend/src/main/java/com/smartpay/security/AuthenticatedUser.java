package com.smartpay.security;

/**
 * Authenticated principal attached to the SecurityContext by the JWT filter.
 */
public record AuthenticatedUser(Long userId, String email, String role) {
}
