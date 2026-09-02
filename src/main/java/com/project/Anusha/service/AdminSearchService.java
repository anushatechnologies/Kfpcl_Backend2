package com.project.Anusha.service;

import com.project.Anusha.dto.AdminCustomerSearchResult;
import com.project.Anusha.dto.AdminOrderSearchResult;
import com.project.Anusha.dto.AdminProductSearchResult;
import com.project.Anusha.dto.AdminSearchResponse;
import com.project.Anusha.model.Customer;
import com.project.Anusha.repository.CustomerRepository;
import com.project.Anusha.repository.OrderRepository;
import com.project.Anusha.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminSearchService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public AdminSearchResponse search(String q, int limit) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return new AdminSearchResponse(q, List.of(), List.of(), List.of());
        }

        int cappedLimit = Math.min(Math.max(limit, 1), 50);
        Pageable pageable = PageRequest.of(0, cappedLimit, Sort.by("id").descending());

        List<AdminProductSearchResult> products = productRepository.adminGlobalSearch(query, pageable);
        List<AdminOrderSearchResult> orders = orderRepository.adminGlobalSearch(query, pageable);

        List<AdminCustomerSearchResult> customers = customerRepository
                .findByNameContainingIgnoreCaseOrPhoneNumberContaining(query, stripNonDigits(query), pageable)
                .stream()
                .map(this::toCustomerSearchResult)
                .toList();

        return new AdminSearchResponse(query, products, orders, customers);
    }

    private AdminCustomerSearchResult toCustomerSearchResult(Customer customer) {
        return new AdminCustomerSearchResult(
                customer.getId(),
                customer.getName(),
                customer.getPhoneNumber(),
                customer.getIsActive()
        );
    }

    private String stripNonDigits(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? value.toLowerCase(Locale.ROOT) : digits;
    }
}

