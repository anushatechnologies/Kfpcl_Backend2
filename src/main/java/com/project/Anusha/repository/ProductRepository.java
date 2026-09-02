package com.project.Anusha.repository;

import com.project.Anusha.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.project.Anusha.dto.AdminProductSearchResult;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findBySubCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(Long subCategoryId);

    List<Product> findByIsTrendingTrueAndIsActiveTrue();

    List<Product> findByNameContainingIgnoreCaseAndIsActiveTrue(String name);

    List<Product> findByCategoryIdAndIsActiveTrue(Long categoryId);

    List<Product> findByBestSellerTrueAndIsActiveTrue();

    List<Product> findByCategoryIdAndNameContainingIgnoreCaseAndIsActiveTrue(Long categoryId, String name);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Product> searchByNameOrDescription(@Param("keyword") String keyword);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ORDER BY p.displayOrder ASC")
    List<Product> searchByNameOnly(@Param("keyword") String keyword);

    List<Product> findByStoreId(Long storeId);

    List<Product> findBySubCategoryId(Long subCategoryId);

    @Query("""
        SELECT DISTINCT p FROM Product p
        LEFT JOIN p.subCategory sc
        WHERE p.category.id = :categoryId OR sc.category.id = :categoryId
        """)
    List<Product> findByCategoryOrSubCategoryCategory(@Param("categoryId") Long categoryId);

    @Query("""
        SELECT p FROM Product p
        WHERE (p.deleted = false OR p.deleted IS NULL)
        ORDER BY p.displayOrder ASC, p.id DESC
        """)
    List<Product> findByDeletedFalse();

    @Query(value = """
        SELECT DISTINCT p FROM Product p
        LEFT JOIN p.category c
        LEFT JOIN p.subCategory sc
        LEFT JOIN p.variants v
        WHERE (p.deleted = false OR p.deleted IS NULL)
          AND (:includeInactive = true OR (
              p.isActive = true
              AND (p.isDraft = false OR p.isDraft IS NULL)
              AND (v IS NULL OR v.isActive = true)
          ))
          AND (:categoryId IS NULL OR c.id = :categoryId)
          AND (:subCategoryId IS NULL OR sc.id = :subCategoryId)
          AND (:storeId IS NULL OR p.store.id = :storeId)
        ORDER BY p.displayOrder ASC, p.id DESC
        """,
        countQuery = """
        SELECT COUNT(DISTINCT p) FROM Product p
        LEFT JOIN p.category c
        LEFT JOIN p.subCategory sc
        LEFT JOIN p.variants v
        WHERE (p.deleted = false OR p.deleted IS NULL)
          AND (:includeInactive = true OR (
              p.isActive = true
              AND (p.isDraft = false OR p.isDraft IS NULL)
              AND (v IS NULL OR v.isActive = true)
          ))
          AND (:categoryId IS NULL OR c.id = :categoryId)
          AND (:subCategoryId IS NULL OR sc.id = :subCategoryId)
          AND (:storeId IS NULL OR p.store.id = :storeId)
        """)
    Page<Product> findProductPage(
            @Param("categoryId") Long categoryId,
            @Param("subCategoryId") Long subCategoryId,
            @Param("storeId") Long storeId,
            @Param("includeInactive") boolean includeInactive,
            Pageable pageable
    );

    @Query("""
        SELECT p FROM Product p
        WHERE p.store.id = :storeId
          AND (p.deleted = false OR p.deleted IS NULL)
        ORDER BY p.displayOrder ASC, p.id DESC
        """)
    List<Product> findByStoreIdAndDeletedFalse(@Param("storeId") Long storeId);

    @Query("""
        SELECT DISTINCT p FROM Product p
        JOIN p.variants v
        WHERE p.isActive = true
          AND v.isActive = true
          AND (:categoryId IS NULL OR p.category.id = :categoryId)
          AND (:subCategoryId IS NULL OR p.subCategory.id = :subCategoryId)
          AND (:storeId IS NULL OR p.store.id = :storeId)
          AND (:minPrice IS NULL OR v.price >= :minPrice)
          AND (:maxPrice IS NULL OR v.price <= :maxPrice)
          AND (:trending IS NULL OR p.isTrending = :trending)
          AND (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
        ORDER BY p.displayOrder ASC
        """)
    List<Product> filterProducts(
            @Param("categoryId") Long categoryId,
            @Param("subCategoryId") Long subCategoryId,
            @Param("storeId") Long storeId,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("trending") Boolean trending,
            @Param("keyword") String keyword
    );

    Page<Product> findByStoreId(Long storeId, Pageable pageable);

    List<Product> findBySubCategoryIdAndIdNot(Long subCategoryId, Long productId);

    /**
     * Zepto-style instant search:
     *  Priority 1 — name starts with keyword  (most relevant)
     *  Priority 2 — name contains keyword
     *  Priority 3 — category/subcategory name matches
     *  Priority 4 — description contains keyword
     * Returns top N active products ordered by relevance score then display order.
     */
    @Query("""
        SELECT DISTINCT p FROM Product p
        LEFT JOIN p.category c
        LEFT JOIN p.subCategory sc
        WHERE p.isActive = true
          AND (p.deleted = false OR p.deleted IS NULL)
          AND (
              LOWER(p.name) LIKE LOWER(CONCAT(:keyword, '%'))
           OR LOWER(REPLACE(p.name, ' ', '')) LIKE LOWER(CONCAT('%', :compactKeyword, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('% ', :keyword, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('%-', :keyword, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('%/', :keyword, '%'))
           OR (c  IS NOT NULL AND LOWER(c.name)  LIKE LOWER(CONCAT('%', :keyword, '%')))
           OR (sc IS NOT NULL AND LOWER(sc.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
           OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY
          CASE WHEN LOWER(p.name) LIKE LOWER(CONCAT(:keyword, '%')) THEN 0 ELSE 1 END ASC,
          CASE WHEN LOWER(REPLACE(p.name, ' ', '')) LIKE LOWER(CONCAT(:compactKeyword, '%')) THEN 0 ELSE 1 END ASC,
          CASE WHEN LOWER(p.name) LIKE LOWER(CONCAT('% ', :keyword, '%')) THEN 0 ELSE 1 END ASC,
          p.isTrending DESC,
          p.displayOrder ASC
        """)
    Page<Product> searchSuggestions(
            @Param("keyword") String keyword,
            @Param("compactKeyword") String compactKeyword,
            Pageable pageable
    );

    /**
     * Full paginated search — same relevance as suggestions but returns a full page.
     */
    @Query(value = """
        SELECT DISTINCT p FROM Product p
        LEFT JOIN p.category c
        LEFT JOIN p.subCategory sc
        JOIN p.variants v
        WHERE p.isActive = true AND v.isActive = true
          AND (p.deleted = false OR p.deleted IS NULL)
          AND (
              LOWER(p.name) LIKE LOWER(CONCAT(:keyword, '%'))
           OR LOWER(REPLACE(p.name, ' ', '')) LIKE LOWER(CONCAT('%', :compactKeyword, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('% ', :keyword, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('%-', :keyword, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('%/', :keyword, '%'))
           OR (c  IS NOT NULL AND LOWER(c.name)  LIKE LOWER(CONCAT('%', :keyword, '%')))
           OR (sc IS NOT NULL AND LOWER(sc.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
           OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY
          CASE WHEN LOWER(p.name) LIKE LOWER(CONCAT(:keyword, '%')) THEN 0 ELSE 1 END ASC,
          CASE WHEN LOWER(REPLACE(p.name, ' ', '')) LIKE LOWER(CONCAT(:compactKeyword, '%')) THEN 0 ELSE 1 END ASC,
          p.isTrending DESC,
          p.displayOrder ASC
        """,
        countQuery = """
        SELECT COUNT(DISTINCT p) FROM Product p
        LEFT JOIN p.category c
        LEFT JOIN p.subCategory sc
        JOIN p.variants v
        WHERE p.isActive = true AND v.isActive = true
          AND (p.deleted = false OR p.deleted IS NULL)
          AND (
              LOWER(p.name) LIKE LOWER(CONCAT(:keyword, '%'))
           OR LOWER(REPLACE(p.name, ' ', '')) LIKE LOWER(CONCAT('%', :compactKeyword, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('% ', :keyword, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('%-', :keyword, '%'))
           OR LOWER(p.name) LIKE LOWER(CONCAT('%/', :keyword, '%'))
           OR (c  IS NOT NULL AND LOWER(c.name)  LIKE LOWER(CONCAT('%', :keyword, '%')))
           OR (sc IS NOT NULL AND LOWER(sc.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
           OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        """)
    Page<Product> searchPaginated(
            @Param("keyword") String keyword,
            @Param("compactKeyword") String compactKeyword,
            Pageable pageable
    );

    /**
     * Admin global search: returns lightweight results for dashboard-level search.
     */
    @Query("""
        SELECT new com.project.Anusha.dto.AdminProductSearchResult(
            p.id,
            p.name,
            p.imageUrl,
            COALESCE(s.name, '')
        )
        FROM Product p
        LEFT JOIN p.store s
        WHERE p.isActive = true
          AND (p.deleted = false OR p.deleted IS NULL)
          AND (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :q, '%'))
             OR LOWER(COALESCE(s.name, '')) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY p.displayOrder ASC, p.id DESC
        """)
    List<AdminProductSearchResult> adminGlobalSearch(@Param("q") String q, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Product p
        SET p.isActive = false, p.updatedAt = :now
        WHERE p.isActive = true
          AND (
            p.deleted = true
            OR p.isDraft = true
            OR (p.publishAt IS NOT NULL AND :now < p.publishAt)
            OR (p.unpublishAt IS NOT NULL AND :now > p.unpublishAt)
          )
        """)
    int deactivateExpiredOrDraftProducts(@Param("now") java.time.LocalDateTime now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
        UPDATE Product p
        SET p.isActive = true, p.updatedAt = :now
        WHERE p.isActive = false
          AND (p.deleted = false OR p.deleted IS NULL)
          AND (p.isDraft = false OR p.isDraft IS NULL)
          AND (p.publishAt IS NOT NULL AND :now >= p.publishAt)
          AND (p.unpublishAt IS NULL OR :now <= p.unpublishAt)
        """)
    int activatePublishedProducts(@Param("now") java.time.LocalDateTime now);
}
