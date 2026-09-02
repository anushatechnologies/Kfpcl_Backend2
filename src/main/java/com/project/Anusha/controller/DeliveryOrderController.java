package com.project.Anusha.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.OrderItem;
import com.project.Anusha.repository.OrderRepository;
import com.project.Anusha.service.DeliveryOrderService;

@RestController
@RequestMapping("/api/delivery-orders")
@CrossOrigin(origins = "*")
public class DeliveryOrderController {

    @Autowired
    private DeliveryOrderService deliveryOrderService;

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Build a flat map for a single DeliveryOrder that includes:
     * – all core delivery fields
     * – store details (name, phone, lat/lng)
     * – items fetched from the linked Order (matched by orderNumber)
     */
    private Map<String, Object> buildOrderDetail(DeliveryOrder order) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", order.getId());
        map.put("orderNumber", order.getOrderNumber());
        map.put("customerName", order.getCustomerName());
        map.put("customerPhone", order.getCustomerPhone());
        map.put("pickupAddress", order.getPickupAddress());
        map.put("deliveryAddress", order.getDeliveryAddress());
        map.put("orderAmount", order.getOrderAmount());
        map.put("grandTotal", order.getOrderAmount());
        map.put("deliveryFee", order.getDeliveryFee());
        map.put("distanceKm", order.getDistanceKm());
        map.put("pickupLatitude", order.getPickupLatitude());
        map.put("pickupLongitude", order.getPickupLongitude());
        map.put("deliveryLatitude", order.getDeliveryLatitude());
        map.put("deliveryLongitude", order.getDeliveryLongitude());
        map.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        map.put("paymentType", order.getPaymentType() != null ? order.getPaymentType().name() : null);
        map.put("isPaidOnline", order.isPaidOnline());
        map.put("deliveryOtp", order.getDeliveryOtp());
        map.put("deliveryOtpExpiry", order.getDeliveryOtpExpiry());
        map.put("assignedAt", order.getAssignedAt());
        map.put("acceptedAt", order.getAcceptedAt());
        map.put("pickedUpAt", order.getPickedUpAt());
        map.put("deliveredAt", order.getDeliveredAt());
        map.put("deliveryOtpVerifiedAt", order.getDeliveryOtpVerifiedAt());
        map.put("deliveryPhotoUrl", order.getDeliveryPhotoUrl());
        map.put("createdAt", order.getCreatedAt());
        map.put("updatedAt", order.getUpdatedAt());

        // Store details
        if (order.getStore() != null) {
            Map<String, Object> storeMap = new HashMap<>();
            storeMap.put("id", order.getStore().getId());
            storeMap.put("name", order.getStore().getName());
            storeMap.put("address", order.getStore().getAddress());
            storeMap.put("phoneNumber", order.getStore().getPhoneNumber());
            storeMap.put("latitude", order.getStore().getLatitude());
            storeMap.put("longitude", order.getStore().getLongitude());
            map.put("store", storeMap);
            map.put("storeName", order.getStore().getName());
            map.put("storePhone", order.getStore().getPhoneNumber());
        }

        // Items from the linked customer Order (same orderNumber)
        List<Map<String, Object>> itemsList = new ArrayList<>();
        orderRepository.findByOrderNumber(order.getOrderNumber()).ifPresent(customerOrder -> {
            for (OrderItem item : customerOrder.getItems()) {
                Map<String, Object> i = new HashMap<>();
                i.put("name", item.getProductName());
                i.put("variantName", item.getVariantName());
                i.put("quantity", item.getQuantity());
                i.put("price", item.getUnitPrice());
                i.put("totalPrice", item.getTotalPrice());
                i.put("imageUrl", item.getImageUrl());
                itemsList.add(i);
            }
        });
        map.put("items", itemsList);
        map.put("itemsCount", itemsList.size());

        return map;
    }

    /**
     * Get order by ID — includes store details and order items from the linked Order.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderById(@PathVariable Long id) {
        try {
            DeliveryOrder order = deliveryOrderService.getOrderById(id);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("order", buildOrderDetail(order));

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch order: " + e.getMessage()));
        }
    }

    /**
     * Get order by order number — includes store details and order items from the linked Order.
     */
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<?> getOrderByOrderNumber(@PathVariable String orderNumber) {
        try {
            return deliveryOrderService.getOrderByOrderNumber(orderNumber)
                .map(order -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("order", buildOrderDetail(order));
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Order not found")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch order: " + e.getMessage()));
        }
    }

    /**
     * Get orders for delivery person
     */
    @GetMapping("/delivery-person/{deliveryPersonId}")
    public ResponseEntity<?> getOrdersForDeliveryPerson(@PathVariable Long deliveryPersonId) {
        try {
            List<DeliveryOrder> orders = deliveryOrderService.getOrdersForDeliveryPerson(deliveryPersonId);
            List<Map<String, Object>> payload = orders.stream()
                    .map(this::buildOrderDetail)
                    .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orders", payload);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch orders: " + e.getMessage()));
        }
    }

    /**
     * Get active orders for delivery person
     */
    @GetMapping("/delivery-person/{deliveryPersonId}/active")
    public ResponseEntity<?> getActiveOrdersForDeliveryPerson(@PathVariable Long deliveryPersonId) {
        try {
            List<DeliveryOrder> orders = deliveryOrderService.getActiveOrdersForDeliveryPerson(deliveryPersonId);
            List<Map<String, Object>> payload = orders.stream()
                    .map(this::buildOrderDetail)
                    .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("activeOrders", payload);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch active orders: " + e.getMessage()));
        }
    }

    /**
     * Get completed orders for delivery person
     */
    @GetMapping("/delivery-person/{deliveryPersonId}/completed")
    public ResponseEntity<?> getCompletedOrdersForDeliveryPerson(@PathVariable Long deliveryPersonId) {
        try {
            List<DeliveryOrder> orders = deliveryOrderService.getCompletedOrdersForDeliveryPerson(deliveryPersonId);
            List<Map<String, Object>> payload = orders.stream()
                    .map(this::buildOrderDetail)
                    .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("completedOrders", payload);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch completed orders: " + e.getMessage()));
        }
    }

    /**
     * Get recent orders for delivery person
     */
    @GetMapping("/delivery-person/{deliveryPersonId}/recent")
    public ResponseEntity<?> getRecentOrdersForDeliveryPerson(
            @PathVariable Long deliveryPersonId,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<DeliveryOrder> orders = deliveryOrderService.getRecentOrdersForDeliveryPerson(deliveryPersonId, limit);
            List<Map<String, Object>> payload = orders.stream()
                    .map(this::buildOrderDetail)
                    .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("recentOrders", payload);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch recent orders: " + e.getMessage()));
        }
    }

    /**
     * Verify pickup OTP
     */
    @PostMapping("/verify-pickup-otp")
    public ResponseEntity<?> verifyPickupOtp(@RequestBody Map<String, String> request) {
        try {
            String orderNumber = request.get("orderNumber");
            String otp = request.get("otp");

            if (orderNumber == null || otp == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Order number and OTP are required"));
            }

            boolean isValid = deliveryOrderService.verifyPickupOtp(orderNumber, otp);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("valid", isValid);
            response.put("message", isValid ? "OTP verified successfully" : "Invalid or expired OTP");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "OTP verification failed: " + e.getMessage()));
        }
    }

    /**
     * Confirm order pickup
     */
    @PostMapping("/confirm-pickup")
    public ResponseEntity<?> confirmOrderPickup(@RequestBody Map<String, String> request) {
        try {
            String orderNumber = request.get("orderNumber");
            String otp = request.get("otp");

            if (orderNumber == null || otp == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Order number and OTP are required"));
            }

            DeliveryOrder order = deliveryOrderService.confirmOrderPickup(orderNumber, otp);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Order picked up successfully");
            response.put("order", order);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to confirm pickup: " + e.getMessage()));
        }
    }

    /**
     * Verify delivery OTP
     */
    @PostMapping("/verify-delivery-otp")
    public ResponseEntity<?> verifyDeliveryOtp(@RequestBody Map<String, String> request) {
        try {
            String orderNumber = request.get("orderNumber");
            String otp = request.get("otp");

            if (orderNumber == null || otp == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Order number and OTP are required"));
            }

            boolean isValid = deliveryOrderService.verifyDeliveryOtp(orderNumber, otp);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("valid", isValid);
            response.put("message", isValid ? "OTP verified successfully" : "Invalid or expired OTP");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "OTP verification failed: " + e.getMessage()));
        }
    }

    /**
     * Confirm order delivery
     */
    @PostMapping("/confirm-delivery")
    public ResponseEntity<?> confirmOrderDelivery(@RequestBody Map<String, String> request) {
        try {
            String orderNumber = request.get("orderNumber");
            String otp = request.get("otp");

            if (orderNumber == null || otp == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Order number and OTP are required"));
            }

            DeliveryOrder order = deliveryOrderService.confirmOrderDelivery(orderNumber, otp);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Order delivered successfully");
            response.put("order", order);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to confirm delivery: " + e.getMessage()));
        }
    }

    /**
     * Get order statistics for delivery person
     */
    @GetMapping("/delivery-person/{deliveryPersonId}/statistics")
    public ResponseEntity<?> getOrderStatisticsForDeliveryPerson(@PathVariable Long deliveryPersonId) {
        try {
            Map<String, Object> statistics = deliveryOrderService.getOrderStatisticsForDeliveryPerson(deliveryPersonId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", statistics);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to fetch order statistics: " + e.getMessage()));
        }
    }

    /**
     * Cancel order
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId, @RequestBody Map<String, String> request) {
        try {
            String reason = request.get("reason");
            
            DeliveryOrder order = deliveryOrderService.cancelOrder(orderId, reason);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Order cancelled successfully");
            response.put("order", order);
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to cancel order: " + e.getMessage()));
        }
    }

    /**
     * Create new delivery order
     */
    @PostMapping
    public ResponseEntity<?> createDeliveryOrder(@RequestBody DeliveryOrder order) {
        try {
            DeliveryOrder createdOrder = deliveryOrderService.createDeliveryOrder(order);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Order created successfully");
            response.put("order", createdOrder);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to create order: " + e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELIVERY PERSON ACCEPT / REJECT
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Delivery person accepts assigned order.
     * POST /api/delivery-orders/{orderNumber}/accept
     * Body: { "deliveryPersonId": 5 }
     */
    @PostMapping("/{orderNumber}/accept")
    public ResponseEntity<?> acceptOrder(@PathVariable String orderNumber,
                                         @RequestBody Map<String, Object> request) {
        try {
            Long deliveryPersonId = ((Number) request.get("deliveryPersonId")).longValue();
            DeliveryOrder order = deliveryOrderService.acceptOrder(orderNumber, deliveryPersonId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order accepted. Head to the store!",
                    "orderNumber", order.getOrderNumber(),
                    "status", order.getStatus().name(),
                    "pickupAddress", order.getPickupAddress()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to accept order: " + e.getMessage()));
        }
    }

    /**
     * Delivery person rejects assigned order.
     * POST /api/delivery-orders/{orderNumber}/reject
     * Body: { "deliveryPersonId": 5, "reason": "Too far" }
     */
    @PostMapping("/{orderNumber}/reject")
    public ResponseEntity<?> rejectOrder(@PathVariable String orderNumber,
                                          @RequestBody Map<String, Object> request) {
        try {
            Long deliveryPersonId = ((Number) request.get("deliveryPersonId")).longValue();
            String reason = (String) request.getOrDefault("reason", "No reason provided");

            DeliveryOrder order = deliveryOrderService.rejectOrder(orderNumber, deliveryPersonId, reason);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Order rejected.",
                    "orderNumber", order.getOrderNumber(),
                    "status", order.getStatus().name()
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reject order: " + e.getMessage()));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LIVE GPS LOCATION UPDATE (called every ~5 seconds by Delivery App)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Push live GPS location to Firebase RTDB.
     * POST /api/delivery-orders/{orderNumber}/update-location
     * Body: { "deliveryPersonId": 5, "lat": 19.0760, "lng": 72.8777 }
     */
    @PostMapping("/{orderNumber}/update-location")
    public ResponseEntity<?> updateLocation(@PathVariable String orderNumber,
                                             @RequestBody Map<String, Object> request) {
        try {
            Long deliveryPersonId = ((Number) request.get("deliveryPersonId")).longValue();
            double lat = ((Number) request.get("lat")).doubleValue();
            double lng = ((Number) request.get("lng")).doubleValue();

            deliveryOrderService.updateLiveLocation(orderNumber, deliveryPersonId, lat, lng);

            return ResponseEntity.ok(Map.of("success", true, "message", "Location updated"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to update location: " + e.getMessage()));
        }
    }
}
