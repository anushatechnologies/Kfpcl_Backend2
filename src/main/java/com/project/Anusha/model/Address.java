package com.project.Anusha.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "addresses")
@Getter
@Setter
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(length = 20)
    private String addressType; // home, work, other

    @Column(length = 50)
    private String flatNumber;

    @Column(nullable = false)
    private String addressLine1;

    private String addressLine2;
    private String landmark;

    @Column(nullable = false)
    private String city;

    private String state;

    @Column(nullable = false, length = 20)
    private String postalCode;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    private Boolean isDefault = false;

    @Column(nullable = false)
    private Boolean archived = false;

    /** Optional receiver name — used when customer orders for someone else */
    private String contactName;

    /** Optional receiver phone — used when customer orders for someone else */
    private String contactPhone;

    /** Compatibility alias: prefer flatNumber, fallback to addressLine1 */
    public String getHouseNumber() { return flatNumber != null && !flatNumber.isBlank() ? flatNumber : addressLine1; }

    /** Compatibility alias: maps to addressLine2 (may be null) */
    public String getArea() { return addressLine2 != null ? addressLine2 : ""; }

    @CreationTimestamp
    private LocalDateTime createdAt;
}
