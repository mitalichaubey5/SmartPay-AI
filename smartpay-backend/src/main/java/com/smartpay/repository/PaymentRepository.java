package com.smartpay.repository;

import com.smartpay.entity.Payment;
import com.smartpay.entity.PaymentMethod;
import com.smartpay.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    Optional<Payment> findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(Long invoiceId, PaymentStatus status);

    Optional<Payment> findByPaypalOrderId(String paypalOrderId);

    // ------------------------------------------------------- user-scoped reads

    @Query("SELECT p FROM Payment p WHERE p.invoice.user.id = :userId ORDER BY p.createdAt DESC")
    List<Payment> findAllForUser(@Param("userId") Long userId);

    @Query("SELECT p FROM Payment p WHERE p.invoice.user.id = :userId AND p.status = :status ORDER BY p.createdAt DESC")
    List<Payment> findForUserByStatus(@Param("userId") Long userId, @Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.invoice.user.id = :userId AND p.method = :method ORDER BY p.createdAt DESC")
    List<Payment> findForUserByMethod(@Param("userId") Long userId, @Param("method") PaymentMethod method);

    @Query("SELECT p FROM Payment p WHERE p.id = :id AND p.invoice.user.id = :userId")
    Optional<Payment> findByIdForUser(@Param("id") Long id, @Param("userId") Long userId);
}
