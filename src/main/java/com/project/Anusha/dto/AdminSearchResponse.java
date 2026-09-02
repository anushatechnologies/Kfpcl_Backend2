package com.project.Anusha.dto;

import java.util.List;

public record AdminSearchResponse(
        String q,
        List<AdminProductSearchResult> products,
        List<AdminOrderSearchResult> orders,
        List<AdminCustomerSearchResult> customers
) {
}

