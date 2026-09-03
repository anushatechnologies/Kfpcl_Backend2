package com.project.Anusha.controller;

import com.project.Anusha.model.Product;
import com.project.Anusha.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    public ResponseEntity<Page<Product>> getProducts(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long subcategoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(productService.getFilteredProducts(categoryId, subcategoryId, page, limit, minPrice, maxPrice, query));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> getSuggestions(@RequestParam(name = "query", required = false) String prefix) {
        return ResponseEntity.ok(productService.getSearchSuggestions(prefix));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchProducts(@RequestParam(name = "query", required = false) String query) {
        return ResponseEntity.ok(productService.searchProducts(query));
    }

    @GetMapping("/trending")
    public ResponseEntity<List<Product>> getTrendingProducts() {
        return ResponseEntity.ok(productService.getTrendingProducts());
    }

    @GetMapping("/bestseller")
    public ResponseEntity<List<Product>> getBestsellers() {
        return ResponseEntity.ok(productService.getBestsellers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
