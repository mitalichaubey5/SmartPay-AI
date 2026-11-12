package com.smartpay.service;

import com.smartpay.dto.request.CreateInvoiceRequest;
import com.smartpay.dto.response.InvoiceResponse;
import com.smartpay.entity.Customer;
import com.smartpay.entity.Invoice;
import com.smartpay.entity.InvoiceItem;
import com.smartpay.entity.InvoiceStatus;
import com.smartpay.entity.Payment;
import com.smartpay.entity.PaymentStatus;
import com.smartpay.entity.User;
import com.smartpay.exception.BadRequestException;
import com.smartpay.exception.NotFoundException;
import com.smartpay.repository.InvoiceRepository;
import com.smartpay.repository.PaymentRepository;
import com.smartpay.repository.UserRepository;
import com.smartpay.security.AuthenticatedUser;
import com.smartpay.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Invoice lifecycle: creation from a (reviewed) AI draft or manual entry,
 * listing, editing while DRAFT, deletion, and status transitions. Totals
 * are always recomputed server-side, and every record is owned by exactly
 * one user.
 */
@Service
public class InvoiceService {

    private static final int MONEY_SCALE = 2;

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerService customerService;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          PaymentRepository paymentRepository,
                          CustomerService customerService,
                          UserRepository userRepository,
                          CurrentUserProvider currentUserProvider) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.customerService = customerService;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public InvoiceResponse create(CreateInvoiceRequest request) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(nextInvoiceNumber());
        invoice.setUser(currentUser());
        apply(invoice, request);
        return InvoiceResponse.from(invoiceRepository.save(invoice), null);
    }

    // -------------------------------------------------------------------- list

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(String status, String search) {
        Long userId = currentUserProvider.require().userId();
        List<Invoice> invoices = findInvoices(userId, status, search);
        return invoices.stream().map(this::toResponse).toList();
    }

    // --------------------------------------------------------------------- get

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        return toResponse(findInvoice(id));
    }

    // ------------------------------------------------------------------ update

    @Transactional
    public InvoiceResponse update(Long id, CreateInvoiceRequest request) {
        Invoice invoice = findInvoice(id);
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new BadRequestException(
                    "Only DRAFT invoices can be edited (current status: " + invoice.getStatus() + ")");
        }
        apply(invoice, request);
        return toResponse(invoiceRepository.save(invoice));
    }

    // ------------------------------------------------------------------ delete

    @Transactional
    public void delete(Long id) {
        Invoice invoice = findInvoice(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BadRequestException("Paid invoices cannot be deleted (payment audit is retained)");
        }
        invoiceRepository.delete(invoice);
    }

    // ------------------------------------------------------------------- util

    /**
     * Loads an invoice and verifies it belongs to the current user.
     */
    @Transactional(readOnly = true)
    public Invoice findInvoice(Long id) {
        Long userId = currentUserProvider.require().userId();
        return invoiceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Invoice " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse toResponse(Invoice invoice) {
        Optional<Payment> latest = paymentRepository
                .findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoice.getId(), PaymentStatus.COMPLETED)
                .or(() -> paymentRepository.findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(
                        invoice.getId(), PaymentStatus.CREATED));
        return InvoiceResponse.from(invoice, latest.orElse(null));
    }

    private List<Invoice> findInvoices(Long userId, String status, String search) {
        InvoiceStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = InvoiceStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status filter: " + status);
            }
        }
        boolean hasSearch = search != null && !search.isBlank();
        if (statusFilter != null && hasSearch) {
            return invoiceRepository.searchForUserWithStatus(userId, statusFilter, search.trim());
        }
        if (statusFilter != null) {
            return invoiceRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, statusFilter);
        }
        if (hasSearch) {
            return invoiceRepository.searchForUser(userId, search.trim());
        }
        return invoiceRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    private void apply(Invoice invoice, CreateInvoiceRequest request) {
        invoice.setClientName(request.clientName().trim());
        invoice.setClientEmail(trimToNull(request.clientEmail()));
        invoice.setCurrency(normalizeCurrency(request.currency()));
        invoice.setTaxRate(money(request.taxRate() == null ? BigDecimal.ZERO : request.taxRate()));
        invoice.setDueDate(request.dueDate());
        invoice.setNotes(trimToNull(request.notes()));
        invoice.setAiSource(trimToNull(request.aiSource()));
        invoice.setCustomer(resolveCustomer(request.customerId()));

        invoice.clearItems();
        for (var itemRequest : request.items()) {
            InvoiceItem item = new InvoiceItem();
            item.setDescription(itemRequest.description().trim());
            item.setQuantity(itemRequest.quantity());
            item.setUnitPrice(money(itemRequest.unitPrice()));
            item.setAmount(money(itemRequest.quantity().multiply(itemRequest.unitPrice())));
            invoice.addItem(item);
        }
        recomputeTotals(invoice);
    }

    private void recomputeTotals(Invoice invoice) {
        BigDecimal subtotal = invoice.getItems().stream()
                .map(InvoiceItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(invoice.getTaxRate())
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
        invoice.setSubtotal(subtotal);
        invoice.setTaxAmount(tax);
        invoice.setTotal(subtotal.add(tax).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }

    private String nextInvoiceNumber() {
        long next = invoiceRepository.count() + 1;
        String number = String.format("INV-%04d", next);
        // Guard against gaps from deletions.
        while (invoiceRepository.existsByInvoiceNumber(number)) {
            next++;
            number = String.format("INV-%04d", next);
        }
        return number;
    }

    private Customer resolveCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        // CustomerService.findCustomer already verifies user ownership.
        return customerService.findCustomer(customerId);
    }

    private User currentUser() {
        AuthenticatedUser user = currentUserProvider.require();
        return userRepository.findById(user.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String normalizeCurrency(String currency) {
        String value = currency == null ? "USD" : currency.trim().toUpperCase();
        if (!value.matches("[A-Z]{3}")) {
            throw new BadRequestException("currency must be a 3-letter ISO-4217 code");
        }
        return value;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
