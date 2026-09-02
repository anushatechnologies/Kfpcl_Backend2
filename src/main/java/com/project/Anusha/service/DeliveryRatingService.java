package com.project.Anusha.service;

import com.project.Anusha.model.Customer;
import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.DeliveryOrder.OrderStatus;
import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.repository.DeliveryOrderRepository;
import com.project.Anusha.repository.DeliveryPersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryRatingService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliveryPersonRepository deliveryPersonRepository;
    private final CustomerService customerService;

    /**
     * Customer submits a 1–5 star rating (+ optional feedback) for the delivery
     * person after the order is delivered.
     *
     * Rules:
     *  - Order must be DELIVERED
     *  - Order must belong to the authenticated customer (matched by phone)
     *  - Rating can only be submitted once (idempotent check)
     */
    @Transactional
    public Map<String, Object> submitRating(String orderNumber, Customer customer,
                                            int rating, String feedback) {

        DeliveryOrder deliveryOrder = deliveryOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        // Verify the order belongs to this customer
        if (!deliveryOrder.getCustomerPhone().equals(customer.getPhoneNumber())) {
            throw new RuntimeException("Order does not belong to this customer");
        }

        // Only allow rating after delivery
        if (deliveryOrder.getStatus() != OrderStatus.DELIVERED) {
            throw new RuntimeException("Rating can only be submitted after the order is delivered");
        }

        // Prevent duplicate rating
        if (deliveryOrder.getCustomerRating() != null) {
            throw new RuntimeException("You have already rated this delivery");
        }

        // Save rating on the delivery order
        deliveryOrder.setCustomerRating(rating);
        deliveryOrder.setCustomerFeedback(feedback);
        deliveryOrderRepository.save(deliveryOrder);

        // Recalculate delivery person's average rating
        DeliveryPerson rider = deliveryOrder.getDeliveryPerson();
        if (rider != null) {
            recalculateAverageRating(rider);
        }

        log.info("Rating submitted: orderNumber={} rating={} rider={}",
                orderNumber, rating, rider != null ? rider.getId() : "none");

        return Map.of(
                "success", true,
                "message", "Thank you for your feedback!",
                "rating", rating
        );
    }

    /**
     * Returns the rating status for a given order — used by the frontend
     * to decide whether to show the rating prompt.
     */
    public Map<String, Object> getRatingStatus(String orderNumber, Customer customer) {
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        if (!deliveryOrder.getCustomerPhone().equals(customer.getPhoneNumber())) {
            throw new RuntimeException("Order does not belong to this customer");
        }

        boolean delivered = deliveryOrder.getStatus() == OrderStatus.DELIVERED;
        boolean alreadyRated = deliveryOrder.getCustomerRating() != null;

        return Map.of(
                "orderNumber", orderNumber,
                "delivered", delivered,
                "alreadyRated", alreadyRated,
                "rating", deliveryOrder.getCustomerRating() != null ? deliveryOrder.getCustomerRating() : 0,
                "feedback", deliveryOrder.getCustomerFeedback() != null ? deliveryOrder.getCustomerFeedback() : "",
                "canRate", delivered && !alreadyRated
        );
    }

    /** Recomputes averageRating on DeliveryPerson from all rated orders. */
    private void recalculateAverageRating(DeliveryPerson rider) {
        List<DeliveryOrder> ratedOrders =
                deliveryOrderRepository.findByDeliveryPersonIdAndCustomerRatingIsNotNull(rider.getId());

        int total = ratedOrders.size();
        double avg = ratedOrders.stream()
                .mapToInt(DeliveryOrder::getCustomerRating)
                .average()
                .orElse(0.0);

        rider.setTotalRatings(total);
        rider.setAverageRating(Math.round(avg * 10.0) / 10.0); // round to 1 decimal
        deliveryPersonRepository.save(rider);

        log.info("Updated rider {} average rating: {} ({} ratings)", rider.getId(), rider.getAverageRating(), total);
    }
}
