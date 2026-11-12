package com.smartpay.repository;

import com.smartpay.entity.Invoice;
import com.smartpay.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    // ------------------------------------------------------- user-scoped reads

    List<Invoice> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Invoice> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, InvoiceStatus status);

    Optional<Invoice> findByIdAndUserId(Long id, Long userId);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.user.id = :userId
              AND (LOWER(i.clientName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(i.clientEmail) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY i.createdAt DESC
            """)
    List<Invoice> searchForUser(@Param("userId") Long userId, @Param("search") String search);

    @Query("""
            SELECT i FROM Invoice i
            WHERE i.user.id = :userId
              AND i.status = :status
              AND (LOWER(i.clientName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(i.clientEmail) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY i.createdAt DESC
            """)
    List<Invoice> searchForUserWithStatus(@Param("userId") Long userId,
                                          @Param("status") InvoiceStatus status,
                                          @Param("search") String search);

    // ------------------------------------------------------------------ misc

    long countByStatus(InvoiceStatus status);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
