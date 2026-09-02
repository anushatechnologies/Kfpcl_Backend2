package com.project.Anusha.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Per-vehicle fare rule set by admin.
 *
 * Fare formula:
 *   If distanceKm <= baseKm  → charge = baseFare
 *   If distanceKm >  baseKm  → charge = baseFare + floor(distanceKm - baseKm) × perKmRate
 *   If isRainActive            → charge += rainSurcharge
 *   If AUTO or HEAVY           → charge += loadingCharge + unloadingCharge
 *
 * Example (BIKE, 9.7 km, rain ON ₹30):
 *   25 + floor(9.7 - 2) × 7 + 30 = 25 + 49 + 30 = ₹104
 *
 * Vehicle groups for rain surcharge:
 *   Light group : BIKE, SCOOTY, EV  (admin sets same rain fare for all three)
 *   Auto group  : AUTO
 *   Heavy group : HEAVY
 */
@Entity
@Table(name = "fare_rules",
       uniqueConstraints = @UniqueConstraint(columnNames = "vehicle_type"))
@Data
public class FareRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** One row per vehicle type. */
    @Column(name = "vehicle_type", nullable = false, unique = true)
    @Enumerated(EnumType.STRING)
    private DeliveryPerson.VehicleType vehicleType;

    /**
     * Flat fare charged for any delivery within baseKm.
     * Default ₹25 (BIKE/SCOOTY/EV). Admin can change per vehicle.
     */
    @Column(name = "base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare = new BigDecimal("25.00");

    /**
     * Distance (km) covered by the flat baseFare.
     * Default 2.0 km.
     */
    @Column(name = "base_km", nullable = false, precision = 5, scale = 1)
    private BigDecimal baseKm = new BigDecimal("2.0");

    /**
     * Rate charged per full km beyond baseKm.
     * Default ₹7/km (BIKE/SCOOTY/EV). Admin can change per vehicle.
     */
    @Column(name = "per_km_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKmRate = new BigDecimal("7.00");

    /**
     * Extra surcharge added when admin enables rain mode for this vehicle type.
     * Default ₹0. Admin sets, e.g., ₹30 for rain.
     */
    @Column(name = "rain_surcharge", nullable = false, precision = 10, scale = 2)
    private BigDecimal rainSurcharge = BigDecimal.ZERO;

    /**
     * Whether rain surcharge is currently active for this vehicle type.
     * Admin toggles ON/OFF from dashboard.
     */
    @Column(name = "is_rain_active", nullable = false)
    private boolean rainActive = false;

    /**
     * Loading charge (only applicable to AUTO and HEAVY).
     * Admin sets this. ₹0 for BIKE/SCOOTY/EV.
     */
    @Column(name = "loading_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal loadingCharge = BigDecimal.ZERO;

    /**
     * Unloading charge (only applicable to AUTO and HEAVY).
     * Admin sets this. ₹0 for BIKE/SCOOTY/EV.
     */
    @Column(name = "unloading_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal unloadingCharge = BigDecimal.ZERO;

    /** Whether this fare rule is enabled. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_admin_id")
    private User updatedByAdmin;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
