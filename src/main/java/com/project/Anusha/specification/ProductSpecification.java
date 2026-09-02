package com.project.Anusha.specification;

import com.project.Anusha.model.Product;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    public static Specification<Product> withFilters(
            String search,
            Long categoryId,
            Long subCategoryId,
            Double minPrice,
            Double maxPrice,
            Boolean inStock,
            Boolean isTrending) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Search by name or description (case‑insensitive)
            if (search != null && !search.trim().isEmpty()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)
                ));
            }

            // Filter by category
            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), categoryId));
            }

            // Filter by sub‑category
            if (subCategoryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("subCategory").get("id"), subCategoryId));
            }

            // Price range
            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            // Only show products in stock
            if (inStock != null && inStock) {
                Join<Object, Object> variantJoin = root.join("variants");
                predicates.add(criteriaBuilder.isTrue(variantJoin.get("isActive")));
                predicates.add(criteriaBuilder.greaterThan(variantJoin.get("stock"), 0));
                query.distinct(true);
            }

            // Trending products
            if (isTrending != null && isTrending) {
                predicates.add(criteriaBuilder.isTrue(root.get("isTrending")));
            }

            // Always only active products
            predicates.add(criteriaBuilder.isTrue(root.get("isActive")));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
