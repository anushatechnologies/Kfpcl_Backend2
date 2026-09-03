package com.project.Anusha.service;

import com.project.Anusha.model.Product;
import com.project.Anusha.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public Page<Product> getFilteredProducts(Long categoryId, Long subcategoryId, int page, int limit,
                                            BigDecimal minPrice, BigDecimal maxPrice, String query) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), limit > 0 ? limit : 10);
        return productRepository.filterProducts(categoryId, subcategoryId, minPrice, maxPrice, query, pageable);
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id).filter(Product::getIsActive);
    }

    public List<String> getSearchSuggestions(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return List.of();
        }
        return productRepository.findTop10ByNameContainingIgnoreCaseAndIsActiveTrue(prefix.trim())
                .stream()
                .map(Product::getName)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Product> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return productRepository.fullTextSearch(query.trim());
    }

    public List<Product> getTrendingProducts() {
        return productRepository.findTrendingProducts(PageRequest.of(0, 10));
    }

    public List<Product> getBestsellers() {
        return productRepository.findBestsellers(PageRequest.of(0, 10));
    }
}
