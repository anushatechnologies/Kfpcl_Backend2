package com.project.Anusha.service;

import com.project.Anusha.model.Order;
import com.project.Anusha.model.Product;
import com.project.Anusha.repository.OrderRepository;
import com.project.Anusha.repository.ProductRepository;
import com.project.Anusha.repository.UserLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardLiveService {

    private final AdminService adminService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserLogRepository userLogRepository;

    public Map<String, Object> getLiveSnapshot(int activeWindowMinutes) {
        int window = Math.min(Math.max(activeWindowMinutes, 1), 24 * 60);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusMinutes(window);

        long activeCustomers = userLogRepository.countDistinctUsersSince(since, "CUSTOMER");
        long activeDelivery = userLogRepository.countDistinctUsersSince(since, "DELIVERY_PERSON");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", now);
        payload.put("summary", adminService.getDashboardSummary());
        payload.put("activeUsers", Map.of(
                "windowMinutes", window,
                "customers", activeCustomers,
                "deliveryPartners", activeDelivery,
                "total", activeCustomers + activeDelivery
        ));
        payload.put("inventory", buildInventorySnapshot(10));
        return payload;
    }

    public List<Map<String, Object>> getOrdersRevenueBar(String period) {
        int days = resolveDays(period);
        LocalDateTime now = LocalDateTime.now();
        LocalDate startDate = now.toLocalDate().minusDays(days - 1L);
        LocalDateTime start = startDate.atStartOfDay();
        List<Order> orders = orderRepository.findByPlacedAtBetween(start, now);

        Map<LocalDate, BarAccumulator> grouped = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate day = startDate.plusDays(i);
            grouped.put(day, new BarAccumulator());
        }

        for (Order order : orders) {
            if (order == null || order.getPlacedAt() == null) {
                continue;
            }
            LocalDate day = order.getPlacedAt().toLocalDate();
            BarAccumulator acc = grouped.get(day);
            if (acc == null) {
                continue;
            }
            acc.orders++;

            String status = order.getOrderStatus() != null ? order.getOrderStatus().toLowerCase(Locale.ROOT) : "";
            if (!status.equals("cancelled") && !status.equals("rejected")) {
                BigDecimal amount = order.getGrandTotal() != null ? order.getGrandTotal() : BigDecimal.ZERO;
                acc.revenue = acc.revenue.add(amount);
            }
        }

        DateTimeFormatter labelFormatter = days <= 7
                ? DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
                : DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

        List<Map<String, Object>> bars = new ArrayList<>();
        for (Map.Entry<LocalDate, BarAccumulator> entry : grouped.entrySet()) {
            LocalDate day = entry.getKey();
            BarAccumulator acc = entry.getValue();
            bars.add(Map.of(
                    "date", day.toString(),
                    "label", labelFormatter.format(day),
                    "orders", acc.orders,
                    "revenue", acc.revenue
            ));
        }

        return bars;
    }

    private Map<String, Object> buildInventorySnapshot(int lowStockThreshold) {
        int threshold = Math.max(lowStockThreshold, 1);
        List<Product> products = productRepository.findByDeletedFalse();
        List<Map<String, Object>> outOfStockProducts = new ArrayList<>();
        List<Map<String, Object>> lowStockProducts = new ArrayList<>();

        for (Product product : products) {
            int stock = totalActiveStock(product);
            if (stock <= 0) {
                outOfStockProducts.add(productStockRow(product, stock, "Out of stock"));
            } else if (stock <= threshold) {
                lowStockProducts.add(productStockRow(product, stock, "Low stock"));
            }
        }

        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("lowStockThreshold", threshold);
        inventory.put("outOfStockCount", outOfStockProducts.size());
        inventory.put("lowStockCount", lowStockProducts.size());
        inventory.put("outOfStockProducts", outOfStockProducts);
        inventory.put("lowStockProducts", lowStockProducts);
        return inventory;
    }

    private int totalActiveStock(Product product) {
        if (product == null || product.getVariants() == null || product.getVariants().isEmpty()) {
            return 0;
        }
        return product.getVariants().stream()
                .filter(variant -> variant != null && Boolean.TRUE.equals(variant.getIsActive()))
                .mapToInt(variant -> variant.getStock() == null ? 0 : variant.getStock())
                .sum();
    }

    private Map<String, Object> productStockRow(Product product, int stock, String reason) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productId", product.getId());
        row.put("productName", product.getName());
        row.put("stock", stock);
        row.put("reason", reason);
        row.put("imageUrl", product.getImageUrl());
        row.put("storeName", product.getStore() != null ? product.getStore().getName() : null);
        row.put("categoryName", product.getCategory() != null ? product.getCategory().getName() : null);
        row.put("subCategoryName", product.getSubCategory() != null ? product.getSubCategory().getName() : null);
        row.put("active", Boolean.TRUE.equals(product.getIsActive()));
        return row;
    }
    private int resolveDays(String period) {
        if (period == null || period.isBlank()) {
            return 7;
        }
        String p = period.trim().toLowerCase(Locale.ROOT);
        return switch (p) {
            case "today" -> 1;
            case "7d", "week" -> 7;
            case "14d", "2w" -> 14;
            case "30d", "month" -> 30;
            default -> 7;
        };
    }

    private static final class BarAccumulator {
        long orders = 0L;
        BigDecimal revenue = BigDecimal.ZERO;
    }
}

