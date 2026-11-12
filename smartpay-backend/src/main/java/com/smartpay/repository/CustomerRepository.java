package com.smartpay.repository;

import com.smartpay.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Customer> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.user.id = :userId
              AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(c.company) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY c.createdAt DESC
            """)
    List<Customer> searchForUser(@Param("userId") Long userId, @Param("search") String search);
}
