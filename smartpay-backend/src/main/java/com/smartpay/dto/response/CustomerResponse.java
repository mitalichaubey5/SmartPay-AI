package com.smartpay.dto.response;

import com.smartpay.entity.Customer;

import java.time.LocalDateTime;

/**
 * Customer API response.
 */
public record CustomerResponse(
        Long id,
        String name,
        String email,
        String phone,
        String company,
        String city,
        String country,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getCompany(),
                customer.getCity(),
                customer.getCountry(),
                customer.getNotes(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
