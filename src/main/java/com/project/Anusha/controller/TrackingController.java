package com.project.Anusha.controller;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.service.DeliveryOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Customer-facing Live Tracking API
 *
 * The customer app should also listen directly to Firebase RTDB at:
 *   tracking/{orderNumber}
 * for real-time updates without polling. This endpoint is a fallback / REST option.
 */
@RestController
@RequestMapping("/api/tracking")
@CrossOrigin(origins = "*")
public class TrackingController {

    private final DeliveryOrderService deliveryOrderService;

    public TrackingController(DeliveryOrderService deliveryOrderService) {
        this.deliveryOrderService = deliveryOrderService;
    }

    /**
     * GET /api/tracking/{orderNumber}
     *
     * Returns current tracking data for the order from Firebase RTDB.
     * If no tracking data exists (order not yet accepted / already delivered), returns the DB order status.
     *
     * Example response (while in transit):
     * {
     *   "orderNumber": "GRO-12345678",
     *   "status": "EN_ROUTE_TO_CUSTOMER",
     *   "deliveryPersonName": "Ravi Kumar",
     *   "deliveryPersonPhone": "+919876543210",
     *   "lat": 19.0760,
     *   "lng": 72.8777,
     *   "updatedAt": "2024-03-24T11:00:00",
     *   "isLive": true
     * }
     *
     * Example response (order delivered / no tracking):
     * {
     *   "orderNumber": "GRO-12345678",
     *   "status": "DELIVERED",
     *   "isLive": false
     * }
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<?> getTrackingData(@PathVariable String orderNumber) {
        try {
            // First verify the order exists
            DeliveryOrder order = deliveryOrderService.getOrderByOrderNumber(orderNumber)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));

            // Read from Firebase RTDB (blocking read with 5s timeout)
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("tracking")
                    .child(orderNumber);

            final Map<String, Object>[] result = new Map[]{null};
            CountDownLatch latch = new CountDownLatch(1);

            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        result[0] = (Map<String, Object>) snapshot.getValue();
                    }
                    latch.countDown();
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    latch.countDown();
                }
            });

            boolean completed = latch.await(5, TimeUnit.SECONDS);

            if (completed && result[0] != null) {
                // Live tracking data found in RTDB
                Map<String, Object> response = new HashMap<>(result[0]);
                response.put("orderNumber", orderNumber);
                response.put("isLive", true);
                addAssignedDeliveryPersonDetails(response, order);
                addAssignedDeliveryPersonLocation(response, order);
                return ResponseEntity.ok(response);
            } else {
                // No live tracking — return DB order status
                Map<String, Object> response = new HashMap<>();
                response.put("orderNumber", orderNumber);
                response.put("status", order.getStatus().name());
                response.put("isLive", false);
                response.put("message", getStatusMessage(order.getStatus()));
                addAssignedDeliveryPersonDetails(response, order);
                addAssignedDeliveryPersonLocation(response, order);
                return ResponseEntity.ok(response);
            }

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch tracking data: " + e.getMessage()));
        }
    }

    private String getStatusMessage(DeliveryOrder.OrderStatus status) {
        return switch (status) {
            case PENDING -> "Order is pending. Awaiting processing.";
            case PLACED -> "Order placed. Waiting for store confirmation.";
            case STORE_NOTIFIED -> "Store has been notified about your order.";
            case STORE_ACCEPTED -> "Store accepted your order. Pickup OTP generated.";
            case STORE_REJECTED -> "Store was unable to process your order.";
            case PICKUP_OTP_GENERATED -> "Pickup OTP generated for the rider.";
            case BROADCASTED_TO_RIDERS -> "Looking for a nearby delivery rider.";
            case RIDER_ASSIGNED -> "A delivery rider has been assigned to your order.";
            case REACHED_STORE -> "Rider has reached the store.";
            case PICKUP_OTP_VERIFIED -> "Rider verified the pickup OTP.";
            case PICKED_UP -> "Rider has picked up your order.";
            case OUT_FOR_DELIVERY -> "Order is on the way to you!";
            case DELIVERED -> "Order has been delivered. Enjoy!";
            case CANCELLED -> "Order was cancelled.";
            case RIDER_REJECTED -> "Rider could not take this order. Looking for another.";
            case ASSIGNED -> "A delivery rider has been assigned to your order.";
            case ACCEPTED -> "Store accepted your order.";
            case EN_ROUTE_TO_STORE -> "Rider is heading to the store.";
            case EN_ROUTE_TO_CUSTOMER -> "Order is on the way to you!";
        };
    }

    private void addAssignedDeliveryPersonDetails(Map<String, Object> response, DeliveryOrder order) {
        if (order.getDeliveryPerson() == null) {
            return;
        }

        String riderName = order.getDeliveryPerson().getFullName();
        String riderPhone = order.getDeliveryPerson().getPhoneNumber();

        if (isBlank(response.get("deliveryPersonName")) && riderName != null && !riderName.isBlank()) {
            response.put("deliveryPersonName", riderName);
        }
        if (isBlank(response.get("deliveryPersonPhone")) && riderPhone != null && !riderPhone.isBlank()) {
            response.put("deliveryPersonPhone", riderPhone);
        }
    }

    private void addAssignedDeliveryPersonLocation(Map<String, Object> response, DeliveryOrder order) {
        if (order.getDeliveryPerson() == null) {
            return;
        }

        boolean shouldExposeRiderLocation =
                order.getStatus() == DeliveryOrder.OrderStatus.PICKED_UP
                        || order.getStatus() == DeliveryOrder.OrderStatus.OUT_FOR_DELIVERY;

        if (!shouldExposeRiderLocation) {
            return;
        }

        Double latitude = order.getDeliveryPerson().getLatitude();
        Double longitude = order.getDeliveryPerson().getLongitude();

        if (response.get("lat") == null && latitude != null) {
            response.put("lat", latitude);
        }
        if (response.get("lng") == null && longitude != null) {
            response.put("lng", longitude);
        }
        if (response.get("updatedAt") == null && order.getDeliveryPerson().getLocationUpdatedAt() != null) {
            response.put("updatedAt", order.getDeliveryPerson().getLocationUpdatedAt().toString());
        }
    }

    private boolean isBlank(Object value) {
        return value == null || value.toString().isBlank();
    }
}
