package com.project.Anusha.service;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.RefreshToken;
import com.project.Anusha.model.UserMain;
import com.project.Anusha.repository.CustomerRepository;
import com.project.Anusha.repository.RefreshTokenRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public CustomerService(CustomerRepository customerRepository,
                           RefreshTokenRepository refreshTokenRepository) {
        this.customerRepository = customerRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    // ------------------------------------------------------------------ auth

    /**
     * Find a customer by phone number (via UserMain join).
     * Returns null if not found (used in controllers for easy null-check).
     */
    public Customer findByPhoneNumber(String phoneNumber) {
        return customerRepository.findByPhoneNumber(phoneNumber).orElse(null);
    }

    /**
     * Find a customer by Firebase UID (via UserMain join).
     */
    public Customer findByFirebaseUid(String fid) {
        return customerRepository.findByFirebaseUid(fid).orElse(null);
    }

    /**
     * Find a customer by UserMain entity.
     */
    public Optional<Customer> findByUserMain(UserMain userMain) {
        return customerRepository.findByUserMainId(userMain.getId());
    }

    /**
     * Create a new Customer linked to the given UserMain.
     * The UserMain must already have CUSTOMER role added before calling this.
     */
    public Customer createCustomer(UserMain userMain, String username) {
        Customer customer = new Customer();
        customer.setUserMain(userMain);
        customer.setUsername(username.trim());
        customer.setIsActive(true);
        return customerRepository.save(customer);
    }

    public Customer updateCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

    public Customer getCustomerByPhone(String phoneNumber) {
        return customerRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new com.project.Anusha.exception.ResourceNotFoundException("Customer not found: " + phoneNumber));
    }

    // ------------------------------------------------------------------ admin

    public Page<Customer> searchCustomers(String search, Boolean active, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            if (active != null) {
                return customerRepository
                        .findByNameContainingIgnoreCaseOrPhoneNumberContainingAndIsActive(
                                search, search, active, pageable);
            }
            return customerRepository
                    .findByNameContainingIgnoreCaseOrPhoneNumberContaining(
                            search, search, pageable);
        }
        if (active != null) {
            return customerRepository.findByIsActive(active, pageable);
        }
        return customerRepository.findAll(pageable);
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found: " + id));
    }

    public Customer setCustomerActive(Long id, boolean active) {
        Customer customer = getCustomerById(id);
        customer.setIsActive(active);
        return customerRepository.save(customer);
    }

    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new RuntimeException("Customer not found: " + id);
        }
        customerRepository.deleteById(id);
    }

    /**
     * Self-service account deletion (soft delete).
     *
     * - Marks the customer inactive so they cannot log in again.
     * - Anonymizes name/email so the user disappears from listings while
     *   preserving order/audit history (required for accounting + GST).
     * - Revokes every active refresh token for this user across all clients
     *   (CustomerApp + Website) so existing sessions can't be used.
     */
    public void deleteOwnAccount(String phoneNumber) {
        Customer customer = getCustomerByPhone(phoneNumber);

        UserMain userMain = customer.getUserMain();
        if (userMain != null) {
            List<RefreshToken> active = refreshTokenRepository
                    .findByPrincipalTypeAndPrincipalIdAndRevokedFalse(
                            RefreshToken.PrincipalType.USER_MAIN, userMain.getId());
            LocalDateTime now = LocalDateTime.now();
            for (RefreshToken token : active) {
                token.setRevoked(true);
                token.setRevokedAt(now);
            }
            if (!active.isEmpty()) {
                refreshTokenRepository.saveAll(active);
            }
        }

        customer.setIsActive(false);
        customer.setName("Deleted user");
        customer.setEmail(null);
        customerRepository.save(customer);
    }
}
