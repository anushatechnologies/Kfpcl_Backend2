package com.project.Anusha.controller;

import com.project.Anusha.dto.PlaceOrderRequest;
import com.project.Anusha.dto.OrderResponse;
import com.project.Anusha.model.Address;
import com.project.Anusha.model.Customer;
import com.project.Anusha.model.Order;
import com.project.Anusha.model.OrderItem;
import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.Store;
import com.project.Anusha.model.StoreSubOrder;
import com.project.Anusha.repository.DeliveryOrderRepository;
import com.project.Anusha.repository.PaymentTransactionRepository;
import com.project.Anusha.repository.StoreSubOrderRepository;
import com.project.Anusha.service.CustomerService;
import com.project.Anusha.service.GstInvoiceDetails;
import com.project.Anusha.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CustomerService customerService;
    private final StoreSubOrderRepository storeSubOrderRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    public OrderController(OrderService orderService, CustomerService customerService,
                           StoreSubOrderRepository storeSubOrderRepository,
                           DeliveryOrderRepository deliveryOrderRepository,
                           PaymentTransactionRepository paymentTransactionRepository) {
        this.orderService = orderService;
        this.customerService = customerService;
        this.storeSubOrderRepository = storeSubOrderRepository;
        this.deliveryOrderRepository = deliveryOrderRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
    }

    private Customer getCustomer(UserDetails userDetails) {
        return customerService.getCustomerByPhone(userDetails.getUsername());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PlaceOrderRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Customer customer = getCustomer(userDetails);
        Order placedOrder = orderService.placeOrder(
                customer,
                request.getAddressId(),
                request.getPaymentMethod(),
                request.getCouponCode(),
                idempotencyKey,
                request.getWalletAmount()
        );
        Order order = orderService.getOrderById(placedOrder.getId());
        return ResponseEntity.ok(toResponse(order));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getMyOrders(@AuthenticationPrincipal UserDetails userDetails) {
        Customer customer = getCustomer(userDetails);
        List<Order> orders = orderService.getCustomerOrders(customer);
        List<OrderResponse> response = orders.stream().map(this::toResponse).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent-products")
    public ResponseEntity<List<com.project.Anusha.dto.ProductResponse>> getRecentProducts(
            @AuthenticationPrincipal UserDetails userDetails) {
        Customer customer = getCustomer(userDetails);
        List<com.project.Anusha.dto.ProductResponse> recentProducts = orderService.getRecentOrderProducts(customer);
        return ResponseEntity.ok(recentProducts);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId) {
        Customer customer = getCustomer(userDetails);
        Order order = orderService.getOrderById(orderId);
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new AccessDeniedException("Order does not belong to the authenticated customer");
        }
        return ResponseEntity.ok(toResponse(order));
    }

    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, String> request) {
        Customer customer = getCustomer(userDetails);
        String reason = request != null ? request.get("reason") : null;
        Order order = orderService.cancelOrderByCustomer(customer, orderId, reason);
        return ResponseEntity.ok(toResponse(order));
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setSubtotal(order.getSubtotal());
        response.setDeliveryCharge(order.getDeliveryCharge());
        response.setPlatformFee(order.getPlatformFee());
        response.setHandlingCharge(order.getHandlingCharge());
        response.setSmallCartFee(order.getSmallCartFee());
        response.setWalletApplied(order.getWalletAmount() != null ? order.getWalletAmount() : BigDecimal.ZERO);
        response.setPaidAmount("PAID".equalsIgnoreCase(order.getPaymentStatus()) ? order.getGrandTotal() : BigDecimal.ZERO);
        response.setTax(order.getTax());
        response.setTaxableAmount(order.getTaxableAmount());
        response.setCgstAmount(order.getCgstAmount());
        response.setSgstAmount(order.getSgstAmount());
        response.setIgstAmount(order.getIgstAmount());
        response.setDiscount(order.getDiscount());
        response.setGrandTotal(order.getGrandTotal());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setOrderStatus(order.getOrderStatus());
        response.setStatus(order.getOrderStatus());
        response.setPaymentStatus(order.getPaymentStatus());
        paymentTransactionRepository
                .findTopByOrderIdAndStatusAndPaymentMethodOrderByCreatedAtDesc(order.getId(), "SUCCESS", "CASHFREE")
                .ifPresent(tx -> {
                    response.setRefundStatus(tx.getRefundStatus());
                    response.setRefundId(tx.getRefundId());
                    response.setRefundAmount(tx.getRefundAmount());
                    response.setRefundReason(tx.getRefundReason());
                    response.setRefundedAt(tx.getRefundedAt());
                });
        response.setPlacedAt(order.getPlacedAt());
        response.setCreatedAt(order.getCreatedAt() != null ? order.getCreatedAt() : order.getPlacedAt());
        response.setAddress(toAddressDto(order.getAddress()));
        applyInvoiceDetails(response, order.getAddress());

        List<OrderResponse.OrderItemDto> itemDtos = order.getItems().stream()
                .map(this::toItemDto)
                .collect(Collectors.toList());
        response.setItems(itemDtos);

        // Build store groups — first try StoreSubOrder table (accurate status),
        // fall back to grouping items directly when no sub-orders exist yet.
        List<OrderResponse.StoreGroupDto> storeGroups = buildStoreGroups(order);
        response.setStoreGroups(storeGroups.size() > 1 ? storeGroups : null);

        // Populate ETA and rider details from the linked DeliveryOrder
        deliveryOrderRepository.findByOrderNumber(order.getOrderNumber()).ifPresent(deliveryOrder -> {
            DeliveryPerson rider = deliveryOrder.getDeliveryPerson();
            if (rider != null) {
                response.setDeliveryPersonName(rider.getFirstName() + " " + rider.getLastName());
                response.setDeliveryPersonPhone(rider.getPhoneNumber());
            }
            if (deliveryOrder.getAssignedAt() != null) {
                response.setEstimatedDeliveryTime(
                        deliveryOrder.getAssignedAt().plusMinutes(45).toString());
            }
        });

        return response;
    }

    private List<OrderResponse.StoreGroupDto> buildStoreGroups(Order order) {
        // Try sub-orders from DB first
        List<StoreSubOrder> subOrders = storeSubOrderRepository.findByOrderId(order.getId());

        if (!subOrders.isEmpty()) {
            return subOrders.stream().map(sub -> {
                OrderResponse.StoreGroupDto g = new OrderResponse.StoreGroupDto();
                g.setStoreId(sub.getStore().getId());
                g.setStoreName(sub.getStore().getName());
                g.setStatus(sub.getStatus() != null ? sub.getStatus().name() : "PENDING");
                g.setSubtotal(sub.getSubtotal());
                // Filter parent order items belonging to this store
                List<OrderResponse.OrderItemDto> items = order.getItems().stream()
                        .filter(i -> i.getVariant() != null
                                && i.getVariant().getProduct() != null
                                && i.getVariant().getProduct().getStore() != null
                                && i.getVariant().getProduct().getStore().getId().equals(sub.getStore().getId()))
                        .map(this::toItemDto)
                        .collect(Collectors.toList());
                g.setItems(items);
                return g;
            }).collect(Collectors.toList());
        }

        // Fallback — group items by store directly
        Map<Long, OrderResponse.StoreGroupDto> map = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            Store store = item.getVariant() != null && item.getVariant().getProduct() != null
                    ? item.getVariant().getProduct().getStore() : null;
            if (store == null || store.getId() == null) continue;

            OrderResponse.StoreGroupDto g = map.computeIfAbsent(store.getId(), ignored -> {
                OrderResponse.StoreGroupDto ng = new OrderResponse.StoreGroupDto();
                ng.setStoreId(store.getId());
                ng.setStoreName(store.getName());
                ng.setStatus(order.getOrderStatus());
                ng.setSubtotal(BigDecimal.ZERO);
                ng.setItems(new ArrayList<>());
                return ng;
            });
            g.getItems().add(toItemDto(item));
            g.setSubtotal(g.getSubtotal().add(
                    item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO));
        }
        return new ArrayList<>(map.values());
    }

    private OrderResponse.OrderItemDto toItemDto(OrderItem item) {
        OrderResponse.OrderItemDto dto = new OrderResponse.OrderItemDto();
        dto.setId(item.getId());
        dto.setProductId(item.getVariant().getProduct().getId());
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
        dto.setFreeItem(Boolean.TRUE.equals(item.getFreeItem()));
        dto.setOfferName(item.getOfferName());
        // attach store name so the customer app can group without extra API call
        if (item.getVariant() != null && item.getVariant().getProduct() != null
                && item.getVariant().getProduct().getStore() != null) {
            dto.setStoreName(item.getVariant().getProduct().getStore().getName());
        }
        return dto;
    }

    private OrderResponse.AddressSummaryDto toAddressDto(Address address) {
        if (address == null) {
            return null;
        }

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

    private void applyInvoiceDetails(OrderResponse response, Address address) {
        String buyerState = address != null ? address.getState() : null;
        response.setSellerName(GstInvoiceDetails.SELLER_NAME);
        response.setSellerGstin(GstInvoiceDetails.GSTIN);
        response.setSellerAddress(GstInvoiceDetails.ADDRESS);
        response.setSellerState(GstInvoiceDetails.STATE);
        response.setSellerStateCode(GstInvoiceDetails.STATE_CODE);
        response.setPlaceOfSupply(buyerState == null || buyerState.isBlank() ? GstInvoiceDetails.STATE : buyerState);
        response.setPlaceOfSupplyCode(GstInvoiceDetails.stateCodeFor(buyerState));
        response.setReverseCharge(GstInvoiceDetails.REVERSE_CHARGE);
    }
}
