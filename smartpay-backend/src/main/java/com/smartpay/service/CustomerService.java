package com.smartpay.service;

import com.smartpay.dto.request.CustomerRequest;
import com.smartpay.dto.response.CustomerResponse;
import com.smartpay.entity.Customer;
import com.smartpay.entity.User;
import com.smartpay.exception.NotFoundException;
import com.smartpay.repository.CustomerRepository;
import com.smartpay.repository.UserRepository;
import com.smartpay.security.AuthenticatedUser;
import com.smartpay.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Customer CRUD — every record is owned by exactly one user and all reads
 * and writes are scoped to that user.
 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public CustomerService(CustomerRepository customerRepository,
                           UserRepository userRepository,
                           CurrentUserProvider currentUserProvider) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setUser(currentUser());
        apply(customer, request);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list(String search) {
        Long userId = currentUserProvider.require().userId();
        List<Customer> customers = (search == null || search.isBlank())
                ? customerRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                : customerRepository.searchForUser(userId, search.trim());
        return customers.stream().map(CustomerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(findCustomer(id));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = findCustomer(id);
        apply(customer, request);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = findCustomer(id);
        // Invoices keep their own client snapshot, so history stays intact.
        customerRepository.delete(customer);
    }

    /**
     * Loads a customer and verifies it belongs to the current user.
     */
    @Transactional(readOnly = true)
    public Customer findCustomer(Long id) {
        Long userId = currentUserProvider.require().userId();
        return customerRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " not found"));
    }

    private User currentUser() {
        AuthenticatedUser user = currentUserProvider.require();
        return userRepository.findById(user.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void apply(Customer customer, CustomerRequest request) {
        customer.setName(request.name().trim());
        customer.setEmail(trimToNull(request.email()));
        customer.setPhone(trimToNull(request.phone()));
        customer.setCompany(trimToNull(request.company()));
        customer.setCity(trimToNull(request.city()));
        customer.setCountry(trimToNull(request.country()));
        customer.setNotes(trimToNull(request.notes()));
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
