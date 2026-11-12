package com.smartpay.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration request.
 */
public record RegisterRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 150)
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be 8–100 characters")
        String password,

        @Size(max = 150) String fullName) {
}
