package com.project.Anusha.service;

import com.project.Anusha.dto.AdminBulkProductActionRequest;
import com.project.Anusha.exception.ResourceNotFoundException;
import com.project.Anusha.model.*;
import com.project.Anusha.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminOpsService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final UserLogRepository userLogRepository;
    private final UserRepository userRepository;
    private final BannerRepository bannerRepository;
    private final CouponRepository couponRepository;
    private final AppNotificationRepository appNotificationRepository;
    private final UserLogService userLogService;

    @Transactional(readOnly = true)
    public Map<String, Object> getCommandCenter(String period, int lowStockThreshold, int delayedHours) {
        List<Product> products = productRepository.findAll();
        List<Order> orders = orderRepository.findAllWithCustomerAndAddress();
        List<Customer> customers = customerRepository.findAll();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = resolvePeriodStart(period, now);

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> catalogHealth = buildCatalogHealth(products, lowStockThreshold);
        Map<String, Object> catalogSummary = castMap(catalogHealth.get("summary"));
        response.put("generatedAt", now);
        response.put("catalogHealth", catalogHealth);
        response.put("inventoryAlerts", catalogHealth.get("inventoryAlerts"));
        response.put("outOfStockProducts", catalogHealth.get("outOfStockProducts"));
        response.put("lowStockProducts", catalogHealth.get("lowStockProducts"));
        response.put("outOfStockCount", catalogSummary.get("outOfStockCount"));
        response.put("lowStockCount", catalogSummary.get("lowStockCount"));
        response.put("orderExceptions", buildOrderExceptions(orders, delayedHours, now));
        response.put("salesAnalytics", buildSalesAnalytics(products, orders, period, periodStart, now));
        response.put("customerIssues", buildCustomerIssues(customers, orders));
        response.put("auditTrail", buildAuditTrail());
        response.put("storePerformance", buildStorePerformance(orders));
        response.put("marketingSummary", buildMarketingSummary());
        return response;
    }

    public Map<String, Object> applyBulkProductAction(AdminBulkProductActionRequest request, String actorEmail) {
        if (request.getProductIds() == null || request.getProductIds().isEmpty()) {
            throw new IllegalArgumentException("At least one product id is required");
        }
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase(Locale.ROOT);
        if (action.isEmpty()) {
            throw new IllegalArgumentException("Action is required");
        }

        List<Product> products = productRepository.findAllById(request.getProductIds());
        if (products.isEmpty()) {
            throw new ResourceNotFoundException("No matching products found");
        }

        Category category = request.getCategoryId() != null
                ? categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.getCategoryId()))
                : null;
        SubCategory subCategory = request.getSubCategoryId() != null
                ? subCategoryRepository.findById(request.getSubCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("SubCategory not found with id: " + request.getSubCategoryId()))
                : null;
        Store store = request.getStoreId() != null
                ? storeRepository.findById(request.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + request.getStoreId()))
                : null;

        int touched = 0;
        for (Product product : products) {
            boolean changed = applyActionToProduct(product, action, category, subCategory, store, request);
            if (changed) {
                touched++;
            }
        }

        productRepository.saveAll(products);

        String details = String.format(
                Locale.ROOT,
                "Bulk product action=%s, touched=%d, requested=%d, ids=%s",
                action,
                touched,
                request.getProductIds().size(),
                request.getProductIds()
        );
        User actor = resolveActor(actorEmail);
        userLogService.log(actor != null ? actor.getId() : null, "ADMIN", "BULK_PRODUCT_ACTION", details, null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("action", action);
        response.put("touchedProducts", touched);
        response.put("requestedProducts", request.getProductIds().size());
        response.put("message", "Bulk action applied successfully");
        return response;
    }

    private boolean applyActionToProduct(
            Product product,
            String action,
            Category category,
            SubCategory subCategory,
            Store store,
            AdminBulkProductActionRequest request
    ) {
        switch (action) {
            case "ACTIVATE" -> {
                product.setIsActive(true);
                product.setDeleted(false);
                reactivateVariants(product);
                return true;
            }
            case "DEACTIVATE" -> {
                product.setIsActive(false);
                deactivateVariants(product);
                return true;
            }
            case "MARK_TRENDING" -> {
                product.setIsTrending(true);
                return true;
            }
            case "UNMARK_TRENDING" -> {
                product.setIsTrending(false);
                return true;
            }
            case "MARK_BESTSELLER" -> {
                product.setBestSeller(true);
                return true;
            }
            case "UNMARK_BESTSELLER" -> {
                product.setBestSeller(false);
                return true;
            }
            case "ASSIGN_STORE" -> {
                if (store == null) {
                    throw new IllegalArgumentException("storeId is required for ASSIGN_STORE");
                }
                product.setStore(store);
                return true;
            }
            case "ASSIGN_CATEGORY" -> {
                if (category == null) {
                    throw new IllegalArgumentException("categoryId is required for ASSIGN_CATEGORY");
                }
                product.setCategory(category);
                return true;
            }
            case "ASSIGN_SUBCATEGORY" -> {
                if (subCategory == null) {
                    throw new IllegalArgumentException("subCategoryId is required for ASSIGN_SUBCATEGORY");
                }
                product.setSubCategory(subCategory);
                return true;
            }
            case "SET_DISPLAY_ORDER" -> {
                if (request.getDisplayOrder() == null) {
                    throw new IllegalArgumentException("displayOrder is required for SET_DISPLAY_ORDER");
                }
                product.setDisplayOrder(request.getDisplayOrder());
                return true;
            }
            case "SHIFT_DISPLAY_ORDER" -> {
                if (request.getDisplayOrderDelta() == null) {
                    throw new IllegalArgumentException("displayOrderDelta is required for SHIFT_DISPLAY_ORDER");
                }
                int current = product.getDisplayOrder() != null ? product.getDisplayOrder() : 0;
                product.setDisplayOrder(current + request.getDisplayOrderDelta());
                return true;
            }
            case "INCREASE_PRICE_PERCENT" -> {
                if (request.getPercentage() == null) {
                    throw new IllegalArgumentException("percentage is required for INCREASE_PRICE_PERCENT");
                }
                adjustVariantPrices(product, Math.abs(request.getPercentage()));
                return true;
            }
            case "DECREASE_PRICE_PERCENT" -> {
                if (request.getPercentage() == null) {
                    throw new IllegalArgumentException("percentage is required for DECREASE_PRICE_PERCENT");
                }
                adjustVariantPrices(product, -Math.abs(request.getPercentage()));
                return true;
            }
            case "SET_STOCK" -> {
                if (request.getStock() == null || request.getStock() < 0) {
                    throw new IllegalArgumentException("stock is required for SET_STOCK");
                }
                setVariantStock(product, request.getStock());
                return true;
            }
            default -> throw new IllegalArgumentException("Unsupported bulk action: " + action);
        }
    }

    private Map<String, Object> buildCatalogHealth(List<Product> products, int lowStockThreshold) {
        List<Map<String, Object>> lowStockProducts = new ArrayList<>();
        List<Map<String, Object>> outOfStockProducts = new ArrayList<>();
        List<Map<String, Object>> missingImages = new ArrayList<>();
        List<Map<String, Object>> missingVariants = new ArrayList<>();
        List<Map<String, Object>> inactiveBestSellers = new ArrayList<>();

        for (Product product : products) {
            List<Variant> activeVariants = product.getVariants() == null
                    ? List.of()
                    : product.getVariants().stream().filter(v -> Boolean.TRUE.equals(v.getIsActive())).toList();

            int totalStock = activeVariants.stream()
                    .map(Variant::getStock)
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();

            if (activeVariants.isEmpty()) {
                missingVariants.add(simpleProductRow(product, totalStock, "No active variants"));
            }

            boolean hasMedia = (product.getImageUrl() != null && !product.getImageUrl().isBlank())
                    || (product.getImages() != null && product.getImages().stream().anyMatch(img -> img.getImageUrl() != null && !img.getImageUrl().isBlank()));
            if (!hasMedia) {
                missingImages.add(simpleProductRow(product, totalStock, "Missing primary and gallery images"));
            }

            if (totalStock <= 0) {
                outOfStockProducts.add(simpleProductRow(product, totalStock, "Out of stock"));
            } else if (totalStock < lowStockThreshold) {
                lowStockProducts.add(simpleProductRow(product, totalStock, "Low stock"));
            }

            if (Boolean.TRUE.equals(product.getBestSeller()) && !Boolean.TRUE.equals(product.getIsActive())) {
                inactiveBestSellers.add(simpleProductRow(product, totalStock, "Best seller is inactive"));
            }
        }

        Map<String, List<String>> duplicateNames = products.stream()
                .filter(product -> product.getName() != null && !product.getName().isBlank())
                .collect(Collectors.groupingBy(
                        product -> product.getName().trim().toLowerCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.mapping(product -> product.getName() + " / " + safeStoreName(product), Collectors.toList())
                ));

        Map<String, List<String>> duplicateSkus = products.stream()
                .flatMap(product -> product.getVariants() == null ? java.util.stream.Stream.empty() : product.getVariants().stream())
                .filter(variant -> variant.getSku() != null && !variant.getSku().isBlank())
                .collect(Collectors.groupingBy(
                        variant -> variant.getSku().trim().toLowerCase(Locale.ROOT),
                        LinkedHashMap::new,
                        Collectors.mapping(variant -> variant.getSku() + " / " + safeProductName(variant.getProduct()), Collectors.toList())
                ));

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalProducts", products.size());
        summary.put("activeProducts", products.stream().filter(product -> Boolean.TRUE.equals(product.getIsActive())).count());
        summary.put("inactiveProducts", products.stream().filter(product -> !Boolean.TRUE.equals(product.getIsActive())).count());
        summary.put("deletedProducts", products.stream().filter(product -> Boolean.TRUE.equals(product.getDeleted())).count());
        summary.put("trendingProducts", products.stream().filter(product -> Boolean.TRUE.equals(product.getIsTrending())).count());
        summary.put("bestSellerProducts", products.stream().filter(product -> Boolean.TRUE.equals(product.getBestSeller())).count());
        summary.put("lowStockCount", lowStockProducts.size());
        summary.put("outOfStockCount", outOfStockProducts.size());
        summary.put("missingImageCount", missingImages.size());
        summary.put("missingVariantCount", missingVariants.size());
        summary.put("inactiveBestSellerCount", inactiveBestSellers.size());
        response.put("summary", summary);
        response.put("inventoryAlerts", concatRows(outOfStockProducts, lowStockProducts));
        response.put("lowStockProducts", lowStockProducts);
        response.put("outOfStockProducts", outOfStockProducts);
        response.put("missingImages", missingImages.stream().limit(12).toList());
        response.put("missingVariants", missingVariants.stream().limit(12).toList());
        response.put("inactiveBestSellers", inactiveBestSellers.stream().limit(12).toList());
        response.put("duplicateNames", duplicateNames.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .limit(10)
                .map(entry -> Map.of("key", entry.getKey(), "items", entry.getValue()))
                .toList());
        response.put("duplicateSkus", duplicateSkus.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .limit(10)
                .map(entry -> Map.of("key", entry.getKey(), "items", entry.getValue()))
                .toList());
        return response;
    }

    private Map<String, Object> buildOrderExceptions(List<Order> orders, int delayedHours, LocalDateTime now) {
        LocalDateTime delayedThreshold = now.minusHours(Math.max(delayedHours, 1));
        List<Map<String, Object>> failedPayments = new ArrayList<>();
        List<Map<String, Object>> delayedDeliveries = new ArrayList<>();
        List<Map<String, Object>> cancelledOrders = new ArrayList<>();
        List<Map<String, Object>> refundCandidates = new ArrayList<>();
        List<Map<String, Object>> suspiciousOrders = new ArrayList<>();

        Map<Long, Long> cancelledByCustomer = orders.stream()
                .filter(order -> "cancelled".equalsIgnoreCase(order.getOrderStatus()))
                .filter(order -> order.getCustomer() != null && order.getCustomer().getId() != null)
                .collect(Collectors.groupingBy(order -> order.getCustomer().getId(), Collectors.counting()));

        Map<String, Long> addressUsage = orders.stream()
                .map(this::normalizeAddressKey)
                .filter(key -> !key.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        for (Order order : orders) {
            if ("failed".equalsIgnoreCase(order.getPaymentStatus())) {
                failedPayments.add(simpleOrderRow(order, "Payment failed"));
            }

            boolean pendingOrActive = !"delivered".equalsIgnoreCase(order.getOrderStatus())
                    && !"cancelled".equalsIgnoreCase(order.getOrderStatus())
                    && !"rejected".equalsIgnoreCase(order.getOrderStatus());
            if (pendingOrActive && order.getPlacedAt() != null && order.getPlacedAt().isBefore(delayedThreshold)) {
                delayedDeliveries.add(simpleOrderRow(
                        order,
                        "Delayed by " + ChronoUnit.HOURS.between(order.getPlacedAt(), now) + "h"
                ));
            }

            if ("cancelled".equalsIgnoreCase(order.getOrderStatus())) {
                cancelledOrders.add(simpleOrderRow(order, coalesce(order.getCancellationReason(), "Cancelled")));
            }

            boolean paidOnline = "paid".equalsIgnoreCase(order.getPaymentStatus())
                    && order.getPaymentMethod() != null
                    && order.getPaymentMethod().toUpperCase(Locale.ROOT).contains("ONLINE");
            if ("cancelled".equalsIgnoreCase(order.getOrderStatus()) && paidOnline) {
                refundCandidates.add(simpleOrderRow(order, "Cancelled paid online order"));
            }

            long customerCancelledCount = order.getCustomer() != null && order.getCustomer().getId() != null
                    ? cancelledByCustomer.getOrDefault(order.getCustomer().getId(), 0L)
                    : 0L;
            long duplicateAddressCount = addressUsage.getOrDefault(normalizeAddressKey(order), 0L);
            if (customerCancelledCount >= 2 || duplicateAddressCount >= 3) {
                suspiciousOrders.add(simpleOrderRow(
                        order,
                        "Flags: customer cancellations=" + customerCancelledCount + ", shared address=" + duplicateAddressCount
                ));
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("failedPayments", failedPayments.size());
        summary.put("delayedDeliveries", delayedDeliveries.size());
        summary.put("cancelledOrders", cancelledOrders.size());
        summary.put("refundCandidates", refundCandidates.size());
        summary.put("suspiciousOrders", suspiciousOrders.size());
        response.put("summary", summary);
        response.put("failedPayments", failedPayments.stream().limit(10).toList());
        response.put("delayedDeliveries", delayedDeliveries.stream().limit(10).toList());
        response.put("cancelledOrders", cancelledOrders.stream().limit(10).toList());
        response.put("refundCandidates", refundCandidates.stream().limit(10).toList());
        response.put("suspiciousOrders", suspiciousOrders.stream().limit(10).toList());
        return response;
    }

    private Map<String, Object> buildSalesAnalytics(
            List<Product> products,
            List<Order> orders,
            String period,
            LocalDateTime periodStart,
            LocalDateTime now
    ) {
        List<Order> periodOrders = orders.stream()
                .filter(order -> order.getPlacedAt() != null && !order.getPlacedAt().isBefore(periodStart))
                .toList();
        List<Order> deliveredOrders = periodOrders.stream()
                .filter(order -> "delivered".equalsIgnoreCase(order.getOrderStatus()))
                .toList();

        BigDecimal revenue = deliveredOrders.stream()
                .map(Order::getGrandTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, ProductSalesAccumulator> productSales = new LinkedHashMap<>();
        Map<Long, StoreSalesAccumulator> storeSales = new LinkedHashMap<>();
        Map<String, BigDecimal> categoryRevenue = new LinkedHashMap<>();

        for (Order order : deliveredOrders) {
            if (order.getItems() == null) {
                continue;
            }

            for (OrderItem item : order.getItems()) {
                Variant variant = item.getVariant();
                Product product = variant != null ? variant.getProduct() : null;
                if (product == null) {
                    continue;
                }

                BigDecimal lineRevenue = item.getTotalPrice() != null
                        ? item.getTotalPrice()
                        : BigDecimal.valueOf(item.getQuantity() != null ? item.getQuantity() : 0)
                        .multiply(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO);

                productSales.computeIfAbsent(
                        product.getId(),
                        ignored -> new ProductSalesAccumulator(product)
                ).add(item.getQuantity() != null ? item.getQuantity() : 0, lineRevenue);

                Store store = product.getStore();
                if (store != null && store.getId() != null) {
                    storeSales.computeIfAbsent(
                            store.getId(),
                            ignored -> new StoreSalesAccumulator(store)
                    ).add(lineRevenue);
                }

                String categoryName = product.getCategory() != null && product.getCategory().getName() != null
                        ? product.getCategory().getName()
                        : "Uncategorized";
                categoryRevenue.merge(categoryName, lineRevenue, BigDecimal::add);
            }
        }

        long cancelledCount = periodOrders.stream()
                .filter(order -> "cancelled".equalsIgnoreCase(order.getOrderStatus()) || "rejected".equalsIgnoreCase(order.getOrderStatus()))
                .count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("period", period == null || period.isBlank() ? "month" : period);
        response.put("from", periodStart);
        response.put("to", now);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orders", periodOrders.size());
        summary.put("deliveredOrders", deliveredOrders.size());
        summary.put("cancelledOrders", cancelledCount);
        summary.put("revenue", revenue);
        summary.put(
                "averageOrderValue",
                deliveredOrders.isEmpty()
                        ? BigDecimal.ZERO
                        : revenue.divide(BigDecimal.valueOf(deliveredOrders.size()), 2, RoundingMode.HALF_UP)
        );
        summary.put("activeProducts", products.stream().filter(product -> Boolean.TRUE.equals(product.getIsActive())).count());
        response.put("summary", summary);
        response.put("topProducts", productSales.values().stream()
                .sorted(Comparator.comparing(ProductSalesAccumulator::getRevenue).reversed())
                .limit(8)
                .map(ProductSalesAccumulator::toMap)
                .toList());
        response.put("topStores", storeSales.values().stream()
                .sorted(Comparator.comparing(StoreSalesAccumulator::getRevenue).reversed())
                .limit(8)
                .map(StoreSalesAccumulator::toMap)
                .toList());
        response.put("revenueByCategory", categoryRevenue.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .map(entry -> Map.of("category", entry.getKey(), "revenue", entry.getValue()))
                .toList());
        return response;
    }

    private Map<String, Object> buildCustomerIssues(List<Customer> customers, List<Order> orders) {
        Map<Long, List<Order>> ordersByCustomer = orders.stream()
                .filter(order -> order.getCustomer() != null && order.getCustomer().getId() != null)
                .collect(Collectors.groupingBy(order -> order.getCustomer().getId()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Customer customer : customers) {
            List<Order> customerOrders = ordersByCustomer.getOrDefault(customer.getId(), List.of());
            long cancelled = customerOrders.stream()
                    .filter(order -> "cancelled".equalsIgnoreCase(order.getOrderStatus()) || "rejected".equalsIgnoreCase(order.getOrderStatus()))
                    .count();
            long delivered = customerOrders.stream()
                    .filter(order -> "delivered".equalsIgnoreCase(order.getOrderStatus()))
                    .count();
            boolean hasIssue = cancelled > 0 || !Boolean.TRUE.equals(customer.getIsActive());
            if (!hasIssue) {
                continue;
            }
            LocalDateTime lastOrderAt = customerOrders.stream()
                    .map(Order::getPlacedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerId", customer.getId());
            row.put("name", coalesce(customer.getUsername(), customer.getName()));
            row.put("phone", coalesce(customer.getPhoneNumber(), ""));
            row.put("active", Boolean.TRUE.equals(customer.getIsActive()));
            row.put("totalOrders", customerOrders.size());
            row.put("deliveredOrders", delivered);
            row.put("cancelledOrders", cancelled);
            row.put("lastOrderAt", lastOrderAt);
            row.put("issueLabel", !Boolean.TRUE.equals(customer.getIsActive()) ? "Blocked customer" : "Repeat cancellations");
            rows.add(row);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customersWithIssues", rows.size());
        summary.put("blockedCustomers", customers.stream().filter(customer -> !Boolean.TRUE.equals(customer.getIsActive())).count());
        summary.put("repeatCancellationCustomers", rows.stream().filter(row -> ((Number) row.get("cancelledOrders")).longValue() >= 2).count());
        response.put("summary", summary);
        response.put("rows", rows.stream()
                .sorted(Comparator.comparing((Map<String, Object> row) -> ((Number) row.get("cancelledOrders")).longValue()).reversed())
                .limit(12)
                .toList());
        return response;
    }

    private Map<String, Object> buildAuditTrail() {
        List<UserLog> logs = new ArrayList<>(userLogRepository.findAll());
        logs.sort(Comparator.comparing(UserLog::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEntries", logs.size());
        summary.put("adminEntries", logs.stream().filter(log -> "ADMIN".equalsIgnoreCase(log.getUserRole())).count());
        response.put("summary", summary);
        response.put("entries", logs.stream().limit(20).map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("userId", log.getUserId());
            row.put("userRole", coalesce(log.getUserRole(), ""));
            row.put("action", coalesce(log.getAction(), ""));
            row.put("details", coalesce(log.getDetails(), ""));
            row.put("ipAddress", coalesce(log.getIpAddress(), ""));
            row.put("timestamp", log.getTimestamp());
            return row;
        }).toList());
        return response;
    }

    private Map<String, Object> buildStorePerformance(List<Order> orders) {
        Map<Long, StoreSalesAccumulator> stores = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.getItems() == null) {
                continue;
            }
            for (OrderItem item : order.getItems()) {
                Variant variant = item.getVariant();
                Product product = variant != null ? variant.getProduct() : null;
                Store store = product != null ? product.getStore() : null;
                if (store == null || store.getId() == null) {
                    continue;
                }
                BigDecimal lineRevenue = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
                StoreSalesAccumulator accumulator = stores.computeIfAbsent(store.getId(), ignored -> new StoreSalesAccumulator(store));
                accumulator.add(lineRevenue);
                accumulator.incrementOrders(order.getId(), order.getOrderStatus());
            }
        }

        return Map.of(
                "rows", stores.values().stream()
                        .sorted(Comparator.comparing(StoreSalesAccumulator::getRevenue).reversed())
                        .limit(12)
                        .map(StoreSalesAccumulator::toMap)
                        .toList()
        );
    }

    private Map<String, Object> buildMarketingSummary() {
        return Map.of(
                "bannerCount", bannerRepository.count(),
                "couponCount", couponRepository.count(),
                "notificationCount", appNotificationRepository.count()
        );
    }

    private Map<String, Object> simpleProductRow(Product product, int totalStock, String issue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productId", product.getId());
        row.put("name", safeProductName(product));
        row.put("storeName", safeStoreName(product));
        row.put("categoryName", product.getCategory() != null ? product.getCategory().getName() : "");
        row.put("stock", totalStock);
        row.put("active", Boolean.TRUE.equals(product.getIsActive()));
        row.put("imageUrl", product.getImageUrl());
        row.put("issue", issue);
        return row;
    }

    private Map<String, Object> simpleOrderRow(Order order, String issue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderId", order.getId());
        row.put("orderNumber", coalesce(order.getOrderNumber(), ""));
        row.put("customerName", order.getCustomer() != null ? coalesce(order.getCustomer().getName(), "") : "");
        row.put("status", coalesce(order.getOrderStatus(), ""));
        row.put("paymentStatus", coalesce(order.getPaymentStatus(), ""));
        row.put("paymentMethod", coalesce(order.getPaymentMethod(), ""));
        row.put("grandTotal", order.getGrandTotal() != null ? order.getGrandTotal() : BigDecimal.ZERO);
        row.put("placedAt", order.getPlacedAt());
        row.put("issue", issue);
        return row;
    }

    private List<Map<String, Object>> concatRows(List<Map<String, Object>> first, List<Map<String, Object>> second) {
        List<Map<String, Object>> rows = new ArrayList<>(first);
        rows.addAll(second);
        return rows;
    }

    private void reactivateVariants(Product product) {
        if (product.getVariants() == null) {
            return;
        }
        product.getVariants().forEach(variant -> variant.setIsActive(true));
    }

    private void deactivateVariants(Product product) {
        if (product.getVariants() == null) {
            return;
        }
        product.getVariants().forEach(variant -> variant.setIsActive(false));
    }

    private void adjustVariantPrices(Product product, double percentageDelta) {
        if (product.getVariants() == null) {
            return;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(BigDecimal.valueOf(percentageDelta).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        for (Variant variant : product.getVariants()) {
            if (variant.getPrice() != null) {
                variant.setPrice(
                        BigDecimal.valueOf(variant.getPrice())
                                .multiply(multiplier)
                                .max(BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue()
                );
            }
            if (variant.getDiscountPrice() != null) {
                variant.setDiscountPrice(
                        BigDecimal.valueOf(variant.getDiscountPrice())
                                .multiply(multiplier)
                                .max(BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue()
                );
            }
        }
    }

    private void setVariantStock(Product product, int stock) {
        if (product.getVariants() == null) {
            return;
        }
        for (Variant variant : product.getVariants()) {
            if (Boolean.TRUE.equals(variant.getIsActive())) {
                variant.setStock(stock);
            }
        }
    }

    private User resolveActor(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(actorEmail).orElse(null);
    }

    private String normalizeAddressKey(Order order) {
        if (order.getAddress() == null) {
            return "";
        }
        Address address = order.getAddress();
        return String.join("|",
                coalesce(address.getAddressLine1(), "").trim().toLowerCase(Locale.ROOT),
                coalesce(address.getPostalCode(), "").trim().toLowerCase(Locale.ROOT),
                coalesce(address.getContactPhone(), "").trim().toLowerCase(Locale.ROOT)
        );
    }

    private LocalDateTime resolvePeriodStart(String period, LocalDateTime now) {
        String normalized = period == null || period.isBlank() ? "month" : period.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "today", "day" -> now.truncatedTo(ChronoUnit.DAYS);
            case "week" -> now.minusDays(6).truncatedTo(ChronoUnit.DAYS);
            case "year" -> now.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
            default -> now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        };
    }

    private String safeStoreName(Product product) {
        return product.getStore() != null && product.getStore().getName() != null
                ? product.getStore().getName()
                : "Unassigned";
    }

    private String safeProductName(Product product) {
        return product != null && product.getName() != null ? product.getName() : "Unnamed product";
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static final class ProductSalesAccumulator {
        private final Product product;
        private long unitsSold;
        private BigDecimal revenue = BigDecimal.ZERO;

        private ProductSalesAccumulator(Product product) {
            this.product = product;
        }

        private void add(int quantity, BigDecimal lineRevenue) {
            this.unitsSold += quantity;
            this.revenue = this.revenue.add(lineRevenue != null ? lineRevenue : BigDecimal.ZERO);
        }

        private BigDecimal getRevenue() {
            return revenue;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", product.getId());
            row.put("productName", product.getName());
            row.put("storeName", product.getStore() != null ? product.getStore().getName() : "");
            row.put("unitsSold", unitsSold);
            row.put("revenue", revenue);
            row.put("imageUrl", product.getImageUrl());
            return row;
        }
    }

    private static final class StoreSalesAccumulator {
        private final Store store;
        private BigDecimal revenue = BigDecimal.ZERO;
        private final Set<Long> orderIds = new HashSet<>();
        private long deliveredOrders;
        private long cancelledOrders;

        private StoreSalesAccumulator(Store store) {
            this.store = store;
        }

        private void add(BigDecimal lineRevenue) {
            this.revenue = this.revenue.add(lineRevenue != null ? lineRevenue : BigDecimal.ZERO);
        }

        private void incrementOrders(Long orderId, String status) {
            if (orderId != null) {
                orderIds.add(orderId);
            }
            if ("delivered".equalsIgnoreCase(status)) {
                deliveredOrders++;
            }
            if ("cancelled".equalsIgnoreCase(status) || "rejected".equalsIgnoreCase(status)) {
                cancelledOrders++;
            }
        }

        private BigDecimal getRevenue() {
            return revenue;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("storeId", store.getId());
            row.put("storeName", store.getName());
            row.put("revenue", revenue);
            row.put("orderCount", orderIds.size());
            row.put("deliveredOrders", deliveredOrders);
            row.put("cancelledOrders", cancelledOrders);
            row.put("active", Boolean.TRUE.equals(store.getActive()));
            row.put("imageUrl", store.getImageUrl());
            return row;
        }
    }
}
