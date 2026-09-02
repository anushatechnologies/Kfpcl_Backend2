package com.project.Anusha.service;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.Payout;
import com.project.Anusha.model.Payout.PayoutStatus;
import com.project.Anusha.repository.DeliveryOrderRepository;
import com.project.Anusha.repository.DeliveryPersonRepository;
import com.project.Anusha.repository.PayoutRepository;

@Service
@Transactional
public class PayoutService {

    @Autowired
    private PayoutRepository payoutRepository;

    @Autowired
    private DeliveryPersonRepository deliveryPersonRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    /**
     * Generate weekly payouts for all delivery persons
     */
    public List<Payout> generateWeeklyPayouts() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                                   .withHour(0)
                                   .withMinute(0)
                                   .withSecond(0)
                                   .withNano(0);
        LocalDateTime weekEnd = weekStart.plusDays(7).minusSeconds(1);

        List<DeliveryPerson> deliveryPersons = deliveryPersonRepository.findByIsApprovedByAdminTrueAndIsVerifiedTrue();
        
        return deliveryPersons.stream()
            .map(deliveryPerson -> generateWeeklyPayoutForDeliveryPerson(deliveryPerson, weekStart, weekEnd))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    /**
     * Generate weekly payout for a specific delivery person
     */
    public Optional<Payout> generateWeeklyPayoutForDeliveryPerson(DeliveryPerson deliveryPerson, LocalDateTime weekStart, LocalDateTime weekEnd) {
        // Check if payout already exists for this week
        Optional<Payout> existingPayout = payoutRepository.findByDeliveryPersonAndWeek(deliveryPerson.getId(), weekStart);
        if (existingPayout.isPresent()) {
            return existingPayout;
        }

        // Calculate total earnings for the week
        List<DeliveryOrder> completedOrders = deliveryOrderRepository.findCompletedOrdersByDeliveryPerson(deliveryPerson.getId())
            .stream()
            .filter(order -> order.getDeliveredAt() != null && 
                           order.getDeliveredAt().isAfter(weekStart) && 
                           order.getDeliveredAt().isBefore(weekEnd))
            .toList();

        if (completedOrders.isEmpty()) {
            return Optional.empty();
        }

        double totalEarnings = completedOrders.stream()
            .filter(order -> order.getDeliveryFee() != null)
            .mapToDouble(order -> order.getDeliveryFee().doubleValue())
            .sum();

        int totalOrders = completedOrders.size();

        Payout payout = new Payout(
            deliveryPerson,
            java.math.BigDecimal.valueOf(totalEarnings),
            weekStart,
            weekEnd,
            totalOrders
        );

        return Optional.of(payoutRepository.save(payout));
    }

    /**
     * Process payout (mark as processed)
     */
    public Payout processPayout(Long payoutId, String transactionId, String paymentMethod, Long adminId) {
        Payout payout = payoutRepository.findById(payoutId)
            .orElseThrow(() -> new IllegalArgumentException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new IllegalArgumentException("Payout is not in pending status");
        }

        payout.setStatus(PayoutStatus.PROCESSED);
        payout.setTransactionId(transactionId);
        payout.setPaymentMethod(paymentMethod);
        payout.setProcessedAt(LocalDateTime.now());
        
        // Set admin who processed (you might need to fetch the admin user)
        // User admin = userRepository.findById(adminId).orElse(null);
        // payout.setProcessedByAdmin(admin);

        return payoutRepository.save(payout);
    }

    /**
     * Get payouts for delivery person
     */
    public List<Payout> getPayoutsForDeliveryPerson(Long deliveryPersonId) {
        return payoutRepository.findByDeliveryPersonIdOrderByWeekStartDateDesc(deliveryPersonId);
    }

    /**
     * Get pending payouts for processing
     */
    public List<Payout> getPendingPayouts() {
        return payoutRepository.findByStatusOrderByCreatedAtDesc(PayoutStatus.PENDING);
    }

    /**
     * Get all payouts
     */
    public List<Payout> getAllPayouts() {
        return payoutRepository.findAll();
    }

    /**
     * Get payout by ID
     */
    public Payout getPayoutById(Long id) {
        return payoutRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Payout not found"));
    }

    /**
     * Get payout statistics
     */
    public java.util.Map<String, Object> getPayoutStatistics() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        List<Payout> allPayouts = payoutRepository.findAll();
        List<Payout> pendingPayouts = payoutRepository.findByStatus(PayoutStatus.PENDING);
        List<Payout> processedPayouts = payoutRepository.findByStatus(PayoutStatus.PROCESSED);
        
        stats.put("totalPayouts", allPayouts.size());
        stats.put("pendingPayouts", pendingPayouts.size());
        stats.put("processedPayouts", processedPayouts.size());
        
        double totalPendingAmount = pendingPayouts.stream()
            .mapToDouble(payout -> payout.getPayoutAmount().doubleValue())
            .sum();
        stats.put("totalPendingAmount", totalPendingAmount);
        
        double totalProcessedAmount = processedPayouts.stream()
            .mapToDouble(payout -> payout.getPayoutAmount().doubleValue())
            .sum();
        stats.put("totalProcessedAmount", totalProcessedAmount);
        
        return stats;
    }

    /**
     * Get recent payouts for delivery person
     */
    public List<Payout> getRecentPayoutsForDeliveryPerson(Long deliveryPersonId, int limit) {
        LocalDateTime startDate = LocalDateTime.now().minusMonths(3);
        List<Payout> payouts = payoutRepository.findRecentPayoutsByDeliveryPerson(deliveryPersonId, startDate);
        return payouts.stream().limit(limit).toList();
    }

    /**
     * Fail payout
     */
    public Payout failPayout(Long payoutId, String remarks, Long adminId) {
        Payout payout = payoutRepository.findById(payoutId)
            .orElseThrow(() -> new IllegalArgumentException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new IllegalArgumentException("Payout is not in pending status");
        }

        payout.setStatus(PayoutStatus.FAILED);
        payout.setRemarks(remarks);
        
        // Set admin who processed
        // User admin = userRepository.findById(adminId).orElse(null);
        // payout.setProcessedByAdmin(admin);

        return payoutRepository.save(payout);
    }

    /**
     * Cancel payout
     */
    public Payout cancelPayout(Long payoutId, String remarks, Long adminId) {
        Payout payout = payoutRepository.findById(payoutId)
            .orElseThrow(() -> new IllegalArgumentException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.PENDING) {
            throw new IllegalArgumentException("Payout is not in pending status");
        }

        payout.setStatus(PayoutStatus.CANCELLED);
        payout.setRemarks(remarks);
        
        // Set admin who processed
        // User admin = userRepository.findById(adminId).orElse(null);
        // payout.setProcessedByAdmin(admin);

        return payoutRepository.save(payout);
    }

    /**
     * Retry failed payout
     */
    public Payout retryFailedPayout(Long payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
            .orElseThrow(() -> new IllegalArgumentException("Payout not found"));

        if (payout.getStatus() != PayoutStatus.FAILED) {
            throw new IllegalArgumentException("Payout is not in failed status");
        }

        payout.setStatus(PayoutStatus.PENDING);
        payout.setRemarks(null);
        payout.setProcessedAt(null);
        payout.setTransactionId(null);
        payout.setPaymentMethod(null);

        return payoutRepository.save(payout);
    }

    /**
     * Get total paid amount for delivery person
     */
    public Optional<Double> getTotalPaidAmountForDeliveryPerson(Long deliveryPersonId) {
        return payoutRepository.getTotalPaidAmountByDeliveryPerson(deliveryPersonId);
    }
}
