package com.smartpay.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create / update a customer.
 */
public record CustomerRequest(

        @NotBlank(message = "name is required")
        @Size(min = 2, max = 200, message = "name must be 2–200 characters")
        String name,

        @Email(message = "email must be a valid address")
        @Size(max = 200)
        String email,

        @Size(max = 40) String phone,
        @Size(max = 200) String company,
        @Size(max = 100) String city,
        @Size(max = 100) String country,
        @Size(max = 1000) String notes) {
}
