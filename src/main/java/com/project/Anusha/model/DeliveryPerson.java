package com.project.Anusha.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Delivery-person profile – specific to the DeliveryApp (ZeptoPartner).
 * All Firebase / phone / role data is stored in {@link UserMain}.
 */
@Entity
@Table(name = "delivery_persons")
public class DeliveryPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the common user record (fid, phoneNumber, roles).
     * OneToOne: one UserMain entry → at most one delivery-person profile.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_main_id", nullable = false, unique = true)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "fcmTokens", "fcmToken", "walletBalance", "version",
            "createdAt", "updatedAt" })
    private UserMain userMain;

    // ------------------------------------------------------------------ personal
    // info

    @NotBlank(message = "First name is required")
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Column(name = "last_name", nullable = false)
    private String lastName;

    /** S3 URL of the profile photo uploaded during registration */
    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_photo_status", nullable = false)
    private DeliveryPersonDocument.DocumentStatus profilePhotoStatus = DeliveryPersonDocument.DocumentStatus.PENDING;

    @Column(name = "profile_photo_remarks")
    private String profilePhotoRemarks;

    // ------------------------------------------------------------------ vehicle
    // info

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private VehicleType vehicleType = VehicleType.BIKE;

    @Column(name = "vehicle_model", nullable = false)
    private String vehicleModel = "Not Specified";

    @Column(name = "registration_number", nullable = false)
    private String registrationNumber = "TEMP-0";

    // ------------------------------------------------------------------ status
    // flags

    @Column(name = "is_online", nullable = false)
    private boolean isOnline = false;

    /**
     * True once the phone number has been verified via Firebase OTP.
     * Set to true automatically during signup.
     */
    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    /**
     * True only after admin explicitly approves the delivery person.
     * Required before the person can log in and accept deliveries.
     */
    @Column(name = "is_approved_by_admin", nullable = false)
    private boolean isApprovedByAdmin = false;

    /**
     * Fine-grained approval lifecycle:
     * PENDING → just registered, waiting for admin review
     * APPROVED → admin accepted all docs + profile
     * REJECTED → admin rejected; person must correct and resubmit
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    // ------------------------------------------------------------------ location (MySQL - last known GPS)

    /** Last known latitude stored in MySQL (updated every 30s when online) */
    @Column(name = "latitude")
    private Double latitude;

    /** Last known longitude stored in MySQL (updated every 30s when online) */
    @Column(name = "longitude")
    private Double longitude;

    /** When the location was last updated */
    @Column(name = "location_updated_at")
    private LocalDateTime locationUpdatedAt;

    // ------------------------------------------------------------------ timestamps

    @Column(name = "registration_date", nullable = false)
    private LocalDateTime registrationDate;

    @Column(name = "last_active_time", nullable = false)
    private LocalDateTime lastActiveTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ------------------------------------------------------------------ earnings

    @Column(name = "total_earnings", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(name = "weekly_earnings", nullable = false, precision = 10, scale = 2)
    private BigDecimal weeklyEarnings = BigDecimal.ZERO;

    @Column(name = "total_completed_orders", nullable = false)
    private Integer totalCompletedOrders = 0;

    @Column(name = "weekly_completed_orders", nullable = false)
    private Integer weeklyCompletedOrders = 0;

    /** Accumulated online minutes across all sessions (updated when going offline). */
    @Column(name = "total_login_minutes", nullable = false)
    private Long totalLoginMinutes = 0L;

    // ------------------------------------------------------------------ bank
    // details

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "ifsc_code")
    private String ifscCode;

    /** Rolling average of customer_rating values across all delivered orders */
    @Column(name = "average_rating")
    private Double averageRating;

    /** Total number of customer ratings received */
    @Column(name = "total_ratings", nullable = false)
    private Integer totalRatings = 0;

    // ------------------------------------------------------------------
    // constructor

    public DeliveryPerson() {
        this.registrationDate = LocalDateTime.now();
        this.lastActiveTime = LocalDateTime.now();
    }

    // ------------------------------------------------------------------
    // convenience delegators

    /** Shortcut – reads phone number from UserMain. */
    public String getPhoneNumber() {
        return userMain != null ? userMain.getPhoneNumber() : null;
    }

    /** Shortcut – reads Firebase UID from UserMain. */
    public String getFirebaseUid() {
        return userMain != null ? userMain.getFid() : null;
    }

    /** Shortcut – reads role string from UserMain. */
    public String getRole() {
        return userMain != null ? userMain.getRoles() : "DELIVERY_PERSON";
    }

    /** Full display name */
    public String getFullName() {
        return firstName + " " + lastName;
    }

    // ------------------------------------------------------------------ getters /
    // setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserMain getUserMain() {
        return userMain;
    }

    public void setUserMain(UserMain userMain) {
        this.userMain = userMain;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getProfilePhotoUrl() {
        return profilePhotoUrl;
    }

    public void setProfilePhotoUrl(String profilePhotoUrl) {
        this.profilePhotoUrl = profilePhotoUrl;
    }

    public DeliveryPersonDocument.DocumentStatus getProfilePhotoStatus() {
        return profilePhotoStatus;
    }

    public void setProfilePhotoStatus(DeliveryPersonDocument.DocumentStatus profilePhotoStatus) {
        this.profilePhotoStatus = profilePhotoStatus;
    }

    public String getProfilePhotoRemarks() {
        return profilePhotoRemarks;
    }

    public void setProfilePhotoRemarks(String profilePhotoRemarks) {
        this.profilePhotoRemarks = profilePhotoRemarks;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(VehicleType vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String vehicleModel) {
        this.vehicleModel = vehicleModel;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    @JsonProperty("isOnline")
    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
        if (online)
            this.lastActiveTime = LocalDateTime.now();
    }

    @JsonProperty("isVerified")
    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    @JsonProperty("isApprovedByAdmin")
    public boolean isApprovedByAdmin() {
        return isApprovedByAdmin;
    }

    public void setApprovedByAdmin(boolean approvedByAdmin) {
        isApprovedByAdmin = approvedByAdmin;
        if (approvedByAdmin) {
            this.approvalStatus = ApprovalStatus.APPROVED;
        }
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
        this.isApprovedByAdmin = (approvalStatus == ApprovalStatus.APPROVED);
    }

    public LocalDateTime getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDateTime registrationDate) {
        this.registrationDate = registrationDate;
    }

    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(LocalDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getTotalLoginMinutes() {
        return totalLoginMinutes == null ? 0L : totalLoginMinutes;
    }

    public void setTotalLoginMinutes(Long totalLoginMinutes) {
        this.totalLoginMinutes = totalLoginMinutes;
    }

    public BigDecimal getTotalEarnings() {
        return totalEarnings;
    }

    public void setTotalEarnings(BigDecimal totalEarnings) {
        this.totalEarnings = totalEarnings;
    }

    public BigDecimal getWeeklyEarnings() {
        return weeklyEarnings;
    }

    public void setWeeklyEarnings(BigDecimal weeklyEarnings) {
        this.weeklyEarnings = weeklyEarnings;
    }

    public Integer getTotalCompletedOrders() {
        return totalCompletedOrders;
    }

    public void setTotalCompletedOrders(Integer totalCompletedOrders) {
        this.totalCompletedOrders = totalCompletedOrders;
    }

    public Integer getWeeklyCompletedOrders() {
        return weeklyCompletedOrders;
    }

    public void setWeeklyCompletedOrders(Integer weeklyCompletedOrders) {
        this.weeklyCompletedOrders = weeklyCompletedOrders;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getIfscCode() {
        return ifscCode;
    }

    public void setIfscCode(String ifscCode) {
        this.ifscCode = ifscCode;
    }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public LocalDateTime getLocationUpdatedAt() { return locationUpdatedAt; }
    public void setLocationUpdatedAt(LocalDateTime locationUpdatedAt) { this.locationUpdatedAt = locationUpdatedAt; }
    public Double getAverageRating() { return averageRating; }
    public void setAverageRating(Double averageRating) { this.averageRating = averageRating; }
    public Integer getTotalRatings() { return totalRatings; }
    public void setTotalRatings(Integer totalRatings) { this.totalRatings = totalRatings; }

    // ------------------------------------------------------------------ enums

    public enum VehicleType {
        BIKE("Bike"),
        SCOOTY("Scooty"),
        EV("Electric Bike"),
        AUTO("Auto"),
        HEAVY("Heavy Vehicle");

        private final String displayName;

        VehicleType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum ApprovalStatus {
        PENDING("Pending Admin Review"),
        APPROVED("Approved"),
        REJECTED("Rejected – Documents Need Correction");

        private final String displayName;

        ApprovalStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
