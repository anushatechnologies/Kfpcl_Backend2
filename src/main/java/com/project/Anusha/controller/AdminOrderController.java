package com.project.Anusha.controller;

import com.project.Anusha.dto.AdminOrderDetailDto;
import com.project.Anusha.dto.AdminOrderSummaryDto;
import com.project.Anusha.dto.OrderResponse;
import com.project.Anusha.dto.PaymentRefundRequest;
import com.project.Anusha.model.Address;
import com.project.Anusha.model.DeliveryBroadcast;
import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.Order;
import com.project.Anusha.model.OrderItem;
import com.project.Anusha.model.Store;
import com.project.Anusha.model.StoreSubOrder;
import com.project.Anusha.repository.DeliveryOrderRepository;
import com.project.Anusha.repository.StoreSubOrderRepository;
import com.project.Anusha.service.AdminService;
import com.project.Anusha.service.DeliveryOrderService;
import com.project.Anusha.service.GstInvoiceDetails;
import com.project.Anusha.service.OrderService;
import com.project.Anusha.service.PaymentService;
import com.project.Anusha.service.StoreOrderDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderService orderService;
    private final DeliveryOrderService deliveryOrderService;
    private final AdminService adminService;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final StoreSubOrderRepository storeSubOrderRepository;
    private final StoreOrderDispatchService storeOrderDispatchService;
    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<List<AdminOrderSummaryDto>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        List<AdminOrderSummaryDto> dtos = orders.stream().map(this::toSummaryDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<AdminOrderDetailDto> getOrderDetails(@PathVariable Long orderId) {
        Order order = orderService.getOrderByIdForAdmin(orderId);
        return ResponseEntity.ok(toDetailDto(order));
    }

    @PostMapping("/{orderId}/accept")
    public ResponseEntity<?> acceptOrder(@PathVariable Long orderId) {
        orderService.acceptOrder(orderId);
        return ResponseEntity.ok(Map.of("message", "Order accepted successfully", "orderId", orderId));
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<?> rejectOrder(@PathVariable Long orderId,
                                         @RequestBody Map<String, String> payload) {
        String reason = payload.getOrDefault("reason", "No reason provided");
        orderService.rejectOrder(orderId, reason);
        return ResponseEntity.ok(Map.of("message", "Order rejected successfully", "orderId", orderId));
    }

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<?> refundOrder(@PathVariable Long orderId,
                                         @RequestBody(required = false) PaymentRefundRequest request) {
        String reason = request != null ? request.getReason() : null;
        BigDecimal amount = request != null ? request.getAmount() : null;
        return ResponseEntity.ok(paymentService.processRefund(orderId, null, amount,
                reason != null && !reason.isBlank() ? reason : "Refund processed by admin"));
    }

    @GetMapping("/{orderId}/refund-status")
    public ResponseEntity<?> getRefundStatus(@PathVariable Long orderId) {
        return ResponseEntity.ok(paymentService.getRefundStatus(orderId, null));
    }

    /**
     * POST /api/admin/orders/test-whatsapp?phone=918019672244
     * Sends a test WhatsApp message to verify token + phone number ID are correct.
     */
    @PostMapping("/test-whatsapp")
    public ResponseEntity<?> testWhatsApp(@RequestParam String phone) {
        try {
            java.util.Map<String, Object> info = java.util.Map.of(
                "phoneNumberId", storeOrderDispatchService.getPhoneNumberId(),
                "tokenConfigured", storeOrderDispatchService.isTokenConfigured()
            );

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("messaging_product", "whatsapp");
            payload.put("to", phone);
            payload.put("type", "template");

            java.util.Map<String, Object> template = new java.util.HashMap<>();
            template.put("name", "order_notification_v1");
            java.util.Map<String, String> lang = new java.util.HashMap<>();
            lang.put("code", "en_US");
            template.put("language", lang);

            java.util.List<java.util.Map<String, String>> params = java.util.List.of(
                java.util.Map.of("type", "text", "text", "TEST-001"),
                java.util.Map.of("type", "text", "text", "Test Item (1)"),
                java.util.Map.of("type", "text", "text", "Rs 100"),
                java.util.Map.of("type", "text", "text", "Test Customer")
            );
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("type", "body");
            body.put("parameters", params);

            java.util.Map<String, String> ap = java.util.Map.of("type", "payload", "payload", "ACCEPT_TEST-001");
            java.util.Map<String, Object> ab = new java.util.HashMap<>();
            ab.put("type", "button"); ab.put("sub_type", "quick_reply"); ab.put("index", "0");
            ab.put("parameters", java.util.List.of(ap));

            java.util.Map<String, String> rp = java.util.Map.of("type", "payload", "payload", "REJECT_TEST-001");
            java.util.Map<String, Object> rb = new java.util.HashMap<>();
            rb.put("type", "button"); rb.put("sub_type", "quick_reply"); rb.put("index", "1");
            rb.put("parameters", java.util.List.of(rp));

            template.put("components", java.util.List.of(body, ab, rb));
            payload.put("template", template);

            storeOrderDispatchService.sendWhatsAppRequest(payload);
            return ResponseEntity.ok(java.util.Map.of("success", true, "config", info, "message", "Test WhatsApp sent to " + phone));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(java.util.Map.of(
                "success", false,
                "phoneNumberId", storeOrderDispatchService.getPhoneNumberId(),
                "tokenConfigured", storeOrderDispatchService.isTokenConfigured(),
                "error", e.getMessage()
            ));
        }
    }

    /**
     * POST /api/admin/orders/{orderId}/broadcast
     * Body: { "vehicleType": "BIKE" }
     * Broadcasts order to all online riders of the given vehicle type.
     * First rider to accept via /api/delivery-app/broadcasts/{broadcastId}/accept wins.
     */
    @PostMapping("/{orderId}/broadcast")
    public ResponseEntity<?> broadcastOrder(@PathVariable Long orderId,
                                            @RequestBody Map<String, String> payload) {
        String vehicleType = payload.get("vehicleType");
        if (vehicleType == null || vehicleType.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "message", "vehicleType is required (BIKE, SCOOTY, EV, AUTO, HEAVY)"));
        }
        try {
            DeliveryBroadcast broadcast = adminService.broadcastOrderToVehicleType(orderId, vehicleType);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "broadcastId", broadcast.getId(),
                    "vehicleType", broadcast.getVehicleType().name(),
                    "expiresAt", broadcast.getExpiresAt().toString(),
                    "message", "Order broadcast sent to all online " + vehicleType + " riders"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/admin/orders/{orderId}/notify-store
     * Sends a WhatsApp notification to the store BEFORE admin formally accepts.
     * Store can reply ACCEPT or REJECT; admin then confirms based on response.
     */
    @PostMapping("/{orderId}/notify-store")
    public ResponseEntity<?> notifyStore(@PathVariable Long orderId) {
        try {
            Order order = orderService.getOrderByIdForAdmin(orderId);
            String status = order.getOrderStatus() == null ? "" : order.getOrderStatus().toLowerCase();
            if ("cancelled".equals(status) || "delivered".equals(status)) {
                return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "Cannot notify store for a " + status + " order"));
            }
            orderService.notifyStoreOfOrder(orderId);
            return ResponseEntity.ok(Map.of("success", true,
                "message", "Store notified via WhatsApp for order " + order.getOrderNumber()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{orderRef}/send-store-otp")
    public ResponseEntity<?> sendStorePickupOtp(@PathVariable String orderRef) {
        try {
            com.project.Anusha.model.Order order;
            // Accept either a numeric DB id or the alphanumeric order number string
            try {
                long id = Long.parseLong(orderRef);
                order = orderService.getOrderByIdForAdmin(id);
            } catch (NumberFormatException nfe) {
                order = orderService.getOrderByOrderNumber(orderRef);
            }
            String status = order.getOrderStatus() == null ? "" : order.getOrderStatus().toLowerCase();
            if ("cancelled".equals(status) || "delivered".equals(status)) {
                return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "Cannot send OTP for a " + status + " order"));
            }
            deliveryOrderService.adminSendStorePickupOtp(order.getOrderNumber());
            return ResponseEntity.ok(Map.of("success", true, "message", "Pickup OTP sent to store via WhatsApp"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private AdminOrderSummaryDto toSummaryDto(Order order) {
        AdminOrderSummaryDto dto = new AdminOrderSummaryDto();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setCustomerName(order.getCustomer().getName());
        dto.setCustomerPhone(order.getCustomer().getPhoneNumber());
        dto.setSubtotal(order.getSubtotal());
        dto.setDeliveryCharge(order.getDeliveryCharge());
        dto.setPlatformFee(order.getPlatformFee());
        dto.setHandlingCharge(order.getHandlingCharge());
        dto.setSmallCartFee(order.getSmallCartFee());
        dto.setDiscount(order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO);
        dto.setTaxableAmount(order.getTaxableAmount());
        dto.setTax(order.getTax());
        dto.setCgstAmount(order.getCgstAmount());
        dto.setSgstAmount(order.getSgstAmount());
        dto.setIgstAmount(order.getIgstAmount());
        dto.setGrandTotal(order.getGrandTotal());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setPlacedAt(order.getPlacedAt());

        // Collect distinct store names/ids from items
        if (order.getItems() != null) {
            LinkedHashMap<Long, String> storeMap = new LinkedHashMap<>();
            for (OrderItem item : order.getItems()) {
                Store store = item.getVariant() != null && item.getVariant().getProduct() != null
                        ? item.getVariant().getProduct().getStore() : null;
                if (store != null && store.getId() != null) {
                    storeMap.put(store.getId(), store.getName());
                }
            }
            dto.setStoreIds(new ArrayList<>(storeMap.keySet()));
            dto.setStoreNames(new ArrayList<>(storeMap.values()));
            dto.setStoreCount(storeMap.size());
        }

        deliveryOrderRepository.findByOrderNumber(order.getOrderNumber()).ifPresent(deliveryOrder -> {
            DeliveryPerson rider = deliveryOrder.getDeliveryPerson();
            dto.setRiderAssigned(rider != null);
            if (rider != null) {
                dto.setDeliveryPersonName(rider.getFirstName() + " " + rider.getLastName());
            }
        });

        return dto;
    }

    private AdminOrderDetailDto toDetailDto(Order order) {
        AdminOrderDetailDto dto = new AdminOrderDetailDto();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setCustomerName(order.getCustomer().getName());
        dto.setCustomerPhone(order.getCustomer().getPhoneNumber());
        dto.setCustomerEmail(order.getCustomer().getEmail());
        dto.setAddress(toAddressSummaryDto(order.getAddress()));
        dto.setSubtotal(order.getSubtotal());
        dto.setDeliveryCharge(order.getDeliveryCharge());
        dto.setPlatformFee(order.getPlatformFee());
        dto.setHandlingCharge(order.getHandlingCharge());
        dto.setSmallCartFee(order.getSmallCartFee());
        dto.setDiscount(order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO);
        dto.setGrandTotal(order.getGrandTotal());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setWalletApplied(order.getWalletAmount() != null ? order.getWalletAmount() : BigDecimal.ZERO);
        dto.setPaidAmount("PAID".equalsIgnoreCase(order.getPaymentStatus()) ? order.getGrandTotal() : BigDecimal.ZERO);
        com.project.Anusha.dto.PaymentRefundResponse refund = paymentService.getRefundStatus(order.getId(), null);
        dto.setRefundStatus(refund.getRefundStatus());
        dto.setRefundId(refund.getRefundId());
        dto.setRefundAmount(refund.getRefundAmount());
        dto.setRefundReason(refund.getRefundReason());
        dto.setRefundedAt(refund.getRefundedAt());
        dto.setPlacedAt(order.getPlacedAt());
        applyInvoiceDetails(dto, order.getAddress());

        // Delivery person details + store acceptance status
        deliveryOrderRepository.findByOrderNumber(order.getOrderNumber()).ifPresent(deliveryOrder -> {
            // Store acceptance status
            if (deliveryOrder.getStatus() != null) {
                dto.setStoreStatus(deliveryOrder.getStatus().name());
            }
            DeliveryPerson rider = deliveryOrder.getDeliveryPerson();
            if (rider != null) {
                dto.setDeliveryPersonName(rider.getFirstName() + " " + rider.getLastName());
                dto.setDeliveryPersonPhone(rider.getPhoneNumber());
                dto.setDeliveryPersonVehicle(
                        rider.getVehicleType() != null ? rider.getVehicleType().name() : null);
                dto.setDeliveryPersonRating(rider.getAverageRating());
            }
            if (deliveryOrder.getAssignedAt() != null) {
                dto.setEstimatedDeliveryTime(
                        deliveryOrder.getAssignedAt().plusMinutes(45).toString());
            }
        });

        List<OrderResponse.OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::toItemDto)
                .collect(Collectors.toList());
        dto.setItems(itemDtos);
        dto.setStoreGroups(buildStoreGroups(order));
        return dto;
    }

    private OrderResponse.AddressSummaryDto toAddressSummaryDto(Address address) {
        if (address == null) return null;
        OrderResponse.AddressSummaryDto dto = new OrderResponse.AddressSummaryDto();
        dto.setId(address.getId());
        dto.setAddressType(address.getAddressType());
        dto.setFlatNumber(address.getFlatNumber());
        dto.setAddressLine1(address.getAddressLine1());
        dto.setAddressLine2(address.getAddressLine2());
        dto.setLandmark(address.getLandmark());
        dto.setCity(address.getCity());
        dto.setState(address.getState());
        dto.setPostalCode(address.getPostalCode());
        return dto;
    }

    private OrderResponse.OrderItemDto toItemDto(OrderItem item) {
        OrderResponse.OrderItemDto dto = new OrderResponse.OrderItemDto();
        dto.setVariantId(item.getVariant().getId());
        dto.setProductName(item.getProductName());
        dto.setVariantName(item.getVariantName());
        dto.setSku(item.getProductSku());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setTotalPrice(item.getTotalPrice());
        dto.setHsnCode(item.getHsnCode());
        dto.setGstRate(item.getGstRate());
        dto.setTaxableAmount(item.getTaxableAmount());
        dto.setCgstRate(item.getCgstRate());
        dto.setSgstRate(item.getSgstRate());
        dto.setIgstRate(item.getIgstRate());
        dto.setCgstAmount(item.getCgstAmount());
        dto.setSgstAmount(item.getSgstAmount());
        dto.setIgstAmount(item.getIgstAmount());
        dto.setTotalTaxAmount(item.getTotalTaxAmount());
        dto.setImageUrl(item.getImageUrl());
        return dto;
    }

    private void applyInvoiceDetails(AdminOrderDetailDto dto, Address address) {
        String buyerState = address != null ? address.getState() : null;
        dto.setSellerName(GstInvoiceDetails.SELLER_NAME);
        dto.setSellerGstin(GstInvoiceDetails.GSTIN);
        dto.setSellerAddress(GstInvoiceDetails.ADDRESS);
        dto.setSellerState(GstInvoiceDetails.STATE);
        dto.setSellerStateCode(GstInvoiceDetails.STATE_CODE);
        dto.setPlaceOfSupply(buyerState == null || buyerState.isBlank() ? GstInvoiceDetails.STATE : buyerState);
        dto.setPlaceOfSupplyCode(GstInvoiceDetails.stateCodeFor(buyerState));
        dto.setReverseCharge(GstInvoiceDetails.REVERSE_CHARGE);
    }

    private List<AdminOrderDetailDto.StoreGroupDto> buildStoreGroups(Order order) {
        // Load StoreSubOrder records for accurate per-store status
        Map<Long, String> subOrderStatusByStore = storeSubOrderRepository
                .findByOrderId(order.getId())
                .stream()
                .collect(Collectors.toMap(
                        sub -> sub.getStore().getId(),
                        sub -> sub.getStatus() != null ? sub.getStatus().name() : "PENDING",
                        (a, b) -> a));

        Map<Long, AdminOrderDetailDto.StoreGroupDto> groupedStores = new LinkedHashMap<>();

        for (OrderItem item : order.getItems()) {
            Store store = item.getVariant() != null && item.getVariant().getProduct() != null
                    ? item.getVariant().getProduct().getStore()
                    : null;
            if (store == null || store.getId() == null) {
                continue;
            }

            AdminOrderDetailDto.StoreGroupDto group = groupedStores.computeIfAbsent(store.getId(), ignored -> {
                AdminOrderDetailDto.StoreGroupDto newGroup = new AdminOrderDetailDto.StoreGroupDto();
                newGroup.setStoreId(store.getId());
                newGroup.setStoreName(store.getName());
                newGroup.setStorePhone(store.getPhoneNumber());
                newGroup.setStatus(subOrderStatusByStore.getOrDefault(store.getId(), order.getOrderStatus()));
                newGroup.setSubtotal(BigDecimal.ZERO);
                newGroup.setItems(new ArrayList<>());
                return newGroup;
            });

            group.getItems().add(toItemDto(item));
            group.setSubtotal(group.getSubtotal().add(
                    item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO));
        }

        return new ArrayList<>(groupedStores.values());
    }
}
