package com.smartpay.dto.response;

import com.smartpay.entity.User;

/**
 * Public user profile.
 */
public record UserResponse(
        Long id,
        String email,
        String fullName,
        String role) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getRole().name());
    }
}
