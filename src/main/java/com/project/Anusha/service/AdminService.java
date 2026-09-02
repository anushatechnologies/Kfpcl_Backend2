package com.project.Anusha.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.Anusha.model.DeliveryBroadcast;
import com.project.Anusha.model.DeliveryPerson;
import com.project.Anusha.model.DeliveryPersonDocument;
import com.project.Anusha.model.DeliveryPersonDocument.DocumentStatus;
import com.project.Anusha.model.DeliveryOrder;
import com.project.Anusha.model.OrderItem;
import com.project.Anusha.model.Store;
import com.project.Anusha.repository.*;
import com.project.Anusha.model.PaymentTransaction;
import com.project.Anusha.model.Order;

import com.project.Anusha.model.ProductRating;

@Service
@Transactional
public class AdminService {

    @Autowired
    private DeliveryPersonRepository deliveryPersonRepository;

    @Autowired
    private DeliveryPersonDocumentRepository documentRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserMainRepository userMainRepository;

    @Autowired
    private UserMainService userMainService;

    @Autowired
    private FirebaseService firebaseService;

    @Autowired
    private ProductRatingRepository ratingRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private DeliveryBroadcastRepository broadcastRepository;

    @Autowired
    private DeliveryOnboardingService deliveryOnboardingService;

    @Autowired
    private FareRuleService fareRuleService;

    @Autowired
    private AppNotificationService appNotificationService;

    public List<ProductRating> getAllRatings() {
        return ratingRepository.findAll();
    }

    public void deleteRating(Long id) {
        ratingRepository.deleteById(id);
    }

    public Map<String, Object> getIncomeSummary() {
        Map<String, Object> summary = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        BigDecimal razorpayToday = zeroAmount(
                paymentTransactionRepository.sumAmountByStatusAndCreatedAtBetween("SUCCESS", todayStart, now));
        BigDecimal razorpayWeek = zeroAmount(
                paymentTransactionRepository.sumAmountByStatusAndCreatedAtBetween("SUCCESS", weekStart, now));
        BigDecimal razorpayMonth = zeroAmount(
                paymentTransactionRepository.sumAmountByStatusAndCreatedAtBetween("SUCCESS", monthStart, now));
        BigDecimal razorpayTotal = zeroAmount(paymentTransactionRepository.sumAmountByStatus("SUCCESS"));

        BigDecimal codToday = zeroAmount(
                orderRepository.sumGrandTotalByPaymentMethodAndOrderStatusBetween("COD", "delivered", todayStart, now));
        BigDecimal codWeek = zeroAmount(
                orderRepository.sumGrandTotalByPaymentMethodAndOrderStatusBetween("COD", "delivered", weekStart, now));
        BigDecimal codMonth = zeroAmount(
                orderRepository.sumGrandTotalByPaymentMethodAndOrderStatusBetween("COD", "delivered", monthStart, now));
        BigDecimal codTotal = zeroAmount(
                orderRepository.sumGrandTotalByPaymentMethodAndOrderStatus("COD", "delivered"));

        summary.put("todayIncome", razorpayToday.add(codToday));
        summary.put("weekIncome", razorpayWeek.add(codWeek));
        summary.put("monthIncome", razorpayMonth.add(codMonth));
        summary.put("totalIncome", razorpayTotal.add(codTotal));
        summary.put("razorpayIncome", razorpayTotal);
        summary.put("codIncome", codTotal);

        return summary;
    }

    public List<Map<String, Object>> getAllPayments() {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (PaymentTransaction paymentTransaction : paymentTransactionRepository.findAllByOrderByCreatedAtDesc()) {
            LocalDateTime eventTime = paymentTransaction.getPaymentDate() != null
                    ? paymentTransaction.getPaymentDate()
                    : paymentTransaction.getCreatedAt();

            Map<String, Object> row = new HashMap<>();
            row.put("id", paymentTransaction.getId());
            row.put("txnid", paymentTransaction.getTransactionId());
            row.put("transactionId", paymentTransaction.getTransactionId());
            row.put("amount", paymentTransaction.getAmount());
            row.put("status", paymentTransaction.getStatus());
            row.put("method", normalizePaymentMethod(paymentTransaction.getPaymentMethod()));
            row.put("paymentMethod", normalizePaymentMethod(paymentTransaction.getPaymentMethod()));
            row.put("date", eventTime);
            row.put("createdAt", eventTime);
            row.put("source", "PAYMENT_GATEWAY");
            row.put("_sortDate", eventTime);
            rows.add(row);
        }

        for (Order order : orderRepository.findByPaymentMethodAndOrderStatusOrderByEffectiveDateDesc("COD", "delivered")) {
            LocalDateTime eventTime = order.getDeliveredAt() != null ? order.getDeliveredAt() : order.getPlacedAt();

            Map<String, Object> row = new HashMap<>();
            row.put("id", "COD-" + order.getId());
            row.put("txnid", order.getOrderNumber() != null ? "COD-" + order.getOrderNumber() : "COD-" + order.getId());
            row.put("transactionId", order.getOrderNumber() != null ? "COD-" + order.getOrderNumber() : "COD-" + order.getId());
            row.put("orderNumber", order.getOrderNumber());
            row.put("amount", order.getGrandTotal());
            row.put("status", "SUCCESS");
            row.put("method", "COD");
            row.put("paymentMethod", "COD");
            row.put("date", eventTime);
            row.put("createdAt", eventTime);
            row.put("source", "CASH_ON_DELIVERY");
            row.put("_sortDate", eventTime);
            rows.add(row);
        }

        rows.sort(Comparator.comparing(
                row -> (LocalDateTime) row.get("_sortDate"),
                Comparator.nullsLast(Comparator.reverseOrder())));
        rows.forEach(row -> row.remove("_sortDate"));
        return rows;
    }

    public List<DeliveryPerson> getDeliveryPersonsPendingApproval() {
        // Returns delivery persons whose approval status is PENDING (not yet reviewed)
        return deliveryPersonRepository.findPendingApproval();
    }

    /**
     * Get all documents pending review
     */
    public List<DeliveryPersonDocument> getDocumentsPendingReview() {
        return documentRepository.findByStatus(DocumentStatus.PENDING);
    }

    /**
     * Approve document
     */
    public DeliveryPersonDocument approveDocument(Long documentId, Long adminId) {
        DeliveryPersonDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        document.setStatus(DocumentStatus.APPROVED);
        document.setReviewedAt(LocalDateTime.now());

        DeliveryPersonDocument savedDocument = documentRepository.save(document);
        DeliveryPerson deliveryPerson = document.getDeliveryPerson();

        // Notify delivery person
        notifyDeliveryPerson(deliveryPerson, "Document Approved! ✅",
                "Your " + document.getDocumentType().getDisplayName() + " has been approved.");

        if (deliveryOnboardingService.isReadyForFinalApproval(
                deliveryPerson,
                documentRepository.findByDeliveryPersonId(deliveryPerson.getId()))) {
            deliveryPerson.setApprovedByAdmin(true);
            deliveryPerson.setApprovalStatus(DeliveryPerson.ApprovalStatus.APPROVED);
            deliveryPersonRepository.save(deliveryPerson);
        }

        return savedDocument;
    }

    /**
     * Reject document with remarks
     */
    public DeliveryPersonDocument rejectDocument(Long documentId, String remarks, Long adminId) {
        DeliveryPersonDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        document.setStatus(DocumentStatus.REJECTED);
        document.setAdminRemarks(remarks);
        document.setReviewedAt(LocalDateTime.now());

        DeliveryPersonDocument saved = documentRepository.save(document);
        DeliveryPerson deliveryPerson = document.getDeliveryPerson();
        deliveryPerson.setApprovedByAdmin(false);
        deliveryPerson.setApprovalStatus(DeliveryPerson.ApprovalStatus.PENDING);
        deliveryPersonRepository.save(deliveryPerson);

        // Notify delivery person
        notifyDeliveryPerson(document.getDeliveryPerson(), "Document Rejected ❌",
                "Your " + document.getDocumentType().getDisplayName() + " was rejected. Reason: " + remarks);

        return saved;
    }

    /**
     * Request document re-upload
     */
    public DeliveryPersonDocument requestDocumentReupload(Long documentId, String remarks, Long adminId) {
        DeliveryPersonDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        document.setStatus(DocumentStatus.NEEDS_REUPLOAD);
        document.setAdminRemarks(remarks);
        document.setReviewedAt(LocalDateTime.now());

        DeliveryPersonDocument saved = documentRepository.save(document);
        DeliveryPerson deliveryPerson = document.getDeliveryPerson();
        deliveryPerson.setApprovedByAdmin(false);
        deliveryPerson.setApprovalStatus(DeliveryPerson.ApprovalStatus.PENDING);
        deliveryPersonRepository.save(deliveryPerson);

        // Notify delivery person
        notifyDeliveryPerson(document.getDeliveryPerson(), "Re-upload Requested ⚠️",
                "Your " + document.getDocumentType().getDisplayName() + " needs re-upload. Reason: " + remarks);

        return saved;
    }

    /**
     * Approve profile photo
     */
    public DeliveryPerson approveProfilePhoto(Long deliveryPersonId, Long adminId) {
        DeliveryPerson dp = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

        dp.setProfilePhotoStatus(DocumentStatus.APPROVED);
        dp.setProfilePhotoRemarks(null);

        if (deliveryOnboardingService.isReadyForFinalApproval(
                dp,
                documentRepository.findByDeliveryPersonId(deliveryPersonId))) {
            dp.setApprovedByAdmin(true);
            dp.setApprovalStatus(DeliveryPerson.ApprovalStatus.APPROVED);
        }

        DeliveryPerson saved = deliveryPersonRepository.save(dp);

        // Notify delivery person
        notifyDeliveryPerson(saved, "Profile Photo Approved! ✅", "Your profile photo has been approved.");

        return saved;
    }

    /**
     * Reject profile photo with remarks
     */
    public DeliveryPerson rejectProfilePhoto(Long deliveryPersonId, String remarks, Long adminId) {
        DeliveryPerson dp = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

        dp.setProfilePhotoStatus(DocumentStatus.REJECTED);
        dp.setProfilePhotoRemarks(remarks);

        // Revoke overall approval if photo is rejected
        dp.setApprovedByAdmin(false);
        dp.setApprovalStatus(DeliveryPerson.ApprovalStatus.PENDING);

        DeliveryPerson saved = deliveryPersonRepository.save(dp);

        // Notify delivery person
        notifyDeliveryPerson(saved, "Profile Photo Rejected ❌", "Your profile photo was rejected. Reason: " + remarks);

        return saved;
    }

    /**
     * Request profile photo re-upload
     */
    public DeliveryPerson requestProfilePhotoReupload(Long deliveryPersonId, String remarks, Long adminId) {
        DeliveryPerson dp = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

        dp.setProfilePhotoStatus(DocumentStatus.NEEDS_REUPLOAD);
        dp.setProfilePhotoRemarks(remarks);

        // Revoke overall approval if photo re-upload is requested
        dp.setApprovedByAdmin(false);
        dp.setApprovalStatus(DeliveryPerson.ApprovalStatus.PENDING);

        DeliveryPerson saved = deliveryPersonRepository.save(dp);

        // Notify delivery person
        notifyDeliveryPerson(saved, "Photo Re-upload Requested ⚠️",
                "Your profile photo needs re-upload. Reason: " + remarks);

        return saved;
    }

    /**
     * Get all delivery persons
     */
    public List<DeliveryPerson> getAllDeliveryPersons() {
        return deliveryPersonRepository.findAll();
    }

    /**
     * Get delivery person by ID
     */
    public DeliveryPerson getDeliveryPersonById(Long id) {
        return deliveryPersonRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));
    }

    /**
     * Get delivery person documents
     */
    public List<DeliveryPersonDocument> getDeliveryPersonDocuments(Long deliveryPersonId) {
        return documentRepository.findByDeliveryPersonId(deliveryPersonId);
    }

    /**
     * Get available delivery persons for order assignment
     */
    public List<DeliveryPerson> getAvailableDeliveryPersons() {
        return deliveryPersonRepository.findAvailableDeliveryPersons();
    }

    /**
     * Approve delivery person manually
     */
    public DeliveryPerson approveDeliveryPerson(Long deliveryPersonId, Long adminId) {
        DeliveryPerson deliveryPerson = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

        if (!deliveryOnboardingService.isReadyForFinalApproval(
                deliveryPerson,
                documentRepository.findByDeliveryPersonId(deliveryPersonId))) {
            throw new IllegalArgumentException(
                    "Vehicle details, bank details, profile photo, and all required documents must be approved before final approval");
        }

        deliveryPerson.setApprovedByAdmin(true);
        deliveryPerson.setApprovalStatus(DeliveryPerson.ApprovalStatus.APPROVED);

        DeliveryPerson saved = deliveryPersonRepository.save(deliveryPerson);

        // Write RTDB status for real-time app notification (instant, no polling needed)
        firebaseService.writeDeliveryPartnerStatus(saved.getId(), "APPROVED");

        // Notify delivery person: FULL APPROVAL via FCM
        notifyDeliveryPerson(saved, "Account Approved! 🎉",
                "Congratulations! Your account has been approved. You can now go online and start accepting orders.");

        return saved;
    }

    /**
     * Reject delivery person with remarks
     */
    public DeliveryPerson rejectDeliveryPerson(Long deliveryPersonId, Long adminId, String remarks) {
        DeliveryPerson deliveryPerson = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

        deliveryPerson.setApprovedByAdmin(false);
        deliveryPerson.setApprovalStatus(DeliveryPerson.ApprovalStatus.REJECTED);

        DeliveryPerson saved = deliveryPersonRepository.save(deliveryPerson);

        // Write RTDB status for real-time app notification
        firebaseService.writeDeliveryPartnerStatus(saved.getId(), "REJECTED");

        // Notify delivery person
        notifyDeliveryPerson(saved, "Account Rejected ❌", "Your application has been rejected. Reason: " + remarks);

        return saved;
    }

    /**
     * Admin updates personal + bank details for a delivery person
     */
    public DeliveryPerson updateDeliveryPersonDetails(Long deliveryPersonId, Map<String, Object> updates) {
        DeliveryPerson dp = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

        if (updates.containsKey("vehicleType")) {
            try {
                dp.setVehicleType(DeliveryPerson.VehicleType.valueOf(
                        updates.get("vehicleType").toString().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (updates.containsKey("vehicleModel") && updates.get("vehicleModel") != null)
            dp.setVehicleModel(updates.get("vehicleModel").toString());
        if (updates.containsKey("registrationNumber") && updates.get("registrationNumber") != null)
            dp.setRegistrationNumber(updates.get("registrationNumber").toString());
        if (updates.containsKey("accountName") && updates.get("accountName") != null)
            dp.setAccountName(updates.get("accountName").toString());
        if (updates.containsKey("accountNumber") && updates.get("accountNumber") != null)
            dp.setAccountNumber(updates.get("accountNumber").toString());
        if (updates.containsKey("bankName") && updates.get("bankName") != null)
            dp.setBankName(updates.get("bankName").toString());
        if (updates.containsKey("ifscCode") && updates.get("ifscCode") != null)
            dp.setIfscCode(updates.get("ifscCode").toString());

        return deliveryPersonRepository.save(dp);
    }

    /**
     * Update delivery person status (activate/deactivate)
     */
    public DeliveryPerson updateDeliveryPersonStatus(Long deliveryPersonId, Boolean isActive) {
        DeliveryPerson deliveryPerson = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

        deliveryPerson.setOnline(isActive);

        // Set a default Hyderabad location if going online and no GPS yet stored
        // This ensures the rider appears on the live map immediately
        if (isActive && (deliveryPerson.getLatitude() == null || deliveryPerson.getLongitude() == null)) {
            deliveryPerson.setLatitude(17.385044);
            deliveryPerson.setLongitude(78.486671);
        }

        return deliveryPersonRepository.save(deliveryPerson);
    }

    private DeliveryPerson.VehicleType getCanonicalBroadcastVehicleType(DeliveryPerson.VehicleType selected) {
        if (selected == DeliveryPerson.VehicleType.SCOOTY || selected == DeliveryPerson.VehicleType.EV) {
            return DeliveryPerson.VehicleType.BIKE;
        }
        return selected;
    }

    private Store resolvePrimaryStore(Order customerOrder) {
        Store primaryStore = customerOrder.getItems() == null ? null :
                customerOrder.getItems().stream()
                        .filter(item -> item.getVariant() != null
                                && item.getVariant().getProduct() != null
                                && item.getVariant().getProduct().getStore() != null)
                        .map(item -> item.getVariant().getProduct().getStore())
                        .findFirst()
                        .orElse(null);
        if (primaryStore == null) {
            primaryStore = storeRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No store configured in the system."));
        }
        return primaryStore;
    }

    private String buildDeliveryAddress(Order customerOrder) {
        if (customerOrder.getAddress() == null) {
            return "Address unavailable";
        }

        StringBuilder address = new StringBuilder();
        if (customerOrder.getAddress().getFlatNumber() != null && !customerOrder.getAddress().getFlatNumber().isBlank()) {
            address.append(customerOrder.getAddress().getFlatNumber());
        }
        if (customerOrder.getAddress().getAddressLine1() != null && !customerOrder.getAddress().getAddressLine1().isBlank()) {
            if (address.length() > 0) {
                address.append(", ");
            }
            address.append(customerOrder.getAddress().getAddressLine1());
        }
        if (customerOrder.getAddress().getCity() != null && !customerOrder.getAddress().getCity().isBlank()) {
            if (address.length() > 0) {
                address.append(", ");
            }
            address.append(customerOrder.getAddress().getCity());
        }
        if (customerOrder.getAddress().getPostalCode() != null && !customerOrder.getAddress().getPostalCode().isBlank()) {
            if (address.length() > 0) {
                address.append(" - ");
            }
            address.append(customerOrder.getAddress().getPostalCode());
        }
        return address.length() > 0 ? address.toString() : "Address unavailable";
    }

    private DeliveryOrder.PaymentType resolvePaymentType(Order customerOrder) {
        String paymentMethod = customerOrder.getPaymentMethod();
        if (paymentMethod == null) {
            return DeliveryOrder.PaymentType.ONLINE_PAID;
        }
        if ("COD".equalsIgnoreCase(paymentMethod) || "COD_WALLET".equalsIgnoreCase(paymentMethod)) {
            return DeliveryOrder.PaymentType.COD;
        }
        if ("UPI".equalsIgnoreCase(paymentMethod)) {
            return DeliveryOrder.PaymentType.UPI;
        }
        return DeliveryOrder.PaymentType.ONLINE_PAID;
    }

    private BigDecimal resolveDeliveryOrderAmount(Order customerOrder) {
        BigDecimal grandTotal = customerOrder.getGrandTotal() != null
                ? customerOrder.getGrandTotal() : BigDecimal.ZERO;
        String paymentMethod = customerOrder.getPaymentMethod() == null
                ? "" : customerOrder.getPaymentMethod().trim().toUpperCase();

        if (!"COD_WALLET".equals(paymentMethod) && !"ONLINE_WALLET".equals(paymentMethod)) {
            return grandTotal;
        }

        BigDecimal walletApplied = customerOrder.getWalletAmount() != null
                ? customerOrder.getWalletAmount() : BigDecimal.ZERO;
        BigDecimal remainingAmount = grandTotal.subtract(walletApplied);
        return remainingAmount.compareTo(BigDecimal.ZERO) > 0 ? remainingAmount : BigDecimal.ZERO;
    }

    private boolean isPaidOnlineForDelivery(Order customerOrder) {
        String paymentMethod = customerOrder.getPaymentMethod() == null
                ? "" : customerOrder.getPaymentMethod().trim().toUpperCase();
        return !("COD".equals(paymentMethod) || "COD_WALLET".equals(paymentMethod));
    }

    private Double calculateDistanceKm(Double pickupLat, Double pickupLon, Double deliveryLat, Double deliveryLon) {
        if (pickupLat == null || pickupLon == null || deliveryLat == null || deliveryLon == null) {
            return null;
        }

        final double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(deliveryLat - pickupLat);
        double dLon = Math.toRadians(deliveryLon - pickupLon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(pickupLat)) * Math.cos(Math.toRadians(deliveryLat))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double distanceKm = earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(distanceKm * 100.0) / 100.0;
    }

    private void populateRouteMetrics(DeliveryOrder deliveryOrder) {
        deliveryOrder.setDistanceKm(calculateDistanceKm(
                deliveryOrder.getPickupLatitude(),
                deliveryOrder.getPickupLongitude(),
                deliveryOrder.getDeliveryLatitude(),
                deliveryOrder.getDeliveryLongitude()));
    }

    private void applyEstimatedFare(DeliveryOrder deliveryOrder, DeliveryPerson.VehicleType vehicleType) {
        if (vehicleType == null || deliveryOrder.getDistanceKm() == null) {
            return;
        }

        try {
            deliveryOrder.setDeliveryFee(
                    fareRuleService.calculateDeliveryCharge(vehicleType, deliveryOrder.getDistanceKm()));
        } catch (Exception ignored) {
            // Keep the existing fee if fare rules are not configured for this vehicle type yet.
        }
    }

    private DeliveryOrder getOrCreateDeliveryOrder(Order customerOrder) {
        DeliveryOrder deliveryOrder = deliveryOrderRepository.findByOrderNumber(customerOrder.getOrderNumber())
                .orElseGet(DeliveryOrder::new);

        Store primaryStore = resolvePrimaryStore(customerOrder);

        deliveryOrder.setOrderNumber(customerOrder.getOrderNumber());
        String customerName = customerOrder.getCustomer() != null && customerOrder.getCustomer().getName() != null
                ? customerOrder.getCustomer().getName()
                : "Customer";
        String customerPhone = customerOrder.getCustomer() != null && customerOrder.getCustomer().getPhoneNumber() != null
                ? customerOrder.getCustomer().getPhoneNumber()
                : "";

        deliveryOrder.setCustomerName(customerName);
        deliveryOrder.setCustomerPhone(customerPhone);
        deliveryOrder.setPickupAddress(primaryStore.getName() + ", " +
                (primaryStore.getAddress() != null ? primaryStore.getAddress() : ""));
        deliveryOrder.setDeliveryAddress(buildDeliveryAddress(customerOrder));
        deliveryOrder.setStore(primaryStore);
        deliveryOrder.setPickupLatitude(primaryStore.getLatitude());
        deliveryOrder.setPickupLongitude(primaryStore.getLongitude());

        if (customerOrder.getAddress() != null) {
            deliveryOrder.setDeliveryLatitude(customerOrder.getAddress().getLatitude());
            deliveryOrder.setDeliveryLongitude(customerOrder.getAddress().getLongitude());
        }

        populateRouteMetrics(deliveryOrder);

        deliveryOrder.setOrderAmount(resolveDeliveryOrderAmount(customerOrder));
        deliveryOrder.setDeliveryFee(
                customerOrder.getDeliveryCharge() != null ? customerOrder.getDeliveryCharge() : BigDecimal.ZERO);
        deliveryOrder.setPaymentType(resolvePaymentType(customerOrder));
        deliveryOrder.setPaidOnline(isPaidOnlineForDelivery(customerOrder));

        return deliveryOrderRepository.save(deliveryOrder);
    }

    /**
     * Assign order to delivery person
     */
    @org.springframework.transaction.annotation.Transactional(noRollbackFor = Exception.class)
    public DeliveryOrder assignOrderToDeliveryPerson(Long orderId, Long deliveryPersonId) {
        Order customerOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Customer Order not found"));

        DeliveryPerson deliveryPerson = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));

        if (!deliveryPerson.isOnline() || !deliveryPerson.isApprovedByAdmin()) {
            throw new IllegalArgumentException("Delivery person is not available for assignment");
        }

        DeliveryOrder order = getOrCreateDeliveryOrder(customerOrder);

        if (order.getDeliveryPerson() != null && !order.getDeliveryPerson().getId().equals(deliveryPersonId)) {
            throw new IllegalArgumentException("Order is already assigned to someone else");
        }

        order.setDeliveryPerson(deliveryPerson);
        order.setStatus(DeliveryOrder.OrderStatus.RIDER_ASSIGNED);
        LocalDateTime acceptedNow = LocalDateTime.now();
        order.setAssignedAt(acceptedNow);
        order.setAcceptedAt(acceptedNow);

        populateRouteMetrics(order);
        applyEstimatedFare(order, deliveryPerson.getVehicleType());

        // Update the main Customer Order status so the Admin Panel sees it immediately!
        customerOrder.setOrderStatus("assigned");
        orderRepository.save(customerOrder);

        // Generate pickup OTP
        String pickupOtp = generateOtp();
        order.setPickupOtp(pickupOtp);
        order.setPickupOtpExpiry(LocalDateTime.now().plusMinutes(30));

        DeliveryOrder savedOrder = deliveryOrderRepository.save(order);

        // Send Push Notification to All registered device tokens for this Delivery
        // Person
        try {
            if (deliveryPerson.getUserMain() != null) {
                java.util.List<String> tokens = userMainService.getFcmTokensForUser(deliveryPerson.getUserMain());

                if (!tokens.isEmpty()) {
                    String title = "New Order Assigned! 📦";
                    String body = "You have been assigned order #" + savedOrder.getOrderNumber()
                            + ". Please check your active orders.";

                    java.util.Map<String, String> data = new java.util.HashMap<>();
                    data.put("orderId", savedOrder.getId().toString());
                    data.put("orderNumber", savedOrder.getOrderNumber());
                    data.put("type", "ORDER_ASSIGNED");

                    notifyDeliveryPerson(
                            deliveryPerson,
                            "ORDER_ASSIGNED",
                            "OrderDetail",
                            title,
                            body,
                            savedOrder.getId(),
                            savedOrder.getOrderNumber(),
                            null,
                            data
                    );
                }
            }
        } catch (Exception e) {
            // Logs error but doesn't fail the transaction
            System.err.println("Failed to send assignment notification: " + e.getMessage());
        }

        return savedOrder;
    }

    /**
     * Get pending orders for assignment
     */
    public List<DeliveryOrder> getPendingOrdersForAssignment() {
        return deliveryOrderRepository.findPendingOrdersForAssignment();
    }

    /**
     * Get all orders
     */
    public List<DeliveryOrder> getAllOrders() {
        return deliveryOrderRepository.findAll();
    }

    /**
     * Get orders by status
     */
    public List<DeliveryOrder> getOrdersByStatus(DeliveryOrder.OrderStatus status) {
        return deliveryOrderRepository.findByStatus(status);
    }

    /**
     * Generate OTP
     */
    private String generateOtp() {
        return String.format("%04d", (int) (Math.random() * 10000));
    }

    private String generateCustomerDeliveryOtp() {
        return String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * Generate customer delivery OTP from admin panel.
     * Accepts the customer order id used by the admin order screen and resolves
     * the linked delivery order by order number.
     */
    public DeliveryOrder generateDeliveryOtp(Long orderId) {
        Order customerOrder = orderRepository.findById(orderId).orElse(null);

        DeliveryOrder order;
        if (customerOrder != null) {
            order = deliveryOrderRepository.findByOrderNumber(customerOrder.getOrderNumber())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Delivery order not found for customer order: " + orderId));
        } else {
            order = deliveryOrderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        }

        if (order.getDeliveryPerson() == null) {
            throw new IllegalArgumentException("Assign a rider before generating customer OTP");
        }

        if (order.getStatus() == DeliveryOrder.OrderStatus.DELIVERED
                || order.getStatus() == DeliveryOrder.OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot generate customer OTP for a completed order");
        }

        String deliveryOtp = generateCustomerDeliveryOtp();
        order.setDeliveryOtp(deliveryOtp);
        order.setDeliveryOtpExpiry(LocalDateTime.now().plusHours(2));
        order.setDeliveryOtpVerifiedAt(null);

        try {
            userMainService.findByPhoneNumber(order.getCustomerPhone()).ifPresent(userMain -> {
                appNotificationService.notifyUser(
                        userMain,
                        "DELIVERY_OTP",
                        "OrderTracking",
                        "Your Delivery OTP",
                        "Share this OTP with the rider to confirm delivery: " + deliveryOtp
                                + " (Order: " + order.getOrderNumber() + ")",
                        customerOrder != null ? customerOrder.getId() : null,
                        order.getOrderNumber(),
                        null
                );
            });
        } catch (Exception e) {
            System.err.println("Failed to notify customer about delivery OTP: " + e.getMessage());
        }

        return deliveryOrderRepository.save(order);
    }

    /**
     * Get dashboard statistics (legacy for delivery admin)
     */
    public Map<String, Object> getDashboardStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalDeliveryPersons", deliveryPersonRepository.count());
        stats.put("approvedDeliveryPersons", deliveryPersonRepository.countApprovedDeliveryPersons());
        stats.put("onlineDeliveryPersons", deliveryPersonRepository.countOnlineDeliveryPersons());
        stats.put("activeDeliveryPersons", deliveryPersonRepository.countActiveDeliveryPersons());
        stats.put("pendingApprovals", getDeliveryPersonsPendingApproval().size());
        stats.put("pendingDocuments", getDocumentsPendingReview().size());
        stats.put("totalOrders", deliveryOrderRepository.count());
        stats.put("pendingOrders", deliveryOrderRepository.countOrdersByStatus(DeliveryOrder.OrderStatus.BROADCASTED_TO_RIDERS));
        stats.put("assignedOrders", deliveryOrderRepository.countOrdersByStatus(DeliveryOrder.OrderStatus.RIDER_ASSIGNED));
        stats.put("pickedUpOrders", deliveryOrderRepository.countOrdersByStatus(DeliveryOrder.OrderStatus.PICKED_UP));
        stats.put("deliveredOrders", deliveryOrderRepository.countOrdersByStatus(DeliveryOrder.OrderStatus.DELIVERED));

        return stats;
    }

    /**
     * Comprehensive dashboard summary with period-based order + income breakdowns.
     * Returns everything the admin dashboard needs in one call.
     */
    public Map<String, Object> getDashboardSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart  = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime todayEnd    = now.withHour(23).withMinute(59).withSecond(59).withNano(999999999);
        LocalDateTime weekStart   = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                       .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthStart  = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        // ── Order counts ──────────────────────────────────────────────────
        long totalOrders    = orderRepository.count();
        long todayOrders    = orderRepository.countByPlacedAtBetween(todayStart, todayEnd);
        long weekOrders     = orderRepository.countByPlacedAtBetween(weekStart, now);
        long monthOrders    = orderRepository.countByPlacedAtBetween(monthStart, now);

        // ── Cancelled counts ──────────────────────────────────────────────
        long cancelledTotal = orderRepository.countCancelledOrders();
        long cancelledToday = orderRepository.countCancelledByPeriod(todayStart, todayEnd);
        long cancelledWeek  = orderRepository.countCancelledByPeriod(weekStart, now);
        long cancelledMonth = orderRepository.countCancelledByPeriod(monthStart, now);

        // ── Delivered counts ──────────────────────────────────────────────
        long deliveredTotal = orderRepository.countByOrderStatus("delivered");
        long deliveredToday = orderRepository.countByPlacedAtBetweenAndStatus(todayStart, todayEnd, "delivered");
        long deliveredWeek  = orderRepository.countByPlacedAtBetweenAndStatus(weekStart, now, "delivered");
        long deliveredMonth = orderRepository.countByPlacedAtBetweenAndStatus(monthStart, now, "delivered");

        // ── Revenue: Razorpay + COD ───────────────────────────────────────
        BigDecimal razorpayTotal  = zeroAmount(paymentTransactionRepository.sumAmountByStatus("SUCCESS"));
        BigDecimal razorpayToday  = zeroAmount(paymentTransactionRepository.sumAmountByStatusAndCreatedAtBetween("SUCCESS", todayStart, todayEnd));
        BigDecimal razorpayWeek   = zeroAmount(paymentTransactionRepository.sumAmountByStatusAndCreatedAtBetween("SUCCESS", weekStart, now));
        BigDecimal razorpayMonth  = zeroAmount(paymentTransactionRepository.sumAmountByStatusAndCreatedAtBetween("SUCCESS", monthStart, now));

        BigDecimal codTotal  = zeroAmount(orderRepository.sumGrandTotalByPaymentMethodAndOrderStatus("COD", "delivered"));
        BigDecimal codToday  = zeroAmount(orderRepository.sumGrandTotalByPaymentMethodAndOrderStatusBetween("COD", "delivered", todayStart, todayEnd));
        BigDecimal codWeek   = zeroAmount(orderRepository.sumGrandTotalByPaymentMethodAndOrderStatusBetween("COD", "delivered", weekStart, now));
        BigDecimal codMonth  = zeroAmount(orderRepository.sumGrandTotalByPaymentMethodAndOrderStatusBetween("COD", "delivered", monthStart, now));

        BigDecimal totalIncome = razorpayTotal.add(codTotal);
        BigDecimal todayIncome = razorpayToday.add(codToday);
        BigDecimal weekIncome  = razorpayWeek.add(codWeek);
        BigDecimal monthIncome = razorpayMonth.add(codMonth);

        // ── Users / customers ─────────────────────────────────────────────
        long activeUsers       = userMainRepository.count();
        long newCustomersToday = customerRepository.countByCreatedAtAfter(todayStart);

        // ── Delivery stats ────────────────────────────────────────────────
        long totalDeliveryPersons   = deliveryPersonRepository.count();
        long approvedDeliveryPersons = deliveryPersonRepository.countApprovedDeliveryPersons();
        long onlineDeliveryPersons  = deliveryPersonRepository.countOnlineDeliveryPersons();
        long pendingApprovals       = deliveryPersonRepository.findPendingApproval().size();

        long deliveryOrdersTotal    = deliveryOrderRepository.count();
        long deliveryOrdersActive   = deliveryOrderRepository.countOrdersByStatus(DeliveryOrder.OrderStatus.OUT_FOR_DELIVERY)
                + deliveryOrderRepository.countOrdersByStatus(DeliveryOrder.OrderStatus.RIDER_ASSIGNED)
                + deliveryOrderRepository.countOrdersByStatus(DeliveryOrder.OrderStatus.PICKED_UP);
        long deliveryOrdersCompleted = deliveryOrderRepository.countOrdersByStatus(DeliveryOrder.OrderStatus.DELIVERED);

        Map<String, Object> summary = new HashMap<>();

        // Orders
        summary.put("totalOrders",    totalOrders);
        summary.put("todayOrders",    todayOrders);
        summary.put("weekOrders",     weekOrders);
        summary.put("monthOrders",    monthOrders);

        // Cancelled
        summary.put("cancelledTotal", cancelledTotal);
        summary.put("cancelledToday", cancelledToday);
        summary.put("cancelledWeek",  cancelledWeek);
        summary.put("cancelledMonth", cancelledMonth);

        // Delivered
        summary.put("deliveredTotal", deliveredTotal);
        summary.put("deliveredToday", deliveredToday);
        summary.put("deliveredWeek",  deliveredWeek);
        summary.put("deliveredMonth", deliveredMonth);

        // Income
        summary.put("totalIncome",    totalIncome);
        summary.put("todayIncome",    todayIncome);
        summary.put("weekIncome",     weekIncome);
        summary.put("monthIncome",    monthIncome);
        summary.put("razorpayIncome", razorpayTotal);
        summary.put("codIncome",      codTotal);

        // Legacy field (kept for backward compat)
        summary.put("todayRevenue",   todayIncome);

        // Users
        summary.put("activeUsers",    activeUsers);
        summary.put("newCustomers",   newCustomersToday);

        // Delivery
        summary.put("totalDeliveryPersons",    totalDeliveryPersons);
        summary.put("approvedDeliveryPersons", approvedDeliveryPersons);
        summary.put("onlineDeliveryPersons",   onlineDeliveryPersons);
        summary.put("pendingApprovals",        pendingApprovals);
        summary.put("deliveryOrdersTotal",     deliveryOrdersTotal);
        summary.put("deliveryOrdersActive",    deliveryOrdersActive);
        summary.put("deliveryOrdersCompleted", deliveryOrdersCompleted);

        return summary;
    }

    /**
     * Get active users count
     */
    public Map<String, Long> getActiveUsersCount() {
        return Map.of("count", userMainRepository.count());
    }

    /**
     * Get recent orders
     */
    public List<Order> getRecentOrders() {
        return orderRepository.findTop10ByOrderByPlacedAtDesc();
    }

    /**
     * Get order analytics by status — now properly period-aware.
     */
    public Map<String, Long> getOrderAnalytics(String period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = resolvePeriodStart(period, now);

        Map<String, Long> analytics = new HashMap<>();
        for (String status : List.of("placed", "confirmed", "assigned", "delivered", "cancelled", "rejected", "processing")) {
            analytics.put(status, orderRepository.countByPlacedAtBetweenAndStatus(start, now, status));
        }
        analytics.put("total", orderRepository.countByPlacedAtBetween(start, now));
        return analytics;
    }

    public Map<String, Object> getProductPerformance(String period) {
        LocalDateTime now = LocalDateTime.now();
        String normalizedPeriod = normalizePeriod(period);
        LocalDateTime start = resolvePeriodStart(normalizedPeriod, now);

        List<OrderItem> deliveredItems = orderItemRepository.findDeliveredOrderItemsBetween(start, now);
        Map<Long, ProductPerformanceAccumulator> groupedProducts = new HashMap<>();
        Set<Long> deliveredOrderIds = new HashSet<>();
        long totalUnitsSold = 0L;
        BigDecimal totalProductRevenue = BigDecimal.ZERO;

        for (OrderItem item : deliveredItems) {
            if (item == null) {
                continue;
            }

            com.project.Anusha.model.Product product =
                    item.getVariant() != null ? item.getVariant().getProduct() : null;
            Long productId = product != null && product.getId() != null
                    ? product.getId()
                    : -(item.getId() != null ? item.getId() : 0L);
            String productName = item.getProductName() != null && !item.getProductName().isBlank()
                    ? item.getProductName()
                    : product != null && product.getName() != null ? product.getName() : "Unknown Product";
            String imageUrl = item.getImageUrl() != null && !item.getImageUrl().isBlank()
                    ? item.getImageUrl()
                    : product != null ? product.getImageUrl() : null;
            String storeName = product != null && product.getStore() != null && product.getStore().getName() != null
                    ? product.getStore().getName()
                    : "Unknown Store";

            ProductPerformanceAccumulator accumulator = groupedProducts.computeIfAbsent(
                    productId,
                    ignored -> new ProductPerformanceAccumulator(productId, productName, imageUrl, storeName)
            );

            int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
            BigDecimal lineRevenue = item.getTotalPrice() != null
                    ? item.getTotalPrice()
                    : (item.getUnitPrice() != null
                    ? item.getUnitPrice().multiply(BigDecimal.valueOf(quantity))
                    : BigDecimal.ZERO);

            accumulator.add(quantity, lineRevenue, item.getOrder() != null ? item.getOrder().getId() : null);
            totalUnitsSold += quantity;
            totalProductRevenue = totalProductRevenue.add(lineRevenue);
            if (item.getOrder() != null && item.getOrder().getId() != null) {
                deliveredOrderIds.add(item.getOrder().getId());
            }
        }

        List<ProductPerformanceAccumulator> sortedProducts = groupedProducts.values().stream()
                .sorted(Comparator.comparingLong(ProductPerformanceAccumulator::getUnitsSold).reversed()
                        .thenComparing(ProductPerformanceAccumulator::getRevenue, Comparator.reverseOrder())
                        .thenComparing(ProductPerformanceAccumulator::getProductName))
                .toList();

        List<Map<String, Object>> topProducts = new ArrayList<>();
        int rank = 1;
        for (ProductPerformanceAccumulator product : sortedProducts.stream().limit(20).toList()) {
            Map<String, Object> row = new HashMap<>();
            row.put("rank", rank++);
            row.put("productId", product.getProductId());
            row.put("productName", product.getProductName());
            row.put("imageUrl", product.getImageUrl());
            row.put("storeName", product.getStoreName());
            row.put("unitsSold", product.getUnitsSold());
            row.put("revenue", product.getRevenue());
            row.put("orderCount", product.getOrderCount());
            topProducts.add(row);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("productsCount", groupedProducts.size());
        summary.put("unitsSold", totalUnitsSold);
        summary.put("productRevenue", totalProductRevenue);
        summary.put("deliveredOrders", deliveredOrderIds.size());

        Map<String, Object> response = new HashMap<>();
        response.put("period", normalizedPeriod);
        response.put("from", start);
        response.put("to", now);
        response.put("summary", summary);
        response.put("topProducts", topProducts);
        return response;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Vehicle-type first-come-first-serve broadcast
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the set of vehicle types that receive the same broadcast.
     *
     * Admin UI has THREE categories:
     *   BIKE  → BIKE + SCOOTY + EV  (all light two-wheelers)
     *   AUTO  → AUTO only
     *   HEAVY → HEAVY only
     */
    private java.util.Set<DeliveryPerson.VehicleType> getBroadcastGroup(DeliveryPerson.VehicleType selected) {
        if (selected == DeliveryPerson.VehicleType.BIKE
                || selected == DeliveryPerson.VehicleType.SCOOTY
                || selected == DeliveryPerson.VehicleType.EV) {
            return java.util.EnumSet.of(
                    DeliveryPerson.VehicleType.BIKE,
                    DeliveryPerson.VehicleType.SCOOTY,
                    DeliveryPerson.VehicleType.EV);
        }
        return java.util.EnumSet.of(selected);
    }

    /**
     * Broadcast an order to all online delivery persons in the vehicle group.
     * BIKE / SCOOTY / EV are treated as one group — all three receive the notification.
     * AUTO and HEAVY each receive it alone.
     */
    @Transactional
    public DeliveryBroadcast broadcastOrderToVehicleType(Long orderId, String vehicleTypeStr) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        DeliveryPerson.VehicleType vehicleType;
        try {
            vehicleType = DeliveryPerson.VehicleType.valueOf(vehicleTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid vehicle type: " + vehicleTypeStr +
                    ". Must be one of: BIKE, AUTO, HEAVY");
        }

        // Normalise: always store BIKE as the canonical key for the light-vehicle group
        DeliveryPerson.VehicleType canonicalType = getCanonicalBroadcastVehicleType(vehicleType);

        java.util.Set<DeliveryPerson.VehicleType> group = getBroadcastGroup(vehicleType);
        DeliveryOrder deliveryOrder = getOrCreateDeliveryOrder(order);
        deliveryOrder.setDeliveryPerson(null);
        deliveryOrder.setAssignedAt(null);
        deliveryOrder.setAcceptedAt(null);
        deliveryOrder.setPickedUpAt(null);
        deliveryOrder.setDeliveredAt(null);
        deliveryOrder.setDeliveryOtp(null);
        deliveryOrder.setDeliveryOtpExpiry(null);
        deliveryOrder.setDeliveryOtpVerifiedAt(null);
        deliveryOrder.setDeliveryPhotoUrl(null);
        deliveryOrder.setRejectionReason(null);
        deliveryOrder.setStatus(DeliveryOrder.OrderStatus.BROADCASTED_TO_RIDERS);
        populateRouteMetrics(deliveryOrder);
        applyEstimatedFare(deliveryOrder, canonicalType);
        deliveryOrder = deliveryOrderRepository.save(deliveryOrder);

        // Cancel any previous pending broadcasts for this order
        broadcastRepository.findPendingByOrderId(orderId).forEach(b -> {
            b.setStatus(DeliveryBroadcast.BroadcastStatus.CANCELLED);
            broadcastRepository.save(b);
        });

        DeliveryBroadcast broadcast = new DeliveryBroadcast(order, canonicalType);
        broadcast = broadcastRepository.save(broadcast);

        // Notify all online + approved riders in the vehicle group
        List<DeliveryPerson> riders = deliveryPersonRepository
                .findByIsOnlineTrueAndIsApprovedByAdminTrueAndIsVerifiedTrue()
                .stream()
                .filter(dp -> group.contains(dp.getVehicleType()))
                .collect(java.util.stream.Collectors.toList());

        final Long broadcastId = broadcast.getId();
        for (DeliveryPerson rider : riders) {
            try {
                if (rider.getUserMain() != null) {
                    List<String> tokens = userMainService.getFcmTokensForUser(rider.getUserMain());
                    if (!tokens.isEmpty()) {
                        String title = "New Order Available! \uD83D\uDCE6";
                        String storeName = deliveryOrder.getStore() != null && deliveryOrder.getStore().getName() != null
                                ? deliveryOrder.getStore().getName()
                                : "store";
                        String body = "Order #" + order.getOrderNumber() + " is available from " + storeName + ". Tap to accept!";
                        java.util.Map<String, String> data = new java.util.HashMap<>();
                        data.put("broadcastId", broadcastId.toString());
                        data.put("orderId", deliveryOrder.getId().toString());
                        data.put("deliveryOrderId", deliveryOrder.getId().toString());
                        data.put("customerOrderId", orderId.toString());
                        data.put("orderNumber", deliveryOrder.getOrderNumber());
                        data.put("type", "NEW_DELIVERY_ORDER");
                        data.put("vehicleType", vehicleType.name());
                        data.put("pickup", deliveryOrder.getPickupAddress());
                        data.put("delivery", deliveryOrder.getDeliveryAddress());
                        data.put("amount", deliveryOrder.getOrderAmount() != null
                                ? deliveryOrder.getOrderAmount().toString()
                                : "0");
                        data.put("deliveryFee", deliveryOrder.getDeliveryFee() != null
                                ? deliveryOrder.getDeliveryFee().toString()
                                : "0");
                        data.put("distanceKm", deliveryOrder.getDistanceKm() != null
                                ? deliveryOrder.getDistanceKm().toString()
                                : "");
                        data.put("customerName", deliveryOrder.getCustomerName() != null
                                ? deliveryOrder.getCustomerName()
                                : "");
                        if (deliveryOrder.getStore() != null) {
                            data.put("storeName", deliveryOrder.getStore().getName() != null
                                    ? deliveryOrder.getStore().getName()
                                    : "");
                            data.put("storePhone", deliveryOrder.getStore().getPhoneNumber() != null
                                    ? deliveryOrder.getStore().getPhoneNumber()
                                    : "");
                            data.put("storeLatitude", deliveryOrder.getStore().getLatitude() != null
                                    ? deliveryOrder.getStore().getLatitude().toString()
                                    : "");
                            data.put("storeLongitude", deliveryOrder.getStore().getLongitude() != null
                                    ? deliveryOrder.getStore().getLongitude().toString()
                                    : "");
                        }
                        notifyDeliveryPerson(
                                rider,
                                "NEW_DELIVERY_ORDER",
                                "OrderDetail",
                                title,
                                body,
                                deliveryOrder.getId(),
                                deliveryOrder.getOrderNumber(),
                                null,
                                data
                        );
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to notify rider " + rider.getId() + ": " + e.getMessage());
            }
        }

        return broadcast;
    }

    /**
     * Delivery person accepts a broadcast — first-come-first-serve.
     * If broadcast is still PENDING, assigns the order to this rider and marks broadcast ACCEPTED.
     * Returns the resulting DeliveryOrder.
     */
    @Transactional
    public synchronized DeliveryOrder acceptBroadcast(Long broadcastId, Long deliveryPersonId) {
        DeliveryBroadcast broadcast = broadcastRepository.findById(broadcastId)
                .orElseThrow(() -> new IllegalArgumentException("Broadcast not found: " + broadcastId));

        if (broadcast.getStatus() != DeliveryBroadcast.BroadcastStatus.PENDING) {
            throw new IllegalStateException("This order has already been taken or expired.");
        }
        if (broadcast.getExpiresAt().isBefore(LocalDateTime.now())) {
            broadcast.setStatus(DeliveryBroadcast.BroadcastStatus.EXPIRED);
            broadcastRepository.save(broadcast);
            throw new IllegalStateException("This broadcast has expired.");
        }

        DeliveryPerson deliveryPerson = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found: " + deliveryPersonId));

        DeliveryPerson.VehicleType riderCanonicalType = getCanonicalBroadcastVehicleType(deliveryPerson.getVehicleType());
        if (riderCanonicalType != broadcast.getVehicleType()) {
            throw new IllegalArgumentException("Your vehicle type does not match this broadcast.");
        }

        // Mark broadcast as accepted immediately (prevents race condition)
        broadcast.setStatus(DeliveryBroadcast.BroadcastStatus.ACCEPTED);
        broadcast.setAcceptedBy(deliveryPerson);
        broadcast.setAcceptedAt(LocalDateTime.now());
        broadcastRepository.save(broadcast);

        // Now assign the order through existing logic
        DeliveryOrder deliveryOrder = assignOrderToDeliveryPerson(broadcast.getOrder().getId(), deliveryPersonId);

        // Notify other online riders of same type that order was taken
        notifyBroadcastClosed(broadcast, deliveryPerson);

        return deliveryOrder;
    }

    /**
     * Get available broadcasts for a delivery person.
     * BIKE/SCOOTY/EV riders all see the same BIKE-group broadcasts.
     */
    public List<DeliveryBroadcast> getAvailableBroadcastsForRider(Long deliveryPersonId) {
        DeliveryPerson dp = deliveryPersonRepository.findById(deliveryPersonId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery person not found"));
        // All light-vehicle riders look up by canonical type BIKE
        DeliveryPerson.VehicleType lookupType = getCanonicalBroadcastVehicleType(dp.getVehicleType());
        return broadcastRepository.findAvailableByVehicleType(lookupType, LocalDateTime.now()).stream()
                .filter(broadcast -> deliveryOrderRepository.findByOrderNumber(broadcast.getOrder().getOrderNumber())
                        .map(order -> order.getStatus() == DeliveryOrder.OrderStatus.BROADCASTED_TO_RIDERS
                                && order.getDeliveryPerson() == null)
                        .orElse(false))
                .collect(java.util.stream.Collectors.toList());
    }

    private void notifyBroadcastClosed(DeliveryBroadcast broadcast, DeliveryPerson acceptedBy) {
        java.util.Set<DeliveryPerson.VehicleType> group = getBroadcastGroup(broadcast.getVehicleType());
        // Notify all other riders in the same group that the order is gone
        deliveryPersonRepository.findByIsOnlineTrueAndIsApprovedByAdminTrueAndIsVerifiedTrue()
                .stream()
                .filter(dp -> group.contains(dp.getVehicleType())
                        && !dp.getId().equals(acceptedBy.getId()))
                .forEach(dp -> {
                    try {
                        if (dp.getUserMain() != null) {
                            List<String> tokens = userMainService.getFcmTokensForUser(dp.getUserMain());
                            if (!tokens.isEmpty()) {
                                java.util.Map<String, String> data = new java.util.HashMap<>();
                                data.put("broadcastId", broadcast.getId().toString());
                                data.put("type", "ORDER_BROADCAST_CLOSED");
                                data.put("orderNumber", broadcast.getOrder().getOrderNumber());
                                deliveryOrderRepository.findByOrderNumber(broadcast.getOrder().getOrderNumber())
                                        .ifPresent(closedOrder -> data.put("orderId", closedOrder.getId().toString()));
                                notifyDeliveryPerson(
                                        dp,
                                        "ORDER_BROADCAST_CLOSED",
                                        "Notifications",
                                        "Order Taken",
                                        "Order #" + broadcast.getOrder().getOrderNumber() + " was accepted by another rider.",
                                        deliveryOrderRepository.findByOrderNumber(broadcast.getOrder().getOrderNumber())
                                                .map(DeliveryOrder::getId)
                                                .orElse(null),
                                        broadcast.getOrder().getOrderNumber(),
                                        null,
                                        data
                                );
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to notify broadcast close: " + e.getMessage());
                    }
                });
    }

    // ──────────────────────────────────────────────────────────────────────
    // Store dashboard — store-wise orders and income
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns all stores with their order count, delivered count, and total income.
     */
    public List<Map<String, Object>> getStoreDashboard() {
        List<Store> stores = storeRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Store store : stores) {
            Map<String, Object> row = buildStoreStats(store);
            result.add(row);
        }

        return result;
    }

    /**
     * Returns order list for a specific store (orders that have at least one item from this store).
     */
    public List<Map<String, Object>> getOrdersByStore(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        return orderRepository.findAll().stream()
                .filter(order -> orderBelongsToStore(order, storeId))
                .map(order -> buildOrderSummary(order, store))
                .sorted(java.util.Comparator.comparing(m -> ((java.time.LocalDateTime) m.get("placedAt")), java.util.Comparator.reverseOrder()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Returns income summary for a specific store.
     */
    public Map<String, Object> getStoreIncome(Long storeId) {
        storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));

        List<Order> storeOrders = orderRepository.findAll().stream()
                .filter(o -> orderBelongsToStore(o, storeId) && "delivered".equalsIgnoreCase(o.getOrderStatus()))
                .collect(java.util.stream.Collectors.toList());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        BigDecimal todayIncome = storeOrders.stream()
                .filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(todayStart))
                .map(o -> storeSubtotalFromOrder(o, storeId))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal weekIncome = storeOrders.stream()
                .filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(weekStart))
                .map(o -> storeSubtotalFromOrder(o, storeId))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthIncome = storeOrders.stream()
                .filter(o -> o.getDeliveredAt() != null && !o.getDeliveredAt().isBefore(monthStart))
                .map(o -> storeSubtotalFromOrder(o, storeId))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalIncome = storeOrders.stream()
                .map(o -> storeSubtotalFromOrder(o, storeId))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> summary = new HashMap<>();
        summary.put("storeId", storeId);
        summary.put("todayIncome", todayIncome);
        summary.put("weekIncome", weekIncome);
        summary.put("monthIncome", monthIncome);
        summary.put("totalIncome", totalIncome);
        summary.put("deliveredOrders", storeOrders.size());
        return summary;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private boolean orderBelongsToStore(Order order, Long storeId) {
        return order.getItems() != null && order.getItems().stream().anyMatch(item ->
                item.getVariant() != null
                && item.getVariant().getProduct() != null
                && item.getVariant().getProduct().getStore() != null
                && storeId.equals(item.getVariant().getProduct().getStore().getId()));
    }

    private BigDecimal storeSubtotalFromOrder(Order order, Long storeId) {
        if (order.getItems() == null) return BigDecimal.ZERO;
        return order.getItems().stream()
                .filter(item -> item.getVariant() != null
                        && item.getVariant().getProduct() != null
                        && item.getVariant().getProduct().getStore() != null
                        && storeId.equals(item.getVariant().getProduct().getStore().getId()))
                .map(item -> item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, Object> buildStoreStats(Store store) {
        List<Order> allOrders = orderRepository.findAll();
        long totalOrders = allOrders.stream().filter(o -> orderBelongsToStore(o, store.getId())).count();
        long deliveredOrders = allOrders.stream()
                .filter(o -> orderBelongsToStore(o, store.getId()) && "delivered".equalsIgnoreCase(o.getOrderStatus()))
                .count();
        BigDecimal totalIncome = allOrders.stream()
                .filter(o -> orderBelongsToStore(o, store.getId()) && "delivered".equalsIgnoreCase(o.getOrderStatus()))
                .map(o -> storeSubtotalFromOrder(o, store.getId()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> row = new HashMap<>();
        row.put("storeId", store.getId());
        row.put("storeName", store.getName());
        row.put("storePhone", store.getPhoneNumber());
        row.put("imageUrl", store.getImageUrl());
        row.put("active", Boolean.TRUE.equals(store.getActive()));
        row.put("totalOrders", totalOrders);
        row.put("deliveredOrders", deliveredOrders);
        row.put("totalIncome", totalIncome);
        return row;
    }

    private Map<String, Object> buildOrderSummary(Order order, Store store) {
        Map<String, Object> row = new HashMap<>();
        row.put("orderId", order.getId());
        row.put("orderNumber", order.getOrderNumber());
        row.put("customerName", order.getCustomer() != null ? order.getCustomer().getName() : "");
        row.put("customerPhone", order.getCustomer() != null ? order.getCustomer().getPhoneNumber() : "");
        row.put("storeSubtotal", storeSubtotalFromOrder(order, store.getId()));
        row.put("orderStatus", order.getOrderStatus());
        row.put("paymentStatus", order.getPaymentStatus());
        row.put("placedAt", order.getPlacedAt());
        return row;
    }

    private BigDecimal zeroAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private String normalizePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "UNKNOWN";
        }
        if ("ONLINE".equalsIgnoreCase(paymentMethod)) {
            return "RAZORPAY";
        }
        return paymentMethod.toUpperCase();
    }

    private String normalizePeriod(String period) {
        return period == null || period.isBlank() ? "all" : period.trim().toLowerCase();
    }

    private LocalDateTime resolvePeriodStart(String period, LocalDateTime now) {
        return switch (normalizePeriod(period)) {
            case "today", "day" -> now.withHour(0).withMinute(0).withSecond(0).withNano(0);
            case "week", "weekly" -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .withHour(0).withMinute(0).withSecond(0).withNano(0);
            case "month", "monthly" -> now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            default -> LocalDateTime.of(2000, 1, 1, 0, 0);
        };
    }

    private void notifyDeliveryPerson(DeliveryPerson dp, String title, String body) {
        notifyDeliveryPerson(dp, "SYSTEM", "Notifications", title, body, null, null, null, null);
    }

    private void notifyDeliveryPerson(
            DeliveryPerson dp,
            String type,
            String screen,
            String title,
            String body,
            Long orderId,
            String orderNumber,
            String targetId,
            Map<String, String> extraData
    ) {
        try {
            if (dp != null && dp.getUserMain() != null) {
                appNotificationService.notifyUser(
                        dp.getUserMain(),
                        type,
                        screen,
                        title,
                        body,
                        orderId,
                        orderNumber,
                        targetId,
                        extraData
                );
            }
        } catch (Exception e) {
            System.err.println("Failed to send notification to delivery person: " + e.getMessage());
        }
    }

    private static final class ProductPerformanceAccumulator {
        private final Long productId;
        private final String productName;
        private final String imageUrl;
        private final String storeName;
        private long unitsSold;
        private BigDecimal revenue = BigDecimal.ZERO;
        private final Set<Long> orderIds = new HashSet<>();

        private ProductPerformanceAccumulator(Long productId, String productName, String imageUrl, String storeName) {
            this.productId = productId;
            this.productName = productName;
            this.imageUrl = imageUrl;
            this.storeName = storeName;
        }

        private void add(int quantity, BigDecimal lineRevenue, Long orderId) {
            this.unitsSold += quantity;
            this.revenue = this.revenue.add(lineRevenue != null ? lineRevenue : BigDecimal.ZERO);
            if (orderId != null) {
                this.orderIds.add(orderId);
            }
        }

        private Long getProductId() {
            return productId;
        }

        private String getProductName() {
            return productName;
        }

        private String getImageUrl() {
            return imageUrl;
        }

        private String getStoreName() {
            return storeName;
        }

        private long getUnitsSold() {
            return unitsSold;
        }

        private BigDecimal getRevenue() {
            return revenue;
        }

        private int getOrderCount() {
            return orderIds.size();
        }
    }
}
